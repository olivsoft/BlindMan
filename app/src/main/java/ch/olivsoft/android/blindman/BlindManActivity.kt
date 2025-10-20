package ch.olivsoft.android.blindman

import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Checkable
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    }

    // View and Music Player
    private lateinit var bmView: BlindManView
    private lateinit var mPlayer: MusicPlayer

    // Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure ad banner. Test devices are older and current phones.
        val testDeviceIds = listOf(
            "98DDF74ECDE599B008274ED3B5C5DCA5",
            "54A8240637407DBE6671033FDA2C7FCA",
            "B6B9CB212805EAF05227298CC384418C"
        )
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        )

        // Assign bindings, load toolbar, content view and ad view
        with(MainBinding.inflate(layoutInflater)) {
            bmView = gameView
            bmView.textView = textView
            setContentView(root)
            setSupportActionBar(toolbar)
            adView.loadAd(AdRequest.Builder().build())
        }

        // Create music player (needed in preferences)
        mPlayer = MusicPlayer(
            this, R.raw.nervous_cubase, true,
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(LOG_TAG, "MusicPlayer error", error)
                    mPlayer.toggle(false)
                    showDialogFragment(R.id.midi)
                }
            })

        // Assign saved preference values to their variables.
        // Use default values from their declarations if available.
        val p = getPreferences(MODE_PRIVATE)
        with(bmView) {
            level = p.getInt(PREF_LEVEL, level)
            size = p.getInt(PREF_SIZE, size)
            background = p.getInt(PREF_BACKGROUND, background)
            lives = p.getInt(PREF_LIVES, lives)
            isHapticFeedbackEnabled = p.getBoolean(PREF_HAPTICS, true)
            isSoundEffectsEnabled = p.getBoolean(PREF_SOUND, true)
        }
        mPlayer.isMusicEnabled = p.getBoolean(PREF_MUSIC, mPlayer.isMusicEnabled)
        ColoredPart.getAllFromPreferences(p, PREF_COL_)
        Log.d(LOG_TAG, "Preferences loaded")

        // Set volume control
        setVolumeControlStream()

        // Add menu
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.quit -> finish()
                    else -> showDialogFragment(menuItem.itemId)
                }
                return true
            }
        })

        // Show help dialog at very first execution
        if (p.getBoolean(PREF_FIRST, true))
            showDialogFragment(R.id.help)
    }

    override fun onStart() {
        super.onStart()
        mPlayer.start()
    }

    override fun onPause() {
        super.onPause()
        mPlayer.pause()

        // This is the recommended place to save persistent
        // settings (not onStop)
        getPreferences(MODE_PRIVATE).edit {
            with(bmView) {
                putInt(PREF_LEVEL, level)
                putInt(PREF_SIZE, size)
                putInt(PREF_BACKGROUND, background)
                putInt(PREF_LIVES, lives)
                putBoolean(PREF_HAPTICS, isHapticFeedbackEnabled)
                putBoolean(PREF_SOUND, isSoundEffectsEnabled)
            }
            putBoolean(PREF_MUSIC, mPlayer.isMusicEnabled)
            ColoredPart.putAllToPreferences(this, PREF_COL_)
            putBoolean(PREF_FIRST, false)
        }
    }

    override fun onResume() {
        super.onResume()
        mPlayer.resume()
        setVolumeControlStream()
    }

    override fun onStop() {
        super.onStop()
        mPlayer.stop()
    }

    // Keys are treated here
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN)
            return false

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

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // Show the dialog fragment
    private fun showDialogFragment(id: Int) {
        BlindManDialogFragment.newInstance(id).show(supportFragmentManager, "dialog")
    }

    // This is the relevant dialog creation method. It is called through the dialog fragment.
    fun createDialog(id: Int): AppCompatDialog {
        // First we treat the color picker
        if (id < ColoredPart.entries.size) {
            val title = resources.getStringArray(R.array.items_colors)[id]
            val cp = ColoredPart.entries[id]

            // We can embed our nice color picker view into a regular dialog.
            // For that we use the provided factory method.
            return ColorPickerView.createDialog(
                this, title, cp.color
            ) { dialog, which ->
                dialog.dismiss()
                cp.color = which
                bmView.invalidate()
                showDialogFragment(R.id.colors)
            }
        }

        // Now we treat all the cases which can easily be built as an AlertDialog.
        // We use custom ArrayAdapter layouts for all list choice types.
        val b = AlertDialog.Builder(this)

        when (id) {
            R.id.level -> {
                b.setTitle(R.string.title_level)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        resources.getStringArray(R.array.items_level)
                    ), bmView.level - 1
                ) { dialog, which ->
                    dialog.dismiss()
                    bmView.newGame(which + 1)
                }
                return b.create()
            }

            R.id.size -> {
                b.setTitle(R.string.title_size)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        resources.getStringArray(R.array.items_size)
                    ), bmView.size - 1
                ) { dialog, which ->
                    dialog.dismiss()
                    bmView.initField(which + 1)
                    bmView.newGame(0)
                }
                return b.create()
            }

            R.id.lives -> {
                b.setTitle(R.string.title_lives)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        BlindManView.ALLOWED_LIVES.map {
                            if (it == 0) "∞" else it.toString()
                        }
                    ), BlindManView.ALLOWED_LIVES.indexOf(bmView.lives)
                ) { dialog, which ->
                    dialog.dismiss()
                    bmView.lives = BlindManView.ALLOWED_LIVES[which]
                }
                return b.create()
            }

            R.id.colors -> {
                b.setTitle(R.string.title_colors)
                b.setPositiveButton(R.string.title_close, null)
                // Avoid dismissing the dialog when a list item is clicked
                b.setAdapter(
                    ArrayAdapter(
                        this, R.layout.dialog_list_item,
                        resources.getStringArray(R.array.items_colors)
                    ), null
                )
                return b.create().apply {
                    with(listView) {
                        itemsCanFocus = false
                        setOnItemClickListener { _, _, position, _ ->
                            if (position == ColoredPart.entries.size) {
                                // Reset colors
                                ColoredPart.resetAll()
                                bmView.invalidate()
                            } else {
                                // Call the color picker dialog
                                dismiss()
                                showDialogFragment(position)
                            }
                        }
                    }
                }
            }

            R.id.background -> {
                b.setTitle(R.string.title_background)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        resources.getStringArray(R.array.items_background)
                    ), bmView.background
                ) { dialog, which ->
                    dialog.dismiss()
                    bmView.background = which
                    bmView.invalidate()
                }
                return b.create()
            }

            // Custom layout version of a multi choice dialog with
            // same font size and appearance as all other list dialogs
            R.id.sound -> {
                b.setTitle(R.string.title_sound)
                b.setAdapter(
                    ArrayAdapter(
                        this, R.layout.dialog_multichoice_item,
                        resources.getStringArray(R.array.items_effects)
                    ), null
                )
                b.setPositiveButton(R.string.title_close, null)
                return b.create().apply {
                    with(listView) {
                        // Set initial states and click listener. Both explicit here.
                        // The OnShowListener is THE way to set initial states.
                        choiceMode = ListView.CHOICE_MODE_MULTIPLE
                        itemsCanFocus = false
                        setOnShowListener {
                            setItemChecked(0, bmView.isHapticFeedbackEnabled)
                            setItemChecked(1, bmView.isSoundEffectsEnabled)
                            setItemChecked(2, mPlayer.isMusicEnabled)
                        }
                        setOnItemClickListener { _, view, position, _ ->
                            val c = view as Checkable
                            when (position) {
                                0 -> bmView.isHapticFeedbackEnabled = c.isChecked

                                1 -> {
                                    bmView.isSoundEffectsEnabled = c.isChecked
                                    setVolumeControlStream()
                                }

                                2 -> {
                                    mPlayer.toggle(c.isChecked)
                                    setVolumeControlStream()
                                }

                                else -> dismiss()
                            }
                        }
                    }
                }
            }

            R.id.about -> return b.setTitle(R.string.title_about)
                .setMessage(R.string.text_about)
                .setPositiveButton(android.R.string.ok, null)
                .create()

            R.id.midi -> return b.setTitle(R.string.title_midi)
                .setMessage(R.string.text_midi)
                .setPositiveButton(android.R.string.ok, null)
                .create()

            // Includes help
            else -> return b.setTitle(R.string.title_help)
                .setMessage(R.string.text_help)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        }
    }

    // Set the right channel for volume control
    private fun setVolumeControlStream() {
        volumeControlStream =
            if (mPlayer.isMusicEnabled || bmView.isSoundEffectsEnabled)
                AudioManager.STREAM_MUSIC
            else
                AudioManager.USE_DEFAULT_STREAM_TYPE
    }
}
