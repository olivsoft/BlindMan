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
    private val hasAnimationListener: Boolean
) {
    GRAB(HapticFeedbackConstants.LONG_PRESS, -1, false, false),
    HIT(HapticFeedbackConstants.VIRTUAL_KEY, R.raw.boing, false, false),
    GOAL(-1, R.raw.tada, true, true),
    OVER(HapticFeedbackConstants.VIRTUAL_KEY, R.raw.crash, true, false);

    companion object {
        private val LOG_TAG = Effect::class.simpleName
        private lateinit var soundPool: SoundPool

        fun loadDynamicElements(
            context: Context?,
            animationListener: Animation.AnimationListener?
        ) {
            // Create SoundPool and AlphaAnimation
            soundPool = SoundPool.Builder().setMaxStreams(3).build()
            val alphaAnimation = AlphaAnimation(1.0f, 0.7f)
            alphaAnimation.duration = 50
            // In total this gives 3 dim-down-then-brighten-up phases
            alphaAnimation.repeatMode = Animation.REVERSE
            alphaAnimation.repeatCount = 5

            // Load sound and animations according to flags
            for (e in entries) {
                if (e.rawSoundID >= 0) {
                    e.soundID = soundPool.load(context, e.rawSoundID, 1)
                    Log.d(LOG_TAG, e.name + " sound loaded")
                }
                if (e.hasAnimation) {
                    e.animation = alphaAnimation
                    if (e.hasAnimationListener) {
                        e.animation!!.setAnimationListener(animationListener)
                    }
                    Log.d(LOG_TAG, e.name + " animation loaded")
                }
            }
        }
    }

    private var soundID = -1
    private var animation: Animation? = null

    fun makeEffect(view: View) {
        // These calls all return immediately, the effects are done
        // in parallel asynchronously. So, the order does not matter.
        animation?.let { view.startAnimation(it) }
        if (view.isHapticFeedbackEnabled && hapticFeedback >= 0)
            view.performHapticFeedback(hapticFeedback)
        if (view.isSoundEffectsEnabled && soundID >= 0)
            soundPool.play(soundID, 1.0f, 1.0f, 0, 0, 1.0f)
    }
}
