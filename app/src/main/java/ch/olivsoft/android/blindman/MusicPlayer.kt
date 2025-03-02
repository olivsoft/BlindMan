package ch.olivsoft.android.blindman

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Encapsulates an [ExoPlayer] for safe
 * playing and gap-less looping of a simple music resource.
 *
 * In the constructor, an [androidx.media3.common.Player.Listener]
 * can be passed that is called back in case of an error.
 * If this is not required set the listener argument to null.
 *
 * @author Oliver Fritz, OlivSoft
 */
class MusicPlayer(
    private val context: Context,
    resId: Int,
    looping: Boolean,
    private val listener: Player.Listener?
) {

    companion object {
        // Constants
        private val LOG_TAG = MusicPlayer::class.simpleName
    }

    // Public variables
    var isMusicEnabled: Boolean = false

    // Private variables
    private var player: ExoPlayer? = null
    private var position = 0L
    private val repeat = if (looping) ExoPlayer.REPEAT_MODE_ONE else ExoPlayer.REPEAT_MODE_OFF
    private val music = MediaItem.fromUri(context.resources.run {
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(getResourcePackageName(resId))
            .appendPath(getResourceTypeName(resId))
            .appendPath(getResourceEntryName(resId))
            .build()
    })

    // Convenience method for error and exception handling
    private fun stopOnError(message: String, e: Exception) {
        // Switch music off all together and inform user with the
        // already defined onError callback (with fairly useless arguments).
        Log.e(LOG_TAG, message, e)
        toggle(false)
        listener?.onPlayerError((e as PlaybackException))
    }

    // The magic toggle function
    fun toggle(enableMusic: Boolean) {
        // We only do something if we really change something
        if (enableMusic == isMusicEnabled)
            return
        isMusicEnabled = enableMusic
        if (isMusicEnabled) start()
        else stop()
    }

    // Quasi-overridden and enriched methods for encapsulated MediaPlayer
    fun start() {
        if (!isMusicEnabled)
            return
        // For starting, we always recreate a player.
        // When stopping, we always release the player again.
        try {
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(music)
                repeatMode = repeat
                listener?.let { addListener(it) }
                prepare()
                play()
            }
        } catch (e: Exception) {
            stopOnError("Exception thrown in start", e)
        }
    }

    fun pause() {
        if (!isMusicEnabled || player == null)
            return
        try {
            position = player!!.currentPosition
            player!!.pause()
        } catch (e: Exception) {
            stopOnError("Exception thrown in pause", e)
        }
    }

    fun resume() {
        if (!isMusicEnabled || player == null)
            return
        try {
            player!!.seekTo(position)
            player!!.play()
        } catch (e: Exception) {
            stopOnError("Exception thrown in resume", e)
        }
    }

    fun stop() {
        if (player == null)
            return
        // Careful here! If stop is called in released state
        // (which is possible, e.g., at application end) this will throw
        // an exception. The final statement setting the player to null
        // helps to guard against this situation.
        try {
            player!!.stop()
            player!!.release()
        } catch (e: Exception) {
            // We conclude that the player was already stopped and/or released
            Log.e(LOG_TAG, "Exception thrown in stop", e)
        } finally {
            player = null
        }
    }
}
