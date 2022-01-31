package ch.olivsoft.android.blindman;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.PaintDrawable;

public class Obstacle extends PaintDrawable {
    private boolean hit = false;
    private boolean hidden = false;

    Obstacle(int ix, int iy, int size) {
        // The allocated shape in a PaintDrawable is
        // a rectangle defined by its bounds
        this.setBounds(ix * size, iy * size, (ix + 1) * size, (iy + 1) * size);
        this.setCornerRadius(0.1f * size);
        this.getPaint().setAntiAlias(true);
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

    @Override
    public void draw(Canvas canvas) {
        if (!hidden)
            super.draw(canvas);
    }
}
