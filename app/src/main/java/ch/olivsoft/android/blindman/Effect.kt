package ch.olivsoft.android.blindman

import android.content.Context
import android.media.SoundPool
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation

enum class Effect(
    private val hapticFeedback: Int,
    private val rawSoundID: Int,
    private val hasAnimation: Boolean,
    private val hasListener: Boolean
) {
    GRAB(HapticFeedbackConstants.LONG_PRESS, -1, false, false),
    HIT(HapticFeedbackConstants.VIRTUAL_KEY, R.raw.boing, false, false),
    GOAL(-1, R.raw.tada, true, true),
    OVER(HapticFeedbackConstants.VIRTUAL_KEY, R.raw.crash, true, false);

    private var soundID = -1
    private var alphaAnimation: Animation? = null

    companion object {
        private val LOG_TAG = Effect::class.simpleName
        private val soundPool = SoundPool.Builder().setMaxStreams(3).build()

        fun loadDynamicElements(context: Context?, listener: Animation.AnimationListener) {
            // Load sound and animations according to flags
            entries.forEach {
                with(it) {
                    if (rawSoundID >= 0) {
                        soundID = soundPool.load(context, rawSoundID, 1)
                        Log.d(LOG_TAG, "$name sound loaded")
                    }
                    if (hasAnimation) {
                        // Alpha animation with 3 dim-down-then-brighten-up phases
                        alphaAnimation = AlphaAnimation(1.0f, 0.7f).apply {
                            duration = 50
                            repeatMode = Animation.REVERSE
                            repeatCount = 5
                            if (hasListener)
                                setAnimationListener(listener)
                        }
                        Log.d(LOG_TAG, "$name animation loaded")
                    }
                }
            }
        }
    }

    fun makeEffect(view: View) {
        // These calls all return immediately, the effects are done
        // in parallel asynchronously. Therefore, the order does not matter.
        // We use "alphaAnimation" because View also has an "animation" property.
        with(view) {
            if (hasAnimation && alphaAnimation != null)
                startAnimation(alphaAnimation)
            if (isHapticFeedbackEnabled && hapticFeedback >= 0)
                performHapticFeedback(hapticFeedback)
            if (isSoundEffectsEnabled && soundID >= 0)
                soundPool.play(soundID, 1.0f, 1.0f, 0, 0, 1.0f)
        }
    }
}
