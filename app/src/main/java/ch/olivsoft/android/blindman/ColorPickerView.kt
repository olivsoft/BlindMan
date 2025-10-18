package ch.olivsoft.android.blindman

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
import kotlin.math.roundToInt

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
         * @param listener     The dialog click listener
         * @return An [AppCompatDialog] containing the color picker view
         */
        fun createDialog(
            context: Context, dialogTitle: String?, initialColor: Int,
            listener: DialogInterface.OnClickListener
        ): AppCompatDialog {
            return object : AppCompatDialog(context) {
                // Constants and variables
                val INITIAL_COLOR = "INITIAL_COLOR"
                lateinit var view: ColorPickerView

                // Call the dialog click listener from the view click listener
                val colorListener = OnClickListener {
                    listener.onClick(this, view.selectedColor)
                }

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setTitle(dialogTitle)
                    with(ColorpickerBinding.inflate(layoutInflater)) {
                        setContentView(root)
                        view = colorPickerView.apply {
                            selectedColor =
                                savedInstanceState?.getInt(INITIAL_COLOR) ?: initialColor
                            setOnClickListener(colorListener)
                            // The view is NOT clickable, only the central "OK" is
                            isClickable = false
                        }
                    }
                }

                // Preserve the color state
                override fun onSaveInstanceState(): Bundle {
                    return super.onSaveInstanceState().apply {
                        putInt(INITIAL_COLOR, view.selectedColor)
                    }
                }
            }
        }

        // Helper functions for getting a color from a circular angle
        private fun interpolateInt(a: Int, b: Int, f: Float): Int {
            return a + (f * (b - a)).roundToInt()
        }

        private fun getColorFromAngle(x: Float, y: Float): Int {
            // The "angle" (x, y) is projected to (-0.5..0.5],
            // then (-0.5..0] is moved to (0.5..1].
            // So, f interpolates the full circle.
            var f = atan2(y, x) / 2f / Math.PI.toFloat()
            if (f < 0)
                f += 1f

            // Stretch to array length and extract integer (i) and fractional (f) part
            f *= COLORS.size - 1
            val i = f.toInt()
            f -= i

            // Interpolate color
            val ca = COLORS[i]
            val cb = COLORS[i + 1]
            val r = interpolateInt(Color.red(ca), Color.red(cb), f)
            val g = interpolateInt(Color.green(ca), Color.green(cb), f)
            val b = interpolateInt(Color.blue(ca), Color.blue(cb), f)
            return Color.rgb(r, g, b)
        }
    }

    // Variables
    private val measureRect = Rect()
    private val centerPoint = PointF()
    private var circleRadius = 0
    private var centerRadius = 0
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)
    private var textHeight = 0
    private var trackingCenter = false
    private var highlightCenter = false

    /**
     * Sets and gets the selected color
     */
    var selectedColor: Int
        set(value) {
            centerPaint.color = value
        }
        get() {
            return centerPaint.color
        }

    /**
     * Constructor
     */
    init {
        // Treat all variables that are not size-dependent
        circlePaint.shader = SweepGradient(0f, 0f, COLORS, null)
        circlePaint.style = Paint.Style.STROKE
        textPaint.textAlign = Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
    }

    // Measure, size and draw. Measure and size events can come in surprising
    // numbers and order. Therefore, we are a bit careful.
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

        // This is the moment to set size dependent properties
        centerPoint.set(w / 2f, h / 2f)
        centerRadius = circleRadius / 3
        circlePaint.strokeWidth = centerRadius.toFloat()
        centerPaint.strokeWidth = circleRadius / 20f
        textPaint.textSize = 0.8f * centerRadius
        textPaint.getTextBounds(OK, 0, OK.length, measureRect)
        textHeight = measureRect.height()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (this.isInEditMode)
            return

        with(canvas) {
            translate(centerPoint.x, centerPoint.y)
            drawCircle(0f, 0f, centerRadius.toFloat(), centerPaint)
            drawCircle(0f, 0f, circleRadius - centerRadius / 2f, circlePaint)
        }

        if (trackingCenter) {
            // This is just for adding a little ring around the OK button
            // which shows if we touch still in the center. It is dimmed
            // when we move out of the center.
            centerPaint.style = Paint.Style.STROKE
            if (!highlightCenter)
                centerPaint.alpha = FULL_ALPHA / 2
            canvas.drawCircle(
                0f, 0f, 2 * centerRadius - centerPaint.strokeWidth, centerPaint
            )

            // Reset to standard
            centerPaint.style = Paint.Style.FILL
            centerPaint.alpha = FULL_ALPHA
        }

        // Add a nicely anti-colored (but equally bright) "OK" in the center
        textPaint.color = centerPaint.color.inv()
        textPaint.alpha = centerPaint.alpha
        canvas.drawText(OK, 0f, textHeight / 2f, textPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val retVal = super.onTouchEvent(e)

        e.offsetLocation(-centerPoint.x, -centerPoint.y)
        val r = hypot(e.x, e.y)
        val inCenter = r <= centerRadius
        val onCircle = r <= circleRadius && r >= circleRadius - centerRadius

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                trackingCenter = inCenter
                if (inCenter)
                    highlightCenter = true
                else if (onCircle)
                    centerPaint.color = getColorFromAngle(e.x, e.y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (trackingCenter) {
                    if (highlightCenter != inCenter) {
                        highlightCenter = inCenter
                        invalidate()
                    }
                } else {
                    if (onCircle)
                        centerPaint.color = getColorFromAngle(e.x, e.y)
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

    override fun performClick(): Boolean {
        return super.performClick() && hasOnClickListeners()
    }
}
