package ch.olivsoft.android.blindman

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatDialog
import ch.olivsoft.android.blindman.databinding.ColorpickerBinding
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * [View] for a color selection dialog.
 * <br></br>
 * <br></br>
 * **Usage:**
 * <br></br>
 * Use the factory method
 * [ColorPickerView.createDialog]
 * doing all the work for you. The picked color (int) is passed back like in other dialogs via
 * a regular [DialogInterface.OnClickListener]. Alternatively, create an
 * [AppCompatDialog] and pass this view (e.g., from a layout resource) to the
 * [AppCompatDialog.setContentView] method. You then have to call
 * [ColorPickerView.selectedColor] to complete the initialization, and you can catch
 * the selected color with an onClickListener, with clickable disabled.
 * <br></br>
 * <br></br>
 * **Notice:**
 * <br></br>
 * This code has been slightly adapted from an original example according to [this license](http://www.apache.org/licenses/LICENSE-2.0). Apart
 * from reordering, comments and renaming, the major changes are as follows:
 *
 *  * An "OK" text in "anti-color" is placed onto the central button.
 *  * The total size is to a certain extent determined from the total available
 * screen size.
 *  * For convenience, a factory method for creating a dialog that already
 * contains the view has been added.
 *
 *
 * @author Oliver Fritz, OlivSoft
 */
class ColorPickerView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    companion object {

        // Constants
        private val LOG_TAG = ColorPickerView::class.simpleName
        private const val EDGE = 5
        private const val MIN_R = 100
        private const val OK = "OK"
        private const val FULL_ALPHA = 0xFF
        private val COLORS = intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE,
            Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
        )

        /**
         * Use this factory method to create an [AppCompatDialog] that contains a new
         * [ColorPickerView] instance.
         *
         * @param context      A valid context
         * @param dialogTitle  The title of the dialog
         * @param initialColor The initial color
         * @param listener     The callback listener
         * @return A dialog containing the color picker view
         */
        fun createDialog(
            context: Context, dialogTitle: String?, initialColor: Int,
            listener: DialogInterface.OnClickListener
        ): AppCompatDialog {
            return object : AppCompatDialog(context) {

                private val INITIAL_COLOR = "INITIAL_COLOR"
                private lateinit var view: ColorPickerView

                override fun onSaveInstanceState(): Bundle {
                    // We preserve the color state!
                    val bundle = super.onSaveInstanceState()
                    bundle.putInt(INITIAL_COLOR, view.selectedColor)
                    return bundle
                }

                override fun onRestoreInstanceState(savedInstanceState: Bundle) {
                    super.onRestoreInstanceState(savedInstanceState)
                    view.selectedColor = savedInstanceState.getInt(INITIAL_COLOR)
                }

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setTitle(dialogTitle)
                    val binding = ColorpickerBinding.inflate(layoutInflater)
                    setContentView(binding.root)
                    view = binding.colorPickerView
                    if (savedInstanceState == null)
                        view.selectedColor = initialColor
                    // The view will send back a click once the color is selected.
                    // Therefore, all other clicks must be disabled.
                    view.setOnClickListener { listener.onClick(this, view.selectedColor) }
                    view.isClickable = false
                }
            }
        }

        // Helper functions for getting a color from a circular angle...
        private fun interpolateLinear(a: Int, b: Int, f: Float): Int {
            return a + Math.round(f * (b - a))
        }

        private fun getColorFromAngle(x: Double, y: Double): Int {
            // The "angle" (x, y) is projected to (-0.5..0.5],
            // then (-0.5..0) is moved to (0.5..1).
            // So, "f" interpolates the full circle.
            var f = (atan2(y, x) / 2 / Math.PI).toFloat()
            if (f < 0)
                f += 1f

            // Stretch to array length and extract integer (i) and fractional (f)
            // part
            f *= (COLORS.size - 1)
            val i = f.toInt()
            f -= i

            // Interpolate color
            val ca = COLORS[i]
            val cb = COLORS[i + 1]
            val r = interpolateLinear(Color.red(ca), Color.red(cb), f)
            val g = interpolateLinear(Color.green(ca), Color.green(cb), f)
            val b = interpolateLinear(Color.blue(ca), Color.blue(cb), f)

            return Color.rgb(r, g, b)
        }
    }

    // Initialize all variables that are not size-dependent
    private val measureRect = Rect()
    private val centerPoint = PointF()
    private var circleRadius = 0
    private var centerRadius = 0
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint: Paint
    private val textPaint: TextPaint
    private var textHeight = 0
    private var trackingCenter = false
    private var highlightCenter = false

    /**
     * Sets and gets the selected color.
     */
    var selectedColor: Int
        set(value) {
            centerPaint.color = value
        }
        get() {
            return centerPaint.color
        }

    /**
     * Constructor for layout mechanism.
     */
    init {
        circlePaint.setShader(SweepGradient(0f, 0f, COLORS, null))
        circlePaint.style = Paint.Style.STROKE

        centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)
        textPaint.textAlign = Align.CENTER
        textPaint.setTypeface(Typeface.DEFAULT_BOLD)
    }

    // Measure, size and draw. Measure and size events can
    // come in surprising numbers and order. Therefore, we
    // are a bit careful.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (this.isInEditMode)
            return

        getWindowVisibleDisplayFrame(measureRect)
        if (measureRect.isEmpty) {
            setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
            return
        }

        circleRadius = max(MIN_R, min(measureRect.width(), measureRect.height()) / 4)
        Log.i(LOG_TAG, String.format("Measured radius: %d", circleRadius))

        // Give a bit of space on the edges
        val d = 2 * (circleRadius + EDGE)
        // d is an even number which is important for the view to look good
        setMeasuredDimension(d, d)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (this.isInEditMode)
            return

        // Determine the translation for onDraw.
        // No matter how big the view is in the end, we
        // want to paint in the center of it.
        centerPoint.set(w / 2f, h / 2f)

        // This is the moment to set all other size dependent properties
        centerRadius = circleRadius / 3
        circlePaint.strokeWidth = centerRadius.toFloat()
        centerPaint.strokeWidth = circleRadius / 20f
        textPaint.textSize = 4 * (centerRadius / 5f) - 1 // Odd!
        textPaint.getTextBounds(OK, 0, OK.length, measureRect)
        textHeight = measureRect.height()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (this.isInEditMode)
            return

        canvas.translate(centerPoint.x, centerPoint.y)
        canvas.drawCircle(0f, 0f, centerRadius.toFloat(), centerPaint)
        canvas.drawCircle(0f, 0f, circleRadius - centerRadius / 2f, circlePaint)

        if (trackingCenter) {
            // This is just for adding a little ring around the OK button
            // which shows if we touch still in the center. It is dimmed
            // if we move out of the center.
            centerPaint.style = Paint.Style.STROKE
            if (!highlightCenter)
                centerPaint.alpha = FULL_ALPHA / 2
            canvas.drawCircle(
                0f, 0f,
                2 * centerRadius - centerPaint.strokeWidth, centerPaint
            )

            // Reset to standard
            centerPaint.style = Paint.Style.FILL
            centerPaint.alpha = FULL_ALPHA
        }

        // Add a nicely anti-colored "OK" in the center.
        // Careful: c contains alpha, which must be
        // the same value (not "anti") as the background.
        textPaint.color = centerPaint.color.inv()
        textPaint.alpha = centerPaint.alpha
        canvas.drawText(OK, 0f, textHeight / 2f, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val retVal = super.onTouchEvent(event)

        val x = event.x - centerPoint.x.toDouble()
        val y = event.y - centerPoint.y.toDouble()
        val inCenter = hypot(x, y) <= centerRadius

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                trackingCenter = inCenter
                if (inCenter)
                    highlightCenter = true
                else
                    centerPaint.color = getColorFromAngle(x, y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (trackingCenter) {
                    if (highlightCenter != inCenter) {
                        highlightCenter = inCenter
                        invalidate()
                    }
                } else {
                    centerPaint.color = getColorFromAngle(x, y)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (trackingCenter) {
                    trackingCenter = false
                    if (inCenter) {
                        // This is a click! Catch it as
                        // the caller with an OnClickListener.
                        return performClick()
                    } else {
                        // No selection yet
                        invalidate()
                    }
                }
            }

            else -> return retVal
        }

        return true
    }
}
