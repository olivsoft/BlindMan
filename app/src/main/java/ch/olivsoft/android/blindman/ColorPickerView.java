package ch.olivsoft.android.blindman;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * <p>
 * {@link View} for a color selection dialog.
 * </p>
 * <p/>
 * <h4>Usage:</h4>
 * <p/>
 * <p>
 * Create a {@link Dialog} and pass a new instance of this class to the dialog's
 * {@link android.app.Activity#setContentView(View)} method. Alternatively, use the factory method
 * {@link ColorPickerView#createDialog(Context, String, int, DialogInterface.OnClickListener)}
 * doing all this for you. The picked color is passed back like in other dialogs via
 * a regular {@link DialogInterface.OnClickListener}.
 * </p>
 * <p>
 * This view is not suitable for layout tools. Therefore, the standard
 * constructors are not implemented.
 * </p>
 * <p/>
 * <h4>Notice:</h4>
 * <p/>
 * <p>
 * This code has been slightly adapted from an original example according to <a
 * href="http://www.apache.org/licenses/LICENSE-2.0">this license</a>. Apart
 * from reordering, comments and renaming, the major changes are as follows:
 * </p>
 * <p/>
 * <ul>
 * <li>The listener interface has been replaced by an available one from the
 * platform's {@link DialogInterface}.
 * <li>An "OK" text in "anti-color" is placed onto the central button.
 * <li>The total size is to a certain extent determined from the total available
 * screen size.
 * <li>For convenience, a factory method for creating a dialog that already
 * contains the view has been added.
 * </ul>
 *
 * @author Oliver Fritz, OlivSoft
 */
@SuppressLint("ViewConstructor")
public class ColorPickerView extends View {
    // Constants
    private static final String LOG_TAG = ColorPickerView.class.getSimpleName();
    private static final float TWO_PI = 2 * (float) Math.PI;
    private static final int EDGE = 5;
    private static final int MIN_R = 100;
    private static final String OK = "OK";
    private static final int FULL_ALPHA = 0xFF;
    private static final int[] COLORS = {
            Color.RED, Color.MAGENTA, Color.BLUE,
            Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED};

    // Variables
    private final DialogInterface dialog;
    private final DialogInterface.OnClickListener listener;
    private final Rect measureRect;
    private int tX, tY, circleRadius, centerRadius;
    private final Paint circlePaint, centerPaint;
    private final TextPaint textPaint;
    private int textHeight;
    private boolean trackingCenter = false;
    private boolean highlightCenter;

    /**
     * Use this constructor to create a {@link ColorPickerView} that can be
     * embedded into a {@link Dialog}.
     *
     * @param dialog       The embedding dialog
     * @param initialColor The initial color
     * @param listener     The callback listener
     */
    public ColorPickerView(Dialog dialog, int initialColor, DialogInterface.OnClickListener listener) {
        super(dialog.getContext());
        this.dialog = dialog;
        this.listener = listener;

        // This may be helpful
        this.setMinimumWidth(2 * MIN_R);
        this.setMinimumHeight(2 * MIN_R);
        int lp = FrameLayout.LayoutParams.WRAP_CONTENT;
        this.setLayoutParams(new FrameLayout.LayoutParams(lp, lp));

        // Content description
        this.setContentDescription(getResources().getText(R.string.menu_colors));

        // Initialize all variables that are not size-dependent
        measureRect = new Rect();

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setShader(new SweepGradient(0, 0, COLORS, null));
        circlePaint.setStyle(Paint.Style.STROKE);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(initialColor);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
        textPaint.setTextAlign(Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /**
     * Use this factory method to create a {@link Dialog} that contains a new
     * {@link ColorPickerView} instance.
     *
     * @param context      A valid context
     * @param dialogTitle  The title of the dialog
     * @param initialColor The initial color
     * @param listener     The callback listener
     * @return A dialog containing the color picker view
     */
    public static Dialog createDialog(Context context, String dialogTitle, int initialColor, DialogInterface.OnClickListener listener) {
        // This hides the somewhat confusing double use of the dialog reference
        // from the caller. We see no other way of handling the OnClick(Dialog, int)
        // properly.
        Dialog d = new Dialog(context);
        d.setTitle(dialogTitle);
        d.setContentView(new ColorPickerView(d, initialColor, listener));
        return d;
    }

    // Measure, size and draw. Measure and size events can
    // come in surprising numbers and order. Therefore, we
    // are a bit careful.
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We determine the screen size through a strange
        // and buggy function (see comments in source).
        // For a perfect look the size must be an even number.
        getWindowVisibleDisplayFrame(measureRect);
        if (measureRect.isEmpty()) {
            setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        circleRadius = Math.max(Math.min(measureRect.width(), measureRect.height()) / 4, MIN_R);
        Log.d(LOG_TAG, String.format("Measured radius: %d", circleRadius));
        // Give a bit of space on the edges
        int d = 2 * (circleRadius + EDGE);
        setMeasuredDimension(d, d);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // One more check if the desired size has been realized. If not
        // we need to adjust the radius of the circle again. This should
        // never be the case if the layout mechanism works correctly.
        tX = w / 2;
        tY = h / 2;
        if (w < measureRect.width() || h < measureRect.height()) {
            circleRadius = Math.min(tX, tY) - EDGE;
            Log.w(LOG_TAG, String.format("Reduced radius: %d", circleRadius));
        }

        // This is the moment to set all other size dependent properties
        centerRadius = circleRadius / 3;
        circlePaint.setStrokeWidth(centerRadius);
        centerPaint.setStrokeWidth(circleRadius / 20f);
        textPaint.setTextSize(4 * (centerRadius / 5f) - 1); // Odd!
        textPaint.getTextBounds(OK, 0, OK.length(), measureRect);
        textHeight = measureRect.height();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.translate(tX, tY);
        canvas.drawCircle(0, 0, centerRadius, centerPaint);
        canvas.drawCircle(0, 0, circleRadius - centerRadius / 2f, circlePaint);

        if (trackingCenter) {
            // This is just for adding a little ring around the OK button
            // which shows if we touch still in the center. It is dimmed
            // if we move out of the center.
            centerPaint.setStyle(Paint.Style.STROKE);
            if (!highlightCenter)
                centerPaint.setAlpha(FULL_ALPHA / 2);
            canvas.drawCircle(0, 0, 2 * centerRadius - centerPaint.getStrokeWidth(), centerPaint);

            // Reset to standard
            centerPaint.setStyle(Paint.Style.FILL);
            centerPaint.setAlpha(FULL_ALPHA);
        }

        // Add a nicely anti-colored "OK" in the center.
        // Careful: c contains alpha, which must be
        // the same value (not "anti") as the background.
        textPaint.setColor(~centerPaint.getColor());
        textPaint.setAlpha(centerPaint.getAlpha());
        canvas.drawText(OK, 0, textHeight / 2f, textPaint);
    }

    // Method for integer interpolation
    private int interpolateLinear(int a, int b, float f) {
        return a + Math.round(f * (b - a));
    }

    // Method for interpolating an array
    @SuppressWarnings("SameParameterValue")
    private int interpolateColorArray(int[] c, float p) {
        if (p <= 0)
            return c[0];
        if (p >= 1)
            return c[c.length - 1];

        // Stretch to array length and extract integer (i) and fractional (f)
        // part
        float f = p * (c.length - 1);
        int i = (int) f;
        f -= i;

        // Interpolate color. We don't need to interpolate alpha,
        // all elements of the array are at its maximum value.
        int ca = c[i];
        int cb = c[i + 1];
        int r = interpolateLinear(Color.red(ca), Color.red(cb), f);
        int g = interpolateLinear(Color.green(ca), Color.green(cb), f);
        int b = interpolateLinear(Color.blue(ca), Color.blue(cb), f);

        return Color.argb(FULL_ALPHA, r, g, b);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retval = super.onTouchEvent(event);

        // Here we use double on purpose. This is not for precision
        // but because x and y are only used as Math function arguments.
        double x = event.getX() - tX;
        double y = event.getY() - tY;
        boolean inCenter = Math.hypot(x, y) <= centerRadius;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                trackingCenter = inCenter;
                if (inCenter) {
                    highlightCenter = true;
                    invalidate();
                    break;
                }

            case MotionEvent.ACTION_MOVE:
                if (trackingCenter) {
                    if (highlightCenter != inCenter) {
                        highlightCenter = inCenter;
                        invalidate();
                    }
                } else {
                    // The "angle" (x, y) is projected to (-0.5..0.5],
                    // then (-0.5..0) is moved to (0.5..1).
                    // So, "p" interpolates the full circle.
                    float p = (float) Math.atan2(y, x) / TWO_PI;
                    if (p < 0) {
                        p += 1;
                    }
                    centerPaint.setColor(interpolateColorArray(COLORS, p));
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (trackingCenter) {
                    trackingCenter = false;
                    if (inCenter) {
                        dialog.dismiss();
                        // Pass selected color to listener
                        listener.onClick(dialog, centerPaint.getColor());
                    } else {
                        // No selection yet
                        invalidate();
                    }
                }
                break;

            default:
                return retval;
        }

        return true;
    }
}
