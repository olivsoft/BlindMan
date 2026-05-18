package ch.olivsoft.android.blindman

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

// Constants
private const val LOG_TAG = "Color Picker"
private const val MIN_R = 100f
private const val OK = "OK"
private val COLORS = listOf(
    Color.Red, Color.Magenta, Color.Blue, Color.Cyan,
    Color.Green, Color.Yellow, Color.Red
)

@Composable
fun ColorPicker(
    modifier: Modifier = Modifier,
    currentColor: Color = Color.Cyan,
    onColorSelected: (Color) -> Unit = {}
) {
    // Constants
    val iMode = LocalInspectionMode.current
    val cSize = if (iMode) IntSize(1080, 1920) else IntSize.Zero

    // Variables
    var canvasSize by remember { mutableStateOf(cSize) }
    var circleRadius by remember { mutableFloatStateOf(0f) }
    var centerRadius by remember { mutableFloatStateOf(0f) }
    var trackingCenter by remember { mutableStateOf(false) }
    var highlightCenter by remember { mutableStateOf(false) }
    var currColor by remember { mutableStateOf(currentColor) }
    val textMeasurer = rememberTextMeasurer()

    // Helper for Colors
    fun getColorFromAngle(x: Float, y: Float): Color {
        // The "angle" (x, y) is projected to (-0.5..0.5],
        // then (-0.5..0] is moved to (0.5..1].
        // So, f interpolates the full circle.
        var f = atan2(y, x) / 2f / PI.toFloat()
        if (f < 0)
            f += 1f

        // Stretch to array length and extract integer (i) and fractional (f) part
        f *= COLORS.size - 1
        val i = f.toInt()
        f -= i

        // Interpolate between adjacent colors
        return lerp(COLORS[i], COLORS[i + 1], f)
    }

    LaunchedEffect(true) {
        if (canvasSize == IntSize.Zero)
            return@LaunchedEffect

        // This is the moment to set size dependent properties
        val s = min(canvasSize.width, canvasSize.height)
        circleRadius = max(MIN_R, s / 2.2f)
        centerRadius = circleRadius / 3

        Log.d(LOG_TAG, "Radius is $circleRadius")
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                if (it != IntSize.Zero)
                    canvasSize = it
            }
            .pointerInput(true) {
                detectDragGestures(
                    onDragStart = {
                        Log.d(LOG_TAG, "Drag started")
                    },
                    onDragEnd = {
                        trackingCenter = false
                        highlightCenter = false
                        Log.d(LOG_TAG, "Drag ended")
                    }
                ) { change, _ ->
                    val o = change.position - size.center.toOffset()
                    val r = o.getDistance()
                    if (r >= 2 * centerRadius) {
                        currColor = getColorFromAngle(o.x, o.y)
                    }
                    trackingCenter = trackingCenter && (r <= 2 * centerRadius)
                    highlightCenter = highlightCenter && (r <= centerRadius)
                }
            }
            .pointerInput(true) {
                detectTapGestures(
                    onTap = {
                        trackingCenter = false
                        highlightCenter = false
                        Log.d(LOG_TAG, "Tap")
                    }
                )
            }
            .pointerInput(true) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var o = down.position - size.center.toOffset()
                    var r = o.getDistance()
                    if (r <= centerRadius) {
                        trackingCenter = true
                        highlightCenter = true
                    } else if (r >= 2 * centerRadius) {
                        currColor = getColorFromAngle(o.x, o.y)
                        Log.d(LOG_TAG, "Down at color $currColor")
                    }
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    o = up.position - size.center.toOffset()
                    r = o.getDistance()
                    if (r <= centerRadius) {
                        onColorSelected(currColor)
                        Log.d(LOG_TAG, "Selected color: $currColor")
                    }
                }
            }
    ) {
        // Protection
        if (canvasSize == IntSize.Zero)
            return@Canvas

        // Color gradient circle
        drawCircle(
            brush = Brush.sweepGradient(
                colors = COLORS,
            ),
            radius = circleRadius - centerRadius / 2f,
            style = Stroke(centerRadius),
        )

        // Central circle
        drawCircle(
            radius = centerRadius,
            color = currColor,
        )
        if (trackingCenter) {
            // This is just for adding a little ring around the OK button
            // which shows if we touch still in the center. It is dimmed
            // when we move out of the center.
            val strokeWidth = circleRadius / 20f
            drawCircle(
                radius = 2 * centerRadius - strokeWidth,
                color = currColor,
                alpha = if (highlightCenter) 1f else 0.5f,
                style = Stroke(strokeWidth)
            )
        }

        // OK text
        val fontSize = (0.8f * centerRadius).toSp()
        val textStyle = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        )
        val textLayoutResult = textMeasurer.measure(OK, textStyle)
        val textOffset = center - textLayoutResult.size.center.toOffset()
        val textColor = Color(
            1f - currColor.red,
            1f - currColor.green,
            1f - currColor.blue
        )
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = textOffset,
            color = textColor
        )
    }
}

@PreviewScreenSizes
@Composable
fun ColorPickerPreview() {
    ColorPicker()
}
