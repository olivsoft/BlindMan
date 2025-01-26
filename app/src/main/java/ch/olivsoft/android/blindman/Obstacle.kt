package ch.olivsoft.android.blindman

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.shapes.Shape

class Obstacle(ix: Int, iy: Int, size: Int) : PaintDrawable() {
    // Some get and set.
    // We do NOT use the inherited setVisible method and hope
    // that no-one ever calls isVisible. That is also why we use
    // "hidden" instead of "visible" as property of an obstacle.
    // We implement many simple methods in order to relieve
    // the caller from taking care of the obstacle's state logic.

    private var hit = false
    private var hidden = false

    init {
        // The allocated shape in a PaintDrawable is
        // a rectangle defined by its bounds
        setBounds(ix * size, iy * size, (ix + 1) * size, (iy + 1) * size)
        setCornerRadius(0.1f * size)
        paint.isAntiAlias = true
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

    // Convenience method for intersection check
    fun intersects(r: Rect): Boolean {
        return Rect.intersects(bounds, r)
    }

    // Draw only visible obstacles, and in the right color
    override fun onDraw(shape: Shape, canvas: Canvas, paint: Paint) {
        if (hidden)
            return
        paint.color = ColoredPart.OBSTACLE.color
        super.onDraw(shape, canvas, paint)
    }
}
