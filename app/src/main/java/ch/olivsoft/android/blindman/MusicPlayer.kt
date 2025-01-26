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
    private val looping: Boolean,
    private val listener: Player.Listener?
) {

    companion object {
        // Constants
        private val LOG_TAG = MusicPlayer::class.simpleName
    }

    // Public variables
    var isMusicEnabled: Boolean = false

    // Private and inner class access variables
    private var player: ExoPlayer? = null
    private val musicUri: Uri
    private var musicPosition: Long = 0

    // Constructor
    init {
        val resources = context.resources
        this.musicUri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(resId))
            .appendPath(resources.getResourceTypeName(resId))
            .appendPath(resources.getResourceEntryName(resId))
            .build()
    }

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
        try {
            // For starting, we always recreate a player with
            // the convenient factory method. Equally, when stopping
            // we always release the player immediately.
            player = ExoPlayer.Builder(context).build()
            player!!.setMediaItem(MediaItem.fromUri(musicUri))
            if (looping) player!!.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            listener?.let { player!!.addListener(it) }
            player!!.prepare()
            player!!.play()
        } catch (e: Exception) {
            stopOnError("Exception thrown in start", e)
        }
    }

    fun pause() {
        if (!isMusicEnabled || player == null)
            return
        try {
            musicPosition = player!!.currentPosition
            player!!.pause()
        } catch (e: Exception) {
            stopOnError("Exception thrown in pause", e)
        }
    }

    fun resume() {
        if (!isMusicEnabled || player == null)
            return
        try {
            player!!.seekTo(musicPosition)
            player!!.play()
        } catch (e: Exception) {
            stopOnError("Exception thrown in resume", e)
        }
    }

    fun stop() {
        if (player == null)
            return
        // Be very careful here because if stop is called in released state
        // (which is possible, e.g., at application end) this will throw
        // an exception. We want to release the player after each stop because
        // we re-create it with each start. The final statement
        // setting the player to null helps to guard against this situation.
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
