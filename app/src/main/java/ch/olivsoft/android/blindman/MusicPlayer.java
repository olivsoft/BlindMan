package ch.olivsoft.android.blindman;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Encapsulates a {@link ExoPlayer} for safe
 * playing and gap-less looping of a simple music resource.
 * <p/>
 * In the constructor, an {@link androidx.media3.common.Player.Listener}
 * can be passed that is called back in case of an error.
 * If this is not required set the listener argument to null.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class MusicPlayer {

    // Constants
    private static final String LOG_TAG = MusicPlayer.class.getSimpleName();

    // Public variables
    public boolean isMusicEnabled = false;

    // Private and inner class access variables
    private ExoPlayer player;
    private final Uri musicUri;
    private final Context context;
    private final Player.Listener listener;
    private final boolean looping;
    private long musicPosition = 0;

    // Constructor
    public MusicPlayer(Context context, int resId, boolean looping, Player.Listener listener) {
        this.context = context;
        this.looping = looping;
        this.listener = listener;
        this.player = null;

        Resources resources = context.getResources();
        this.musicUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(resId))
                .appendPath(resources.getResourceTypeName(resId))
                .appendPath(resources.getResourceEntryName(resId))
                .build();
    }

    // Convenience method for error and exception handling
    private void stopOnError(String message, Exception e) {
        // Switch music off all together and inform user with the
        // already defined onError callback (with fairly useless arguments).
        Log.e(LOG_TAG, message, e);
        toggle(false);
        if (listener != null)
            listener.onPlayerError((PlaybackException) e);
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
            player = new ExoPlayer.Builder(context).build();
            player.setMediaItem(MediaItem.fromUri(musicUri));
            if (looping)
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ONE);
            if (listener != null)
                player.addListener(listener);
            player.prepare();
            player.play();
        } catch (Exception e) {
            stopOnError("Exception thrown in start", e);
        }
    }

    public void pause() {
        if (!isMusicEnabled || player == null)
            return;

        try {
            musicPosition = player.getCurrentPosition();
            player.pause();
        } catch (Exception e) {
            stopOnError("Exception thrown in pause", e);
        }
    }

    public void resume() {
        if (!isMusicEnabled || player == null)
            return;

        try {
            player.seekTo(musicPosition);
            player.play();
        } catch (Exception e) {
            stopOnError("Exception thrown in resume", e);
        }
    }

    public void stop() {
        if (player == null)
            return;

        // Be very careful here because if stop is called in released state
        // (which is possible, e.g., at application end) this will throw
        // an exception. We want to release the player after each stop because
        // we re-create it with each start. The final statement
        // setting the player to null helps to guard against this situation.
        try {
            player.stop();
            player.release();
        } catch (Exception e) {
            // We conclude that the player was already stopped and/or released
            Log.e(LOG_TAG, "Exception thrown in stop", e);
        } finally {
            player = null;
        }
    }
}
