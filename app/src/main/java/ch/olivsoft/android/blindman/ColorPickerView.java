package ch.olivsoft.android.blindman;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;

import ch.olivsoft.android.blindman.databinding.ColorpickerBinding;

/**
 * {@link View} for a color selection dialog.
 * <br/>
 * <br/>
 * <b>Usage:</b>
 * <br/>
 * Use the factory method
 * {@link ColorPickerView#createDialog(Context, String, int, DialogInterface.OnClickListener)}
 * doing all the work for you. The picked color (int) is passed back like in other dialogs via
 * a regular {@link DialogInterface.OnClickListener}. Alternatively, create an
 * {@link AppCompatDialog} and pass this view (e.g., from a layout resource) to the dialog's
 * {@link AppCompatActivity#setContentView(View)} method. You then have to call
 * {@link ColorPickerView#setColorPickerOnClickListener(DialogInterface, DialogInterface.OnClickListener)}
 * and {@link ColorPickerView#setInitialColor(int)} to complete the initialization.
 * <br/>
 * <br/>
 * <b>Notice:</b>
 * <br/>
 * This code has been slightly adapted from an original example according to <a
 * href="http://www.apache.org/licenses/LICENSE-2.0">this license</a>. Apart
 * from reordering, comments and renaming, the major changes are as follows:
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
public class ColorPickerView extends View {

    // Constants
    private static final String LOG_TAG = ColorPickerView.class.getSimpleName();
    private static final int EDGE = 5;
    private static final int MIN_R = 100;
    private static final String OK = "OK";
    private static final int FULL_ALPHA = 0xFF;
    private static final int[] COLORS = {
            Color.RED, Color.MAGENTA, Color.BLUE,
            Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED};

    // Variables
    private DialogInterface dialog;
    private DialogInterface.OnClickListener listener;
    private final Rect measureRect;
    private int tX, tY, circleRadius, centerRadius;
    private final Paint circlePaint, centerPaint;
    private final TextPaint textPaint;
    private int textHeight;
    private boolean trackingCenter = false;
    private boolean highlightCenter;


    /**
     * Use this method to initialize the {@link ColorPickerView} embedded into
     * an {@link AppCompatDialog}.
     *
     * @param dialog   The embedding dialog
     * @param listener The callback listener
     */
    public void setColorPickerOnClickListener(DialogInterface dialog,
                                              DialogInterface.OnClickListener listener) {
        this.dialog = dialog;
        this.listener = listener;
    }

    /**
     * Sets the initial color of the color picker.
     *
     * @param initialColor The initial color
     */
    public void setInitialColor(int initialColor) {
        centerPaint.setColor(initialColor);
    }

    /**
     * Returns the currently selected color
     *
     * @return Selected color (argb)
     */
    public int getSelectedColor() {
        return centerPaint.getColor();
    }

    /**
     * Constructor for layout mechanism. Do not forget to call
     * {@link ColorPickerView#setColorPickerOnClickListener(DialogInterface, DialogInterface.OnClickListener)}
     * afterwards.
     */
    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize all variables that are not size-dependent
        measureRect = new Rect();

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setShader(new SweepGradient(0, 0, COLORS, null));
        circlePaint.setStyle(Paint.Style.STROKE);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
        textPaint.setTextAlign(Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /**
     * Use this factory method to create an {@link AppCompatDialog} that contains a new
     * {@link ColorPickerView} instance.
     *
     * @param context      A valid context
     * @param dialogTitle  The title of the dialog
     * @param initialColor The initial color
     * @param listener     The callback listener
     * @return A dialog containing the color picker view
     */
    public static AppCompatDialog createDialog(
            Context context, String dialogTitle, int initialColor,
            DialogInterface.OnClickListener listener) {

        // This hides the somewhat confusing double use of the dialog reference
        // from the caller. It is the best way of handling OnClick(Dialog, int)
        // properly. We also preserve the color state here!
        return new AppCompatDialog(context) {
            private static final String INITIAL_COLOR = "INITIAL_COLOR";
            private ColorPickerView view;

            @NonNull
            @Override
            public Bundle onSaveInstanceState() {
                Bundle bundle = super.onSaveInstanceState();
                bundle.putInt(INITIAL_COLOR, view.getSelectedColor());
                return bundle;
            }

            @Override
            public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
                super.onRestoreInstanceState(savedInstanceState);
                view.setInitialColor(savedInstanceState.getInt(INITIAL_COLOR));
            }

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setTitle(dialogTitle);
                ColorpickerBinding binding = ColorpickerBinding.inflate(getLayoutInflater());
                setContentView(binding.getRoot());
                view = binding.colorPickerView;
                view.setColorPickerOnClickListener(this, listener);
                if (savedInstanceState == null) {
                    view.setInitialColor(initialColor);
                }
            }
        };
    }

    /**
     * Linear interpolation for integer values
     *
     * @param a Lower bound of range to interpolate
     * @param b Upper bound of range to interpolate
     * @param f Interpolator (0...1)
     * @return A linear interpolation between a and b according to f = 0...1
     */
    public static int interpolateLinear(int a, int b, float f) {
        return a + Math.round(f * (b - a));
    }

    /**
     * Method for interpolating a color array
     *
     * @param c     Color array
     * @param f     Interpolator
     * @param alpha Alpha value of resulting color
     * @return Color, interpolated from c according to p = 0...1
     */
    public static int interpolateColorArray(int[] c, float f, int alpha) {
        if (f <= 0)
            return c[0];
        if (f >= 1)
            return c[c.length - 1];

        // Stretch to array length and extract integer (i) and fractional (f)
        // part
        f *= c.length - 1;
        int i = (int) f;
        f -= i;

        // Interpolate color. We don't interpolate alpha.
        int ca = c[i];
        int cb = c[i + 1];
        int r = interpolateLinear(Color.red(ca), Color.red(cb), f);
        int g = interpolateLinear(Color.green(ca), Color.green(cb), f);
        int b = interpolateLinear(Color.blue(ca), Color.blue(cb), f);

        return Color.argb(alpha, r, g, b);
    }

    // Measure, size and draw. Measure and size events can
    // come in surprising numbers and order. Therefore, we
    // are a bit careful.
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (this.isInEditMode())
            return;

        // We determine the screen size through a strange
        // and buggy function (see comments in source).
        // For a perfect look the size must be an even number.
        getWindowVisibleDisplayFrame(measureRect);
        if (measureRect.isEmpty()) {
            setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        circleRadius = Math.max(Math.min(
                measureRect.width(), measureRect.height()) / 4, MIN_R);
        Log.i(LOG_TAG, String.format("Measured radius: %d", circleRadius));
        // Give a bit of space on the edges
        int d = 2 * (circleRadius + EDGE);
        setMeasuredDimension(d, d);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (this.isInEditMode())
            return;

        // Determine the translation for onDraw.
        // No matter how big the view is in the end, we
        // want to paint in the center of it.
        tX = w / 2;
        tY = h / 2;

        // One more check if the size requested in onMeasure has been realized.
        // If not we need to adjust the radius of the circle again. This should
        // never be the case if the layout mechanism works correctly.
        if (circleRadius + EDGE > Math.min(tX, tY)) {
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
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (this.isInEditMode())
            return;

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
            canvas.drawCircle(0, 0,
                    2 * centerRadius - centerPaint.getStrokeWidth(), centerPaint);

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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retVal = super.onTouchEvent(event);

        float x = event.getX() - tX;
        float y = event.getY() - tY;
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
                    // So, "f" interpolates the full circle.
                    float f = (float) (Math.atan2(y, x) / 2 / Math.PI);
                    if (f < 0)
                        f += 1;
                    centerPaint.setColor(interpolateColorArray(COLORS, f, FULL_ALPHA));
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (trackingCenter) {
                    trackingCenter = false;
                    if (inCenter) {
                        // This is a click!
                        performClick();
                    } else {
                        // No selection yet
                        invalidate();
                    }
                }
                break;

            default:
                return retVal;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        if (dialog != null && listener != null) {
            // Pass selected color to listener
            listener.onClick(dialog, getSelectedColor());
            super.performClick();
            return true;
        } else {
            return super.performClick();
        }
    }
}
