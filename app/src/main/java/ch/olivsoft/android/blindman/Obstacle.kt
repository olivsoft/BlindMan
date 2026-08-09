package ch.olivsoft.android.blindman

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.shapes.Shape
import androidx.compose.ui.graphics.toComposeIntRect
import androidx.compose.ui.unit.IntRect

class Obstacle(ix: Int, iy: Int, size: Int) : PaintDrawable() {
    // We implement all necessary methods in order to relieve
    // the caller from taking care of the obstacle's state logic.

    private var hit = false
    private var hidden = false
    private val composeRect: IntRect

    init {
        // The allocated shape in a PaintDrawable is
        // a rectangle defined by its bounds
        setBounds(ix * size, iy * size, (ix + 1) * size, (iy + 1) * size)
        setCornerRadius(0.1f * size)
        paint.isAntiAlias = true
        composeRect = bounds.toComposeIntRect()
    }

    // Just for safety reasons. This should not be used.
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        hidden = !visible
        return true
    }

    fun isHit(): Boolean {
        return hit
    }

    fun setHit() {
        hit = true
        hidden = false
    }

    fun setHidden() {
        hidden = true
    }

    fun setVisibleIfHit() {
        hidden = !hit
    }

    fun setVisible() {
        hidden = false
    }

    // Convenience methods for intersection checks
    fun intersects(r: Rect): Boolean {
        return Rect.intersects(bounds, r)
    }

    fun overlaps(r: IntRect): Boolean {
        return composeRect.overlaps(r)
    }

    // Draw only visible obstacles, and in the right color
    override fun onDraw(shape: Shape, canvas: Canvas, paint: Paint) {
        if (hidden)
            return
        paint.color = ColoredPart.OBSTACLE.color
        super.onDraw(shape, canvas, paint)
    }
}
