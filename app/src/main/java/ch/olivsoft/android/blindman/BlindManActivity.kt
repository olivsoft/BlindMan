package ch.olivsoft.android.blindman

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import ch.olivsoft.android.blindman.BlindManDialogFragment.Companion.newInstance
import ch.olivsoft.android.blindman.ColorPickerView.Companion.createDialog
import ch.olivsoft.android.blindman.ColoredPart.Companion.resetAll
import ch.olivsoft.android.blindman.databinding.MainBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

class BlindManActivity : AppCompatActivity() {

    companion object {
        // Constants
        private val LOG_TAG = BlindManActivity::class.simpleName

        private const val PREF_FIRST = "PREF_FIRST"
        private const val PREF_LEVEL = "PREF_LEVEL"
        private const val PREF_SIZE = "PREF_SIZE"
        private const val PREF_LIVES = "PREF_LIVES"
        private const val PREF_HAPTICS = "PREF_HAPTICS"
        private const val PREF_SOUND = "PREF_SOUND"
        private const val PREF_MUSIC = "PREF_MUSIC"
        private const val PREF_COL_ = "PREF_COL_"
        private const val PREF_BACKGROUND = "PREF_BACKGROUND"

        private const val DIALOG_LEVEL = 1
        private const val DIALOG_SIZE = 2
        private const val DIALOG_LIVES = 3
        private const val DIALOG_COLORS = 4
        private const val DIALOG_BACKGROUND = 5
        private const val DIALOG_SOUND = 6
        private const val DIALOG_MIDI = 11
        private const val DIALOG_HELP = 21
        private const val DIALOG_ABOUT = 31
        private const val DIALOG_MASK_COLORS = 101
    }

    // View and Music Player
    private lateinit var bmView: BlindManView
    private lateinit var musicPlayer: MusicPlayer

    // Set the right channel for volume control
    private fun setVolumeControlStream() {
        volumeControlStream =
            if (musicPlayer.isMusicEnabled || bmView.isSoundEffectsEnabled)
                AudioManager.STREAM_MUSIC
            else
                AudioManager.USE_DEFAULT_STREAM_TYPE
    }

    // Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Assign bindings, set content view and toolbar
        val binding = MainBinding.inflate(layoutInflater)
        bmView = binding.gameView
        bmView.textView = binding.textView
        Log.d(LOG_TAG, "View inflated and bound")
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Initialize ad banner. Test devices are older and current phones.
        val testDeviceIds = listOf(
            "98DDF74ECDE599B008274ED3B5C5DCA5",
            "54A8240637407DBE6671033FDA2C7FCA",
            "B6B9CB212805EAF05227298CC384418C"
        )
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        )
        binding.adView.loadAd(AdRequest.Builder().build())

        // Create music player (needed in preferences)
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(LOG_TAG, "MusicPlayer error", error)
                musicPlayer.toggle(false)
                doDialog(DIALOG_MIDI)
            }
        }
        musicPlayer = MusicPlayer(this, R.raw.nervous_cubase, true, listener)

        // Assign saved preference values to their variables.
        // Use default values from their declarations if available.
        val p = getPreferences(MODE_PRIVATE)
        bmView.level = p.getInt(PREF_LEVEL, bmView.level)
        bmView.size = p.getInt(PREF_SIZE, bmView.size)
        bmView.background = p.getInt(PREF_BACKGROUND, bmView.background)
        bmView.lives = p.getInt(PREF_LIVES, bmView.lives)
        bmView.isHapticFeedbackEnabled = p.getBoolean(PREF_HAPTICS, true)
        bmView.isSoundEffectsEnabled = p.getBoolean(PREF_SOUND, true)
        musicPlayer.isMusicEnabled = p.getBoolean(PREF_MUSIC, musicPlayer.isMusicEnabled)
        for (c in ColoredPart.entries)
            c.color = p.getInt(PREF_COL_ + c.name, c.defaultColor)
        Log.d(LOG_TAG, "Preferences loaded")

        // Set volume control
        setVolumeControlStream()

        // Show help dialog at very first execution
        if (p.getBoolean(PREF_FIRST, true))
            doDialog(DIALOG_HELP)
    }

    override fun onStart() {
        super.onStart()
        musicPlayer.start()
    }

    override fun onPause() {
        super.onPause()
        musicPlayer.pause()

        // This is the recommended place to save persistent
        // settings (not onStop)
        val e = getPreferences(MODE_PRIVATE).edit()
        e.putInt(PREF_LEVEL, bmView.level)
        e.putInt(PREF_SIZE, bmView.size)
        e.putInt(PREF_BACKGROUND, bmView.background)
        e.putInt(PREF_LIVES, bmView.lives)
        e.putBoolean(PREF_HAPTICS, bmView.isHapticFeedbackEnabled)
        e.putBoolean(PREF_SOUND, bmView.isSoundEffectsEnabled)
        e.putBoolean(PREF_MUSIC, musicPlayer.isMusicEnabled)
        for (c in ColoredPart.entries)
            e.putInt(PREF_COL_ + c.name, c.color)
        e.putBoolean(PREF_FIRST, false)
        e.apply()
    }

    override fun onResume() {
        super.onResume()
        musicPlayer.resume()
        setVolumeControlStream()
    }

    override fun onStop() {
        super.onStop()
        musicPlayer.stop()
    }

    // Menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menus, menu)
        return true
    }

    @SuppressLint("NonConstantResourceId")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_level -> doDialog(DIALOG_LEVEL)
            R.id.menu_size -> doDialog(DIALOG_SIZE)
            R.id.menu_lives -> doDialog(DIALOG_LIVES)
            R.id.menu_colors -> doDialog(DIALOG_COLORS)
            R.id.menu_background -> doDialog(DIALOG_BACKGROUND)
            R.id.menu_sound -> doDialog(DIALOG_SOUND)
            R.id.menu_about -> doDialog(DIALOG_ABOUT)
            R.id.menu_help -> doDialog(DIALOG_HELP)
            R.id.menu_quit -> finish()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // Call the selected dialog
    private fun doDialog(id: Int) {
        newInstance(id).show(supportFragmentManager, "dialog")
    }

    // This is the relevant dialog creation method. It is called through the dialog fragment.
    fun createDialog(id: Int): AppCompatDialog {
        // First we treat the color picker
        if (id >= DIALOG_MASK_COLORS) {
            val icp = id - DIALOG_MASK_COLORS
            val title = resources.getStringArray(R.array.items_colors)[icp]
            val cp = ColoredPart.entries[icp]

            // We can embed our nice color picker view into a regular dialog.
            // For that we use the provided factory method.
            return createDialog(
                this, title, cp.color
            ) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                cp.color = which
                bmView.invalidate()
                doDialog(DIALOG_COLORS)
            }
        }

        // Now we treat all the cases which can easily be built as an AlertDialog.
        // For readability throughout the many cases we don't use chaining.
        // We use custom ArrayAdapter layouts for some of the list choice types.
        val b = AlertDialog.Builder(this)
        val a: ArrayAdapter<String>

        when (id) {
            DIALOG_LEVEL -> {
                b.setTitle(R.string.menu_level)
                a = ArrayAdapter(this, R.layout.dialog_singlechoice_item)
                a.addAll(*resources.getStringArray(R.array.items_level))
                b.setSingleChoiceItems(
                    a, bmView.level - 1
                ) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    bmView.newGame(which + 1)
                }
            }

            DIALOG_SIZE -> {
                b.setTitle(R.string.menu_size)
                a = ArrayAdapter(this, R.layout.dialog_singlechoice_item)
                a.addAll(*resources.getStringArray(R.array.items_size))
                b.setSingleChoiceItems(
                    a, bmView.size - 1
                ) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    bmView.initField(which + 1)
                    bmView.newGame(0)
                }
            }

            DIALOG_LIVES -> {
                b.setTitle(R.string.menu_lives)
                a = ArrayAdapter(this, R.layout.dialog_singlechoice_item)
                for (i in BlindManView.ALLOWED_LIVES)
                    a.add(if (i != 0) i.toString() else "∞")
                val currSel = BlindManView.ALLOWED_LIVES.indexOf(bmView.lives)
                b.setSingleChoiceItems(a, currSel) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    bmView.lives = BlindManView.ALLOWED_LIVES[which]
                }
            }

            DIALOG_COLORS -> {
                b.setTitle(R.string.menu_colors)
                a = ArrayAdapter(this, R.layout.dialog_list_item)
                a.addAll(*resources.getStringArray(R.array.items_colors))
                b.setAdapter(a) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    if (which == ColoredPart.entries.size) {
                        // Reset colors
                        resetAll()
                        bmView.invalidate()
                    } else {
                        // Call color picker dialog
                        doDialog(which + DIALOG_MASK_COLORS)
                    }
                }
                b.setPositiveButton(android.R.string.ok, null)
            }

            DIALOG_BACKGROUND -> {
                b.setTitle(R.string.menu_background)
                a = ArrayAdapter(this, R.layout.dialog_singlechoice_item)
                a.addAll(*resources.getStringArray(R.array.items_background))
                b.setSingleChoiceItems(
                    a, bmView.background
                ) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    bmView.background = which
                    bmView.invalidate()
                }
            }

            DIALOG_SOUND -> {
                b.setTitle(R.string.menu_sound)
                val effEnabled = booleanArrayOf(
                    bmView.isHapticFeedbackEnabled,
                    bmView.isSoundEffectsEnabled,
                    musicPlayer.isMusicEnabled
                )
                // Unfortunately, the adapter cannot be used here. Looks are ok though.
                b.setMultiChoiceItems(
                    R.array.items_effects, effEnabled
                ) { dialog: DialogInterface, which: Int, isChecked: Boolean ->
                    when (which) {
                        0 -> bmView.isHapticFeedbackEnabled = isChecked

                        1 -> {
                            bmView.isSoundEffectsEnabled = isChecked
                            setVolumeControlStream()
                        }

                        2 -> {
                            musicPlayer.toggle(isChecked)
                            setVolumeControlStream()
                        }

                        else -> dialog.dismiss()
                    }
                }
                b.setPositiveButton(android.R.string.ok, null)
            }

            DIALOG_MIDI -> {
                b.setTitle(R.string.title_midi)
                b.setMessage(R.string.text_midi)
                b.setPositiveButton(android.R.string.ok, null)
            }

            DIALOG_ABOUT -> {
                b.setTitle(R.string.menu_about)
                b.setMessage(R.string.text_about)
                b.setPositiveButton(android.R.string.ok, null)
            }

            DIALOG_HELP -> {
                b.setTitle(R.string.menu_help)
                b.setMessage(R.string.text_help)
                b.setPositiveButton(android.R.string.ok, null)
            }

            else -> {
                b.setTitle(R.string.menu_help)
                b.setMessage(R.string.text_help)
                b.setPositiveButton(android.R.string.ok, null)
            }
        }

        return b.create()
    }

    // Keys are treated here
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_NUMPAD_6,
            KeyEvent.KEYCODE_SOFT_RIGHT -> bmView.makeMove(1f, 0f)

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_NUMPAD_4,
            KeyEvent.KEYCODE_SOFT_LEFT -> bmView.makeMove(-1f, 0f)

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_NUMPAD_8,
            KeyEvent.KEYCODE_PAGE_UP -> bmView.makeMove(0f, -1f)

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_NUMPAD_2,
            KeyEvent.KEYCODE_PAGE_DOWN -> bmView.makeMove(0f, 1f)

            KeyEvent.KEYCODE_BACK -> super.onBackPressedDispatcher.onBackPressed()

            else -> return super.onKeyDown(keyCode, event)
        }

        return true
    }
}
