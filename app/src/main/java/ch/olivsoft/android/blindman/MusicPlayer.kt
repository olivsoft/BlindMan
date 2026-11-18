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
 * can be passed that is called in case of an error.
 * If this is not required, set the listener argument to null.
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
        private val LOG_TAG = MusicPlayer::class.simpleName
    }

    // Private variables
    private var player: ExoPlayer? = null
    private var position = 0L
    private val repeat = if (looping) ExoPlayer.REPEAT_MODE_ONE else ExoPlayer.REPEAT_MODE_OFF
    private val music = MediaItem.fromUri(
        context.resources.run {
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(getResourcePackageName(resId))
                .appendPath(getResourceTypeName(resId))
                .appendPath(getResourceEntryName(resId))
                .build()
        }
    )

    // Convenience method for error and exception handling
    private fun stopOnError(message: String, e: Exception) {
        // Stop and release the player and pass the exception to the listener
        stop()
        Log.e(LOG_TAG, message, e)
        listener?.onPlayerError(e as PlaybackException)
    }

    // The magic toggle function implemented as a property
    var musicEnabled = false
        set(value) {
            // No action if no change
            if (value != field) {
                field = value
                if (field) play() else stop()
            }
        }

    // Quasi-overridden and enriched methods for the encapsulated ExoPlayer
    fun play() {
        if (!musicEnabled || player?.playWhenReady == true)
            return
        try {
            if (player == null) {
                player = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(music)
                    repeatMode = repeat
                    listener?.let { addListener(it) }
                    prepare()
                }
            }
            player!!.seekTo(position)
            player!!.play()
            Log.d(LOG_TAG, "Player started")
        } catch (e: Exception) {
            stopOnError("Exception thrown in play", e)
        }
    }

    fun pause() {
        if (!musicEnabled || player == null || player?.playWhenReady == false)
            return
        try {
            player!!.pause()
            position = player!!.currentPosition
            Log.d(LOG_TAG, "Player paused")
        } catch (e: Exception) {
            stopOnError("Exception thrown in pause", e)
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
            Log.d(LOG_TAG, "Player stopped and released")
        } catch (e: Exception) {
            // We conclude that the player was already stopped and/or released
            Log.e(LOG_TAG, "Exception thrown in stop", e)
        } finally {
            player = null
        }
    }
}
