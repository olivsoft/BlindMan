package ch.olivsoft.android.blindman;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.util.Log;

/**
 * Encapsulates a {@link MediaPlayer} for safe
 * playing and looping of a simple music resource.
 * Methods are ideally called from the similarly named
 * event handlers in an {@link Activity}.
 * <p/>
 * In the constructor, an {@link OnErrorListener} can be passed
 * that is called back in case of an error. If this is not required
 * set the listener argument to null.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class MusicPlayer {
    // Constants
    private static final String LOG_TAG = MusicPlayer.class.getSimpleName();

    // Public variables
    public boolean isMusicEnabled = false;
    // Private and inner class access variables
    private MediaPlayer mp;
    private final Context context;
    private final int resid;
    private final OnErrorListener listener;
    private final boolean looping;
    private int musicPosition = 0;

    // Constructor
    public MusicPlayer(Context context, int resid, boolean looping, OnErrorListener listener) {
        this.context = context;
        this.resid = resid;
        this.looping = looping;
        this.listener = listener;
        this.mp = null;
    }

    // Convenience method for error and exception handling
    private void stopOnError(String message, Exception e) {
        // Switch music off all together and inform user with the
        // already defined onError callback (with fairly useless arguments).
        // The exception may be null without causing problems.
        Log.e(LOG_TAG, message, e);
        isMusicEnabled = false;
        stop();
        if (listener != null)
            listener.onError(mp, 0, 0);
    }

    // The magic toggle function
    public void toggle(boolean enableMusic) {
        // We only do something if we really change something
        if (enableMusic == isMusicEnabled)
            return;

        isMusicEnabled = enableMusic;
        if (isMusicEnabled)
            start();
        else
            stop();
    }

    // Quasi-overridden and enriched methods for encapsulated MediaPlayer
    public void start() {
        // Check if we need to do something
        if (!isMusicEnabled)
            return;

        try {
            // For starting, we always recreate a player with
            // the convenient factory method. Equally, when stopping
            // we always release the player immediately.
            mp = MediaPlayer.create(context, resid);
            if (mp == null)
                stopOnError("MediaPlayer.create returned null", null);
            mp.setLooping(looping);
            mp.setOnErrorListener((mp, what, extra) -> {
                stopOnError("MusicPlayer.onError called", null);
                return true;
            });
            mp.start();
        } catch (Exception e) {
            stopOnError("Exception thrown in start", e);
        }
    }

    public void pause() {
        if (!isMusicEnabled || mp == null)
            return;

        try {
            musicPosition = mp.getCurrentPosition();
            mp.pause();
        } catch (Exception e) {
            stopOnError("Exception thrown in pause", e);
        }
    }

    public void resume() {
        if (!isMusicEnabled || mp == null)
            return;

        try {
            mp.seekTo(musicPosition);
            mp.start();
        } catch (Exception e) {
            stopOnError("Exception thrown in resume", e);
        }
    }

    public void stop() {
        if (mp == null)
            return;

        // Be very careful here because if stop is called in released state
        // (which is possible, e.g., at application end) this will throw
        // an exception. We want to release the player after each stop because
        // we re-create it with each start. The final statement
        // setting the player to null helps to guard against this situation.
        try {
            mp.stop();
            mp.release();
        } catch (Exception e) {
            // We conclude that the player was already stopped and/or released
            Log.e(LOG_TAG, "Exception thrown in stop", e);
        } finally {
            mp = null;
        }
    }
}
