package ch.olivsoft.android.blindman;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.shapes.Shape;

public class Obstacle extends PaintDrawable {

    private boolean hit = false;
    private boolean hidden = false;

    Obstacle(int ix, int iy, int size) {
        super();
        // The allocated shape in a PaintDrawable is
        // a rectangle defined by its bounds
        setBounds(ix * size, iy * size, (ix + 1) * size, (iy + 1) * size);
        setCornerRadius(0.1f * size);
        getPaint().setAntiAlias(true);
    }

    // Some get and set.
    // We do NOT use the inherited setVisible method and hope
    // that no-one ever calls isVisible. That is also why we use
    // "hidden" instead of "visible" as property of an obstacle.
    // We implement many simple methods in order to relieve
    // the caller from taking care of the obstacle's state logic.
    boolean isHit() {
        return hit;
    }

    void setHit() {
        hit = true;
        hidden = false;
    }

    void setHidden() {
        hidden = true;
    }

    void setVisibleIfHit() {
        hidden = !hit;
    }

    void setVisible() {
        hidden = false;
    }

    // Convenience method for intersection check
    boolean intersects(Rect r) {
        return Rect.intersects(getBounds(), r);
    }

    // Draw only visible obstacles, and in the right color
    @Override
    protected void onDraw(Shape shape, Canvas canvas, Paint paint) {
        if (hidden)
            return;
        paint.setColor(ColoredPart.OBSTACLE.color);
        super.onDraw(shape, canvas, paint);
    }
}
