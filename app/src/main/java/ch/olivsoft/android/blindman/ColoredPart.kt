package ch.olivsoft.android.blindman;

import android.graphics.Color;

public enum ColoredPart {
    FIELD(Color.rgb(0, 0x80, 0xFF)),
    PLAYER(Color.rgb(0xFF, 0x80, 0)),
    GOAL(Color.rgb(0, 0xFF, 0x80)),
    OBSTACLE(Color.rgb(0xC0, 0xFF, 0));

    public int color;
    public final int defaultColor;

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
