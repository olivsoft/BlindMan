package ch.olivsoft.android.blindman

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.PaintDrawable
import androidx.compose.ui.graphics.toComposeIntRect
import androidx.compose.ui.unit.IntRect

class Obstacle(ix: Int, iy: Int, size: Int) : PaintDrawable() {
    // We implement all necessary methods in order to relieve
    // the caller from taking care of the obstacle's state logic.

    private var hit = false
    private var visible = true
    private val composeRect: IntRect

    init {
        // The allocated shape in a PaintDrawable is
        // a rectangle defined by its bounds
        setBounds(
            ix * size, iy * size,
            (ix + 1) * size, (iy + 1) * size
        )
        setCornerRadius(0.1f * size)
        paint.isAntiAlias = true
        composeRect = bounds.toComposeIntRect()
    }

    fun isHit(): Boolean {
        return hit
    }

    fun setHit() {
        hit = true
        visible = true
    }

    fun setHidden() {
        visible = false
    }

    fun setVisibleIfHit() {
        visible = hit
    }

    fun setVisible() {
        visible = true
    }

    // Convenience methods for intersection checks
    fun intersects(r: Rect): Boolean {
        return Rect.intersects(bounds, r)
    }

    fun overlaps(r: IntRect): Boolean {
        return composeRect.overlaps(r)
    }

    // Draw visible obstacles in the current color
    override fun draw(canvas: Canvas) {
        draw(canvas, ColoredPart.OBSTACLE.color)
    }

    fun draw(canvas: Canvas, color: Int) {
        if (visible) {
            paint.color = color
            super.draw(canvas)
        }
    }
}
