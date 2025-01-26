package ch.olivsoft.android.blindman

import android.graphics.Color

enum class ColoredPart(val defaultColor: Int) {
    FIELD(Color.rgb(0, 0x80, 0xFF)),
    PLAYER(Color.rgb(0xFF, 0x80, 0)),
    GOAL(Color.rgb(0, 0xFF, 0x80)),
    OBSTACLE(Color.rgb(0xC0, 0xFF, 0));

    var color: Int = 0

    fun reset() {
        this.color = this.defaultColor
    }

    companion object {
        fun resetAll() {
            for (c in entries) c.reset()
        }
    }
}
