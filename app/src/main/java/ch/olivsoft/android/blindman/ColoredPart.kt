package ch.olivsoft.android.blindman

import android.content.SharedPreferences
import android.graphics.Color

enum class ColoredPart(val defaultColor: Int) {
    FIELD(Color.rgb(0, 0x80, 0xFF)),
    PLAYER(Color.rgb(0xFF, 0x80, 0)),
    OBSTACLE(Color.rgb(0xC0, 0xFF, 0)),
    GOAL(Color.rgb(0, 0xFF, 0x80));

    var color = defaultColor

    fun reset() {
        color = defaultColor
    }

    companion object {
        fun resetAll() {
            entries.forEach { it.reset() }
        }

        fun getAllFromPreferences(p: SharedPreferences, prefix: String) {
            entries.forEach {
                it.color = p.getInt(prefix + it.name, it.defaultColor)
            }
        }

        fun putAllToPreferences(e: SharedPreferences.Editor, prefix: String) {
            entries.forEach {
                e.putInt(prefix + it.name, it.color)
            }
        }
    }
}
