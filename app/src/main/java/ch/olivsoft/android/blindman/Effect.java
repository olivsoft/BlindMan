package ch.olivsoft.android.blindman;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;

public enum Effect {
    GRAB(HapticFeedbackConstants.LONG_PRESS, -1, false, false),
    HIT(HapticFeedbackConstants.VIRTUAL_KEY, R.raw.boing, false, false),
    GOAL(-1, R.raw.tada, true, true),
    OVER(HapticFeedbackConstants.VIRTUAL_KEY, R.raw.crash, true, false);

    private final static String LOG_TAG = Effect.class.getSimpleName();
    private static SoundPool soundPool = null;

    private final int hapticFeedback;
    private final int rawSoundID;
    private final boolean hasAnimation;
    private final boolean hasAnimationListener;
    private int soundID = -1;
    private Animation animation = null;

    Effect(int hapticFeedback, int rawID, boolean hasAnimation, boolean hasAnimationListener) {
        this.hapticFeedback = hapticFeedback;
        this.rawSoundID = rawID;
        this.hasAnimation = hasAnimation;
        this.hasAnimationListener = hasAnimationListener;
    }

    public static void loadDynamicElements(Context context, AnimationListener animationListener) {
        // Create SoundPool and AlphaAnimation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            soundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
        else
            soundPool = new SoundPool.Builder().setMaxStreams(3).build();
        AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.7f);
        alphaAnimation.setDuration(50);
        // In total this gives 3 dim-down-then-brighten-up phases
        alphaAnimation.setRepeatMode(Animation.REVERSE);
        alphaAnimation.setRepeatCount(5);

        // Load sound and animations according to flags
        for (Effect e : Effect.values()) {
            if (e.rawSoundID >= 0) {
                e.soundID = soundPool.load(context, e.rawSoundID, 1);
                Log.d(LOG_TAG, e.name() + " sound loaded");
            }
            if (e.hasAnimation) {
                e.animation = alphaAnimation;
                if (e.hasAnimationListener) {
                    e.animation.setAnimationListener(animationListener);
                }
                Log.d(LOG_TAG, e.name() + " animation loaded");
            }
        }
    }

    public void makeEffect(View view) {
        // These calls all return immediately, the effects are done
        // in parallel asynchronously. So, the order does not matter.
        if (animation != null)
            view.startAnimation(animation);

        if (view.isHapticFeedbackEnabled() && hapticFeedback >= 0)
            view.performHapticFeedback(hapticFeedback);

        if (soundPool != null && view.isSoundEffectsEnabled() && soundID >= 0)
            soundPool.play(soundID, 1.0f, 1.0f, 0, 0, 1.0f);
    }
}
