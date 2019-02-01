package ch.olivsoft.android.blindman;

import android.graphics.Color;

public enum ColoredPart {
    FIELD(Color.argb(0xFF, 0, 0x80, 0xFF)),
    PLAYER(Color.argb(0xFF, 0xFF, 0x80, 0)),
    GOAL(Color.argb(0xFF, 0, 0xFF, 0x80)),
    OBSTACLE(Color.argb(0xFF, 0xC0, 0xFF, 0));

    public int color;
    public int defaultColor;

    ColoredPart(int defaultColor) {
        // Constructor ONLY sets defaultColor!
        this.defaultColor = defaultColor;
    }

    public static void resetAll() {
        for (ColoredPart c : ColoredPart.values())
            c.reset();
    }

    public void reset() {
        this.color = this.defaultColor;
    }
}
