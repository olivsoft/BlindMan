package ch.olivsoft.android.blindman

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Checkable
import android.widget.ListView
import android.widget.PopupMenu
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import ch.olivsoft.android.blindman.databinding.MainBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

@Suppress("KotlinConstantConditions")
class BlindManActivity : AppCompatActivity(), MenuProvider {

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

        // Test devices are older and current phones
        private val testDeviceIds = listOf(
            "98DDF74ECDE599B008274ED3B5C5DCA5",
            "54A8240637407DBE6671033FDA2C7FCA",
            "B6B9CB212805EAF05227298CC384418C"
        )
    }

    // View model
    private val bmViewModel: BlindManViewModel by viewModels()

    // Music player
    private lateinit var mPlayer: MusicPlayer
    private var adView: AdView? = null

    // Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        // Compatibility
        val v34up = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val darkBarColor =
            if (v34up) resources.getColor(
                android.R.color.system_surface_dark,
                resources.newTheme()
            )
            else 0x211F26
        val darkBarStyle = SystemBarStyle.dark(scrim = darkBarColor)
        enableEdgeToEdge(
            statusBarStyle = darkBarStyle,
            navigationBarStyle = darkBarStyle
        )
        super.onCreate(savedInstanceState)

        // The music player is initialized here because context
        // and resources are not safe to use before onCreate
        mPlayer = MusicPlayer(
            this, R.raw.nervous_cubase, true,
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(LOG_TAG, "MusicPlayer error", error)
                    mPlayer.toggle(false)
                    bmViewModel.isMusicEnabled = false
                    showDialogFragment(R.id.midi)
                }
            }
        )

        // Saved preference values (or initially their default values)
        // are loaded here and saved again in onPause
        val p = getPreferences(MODE_PRIVATE)
        with(bmViewModel) {
            level = p.getInt(PREF_LEVEL, level)
            size = p.getInt(PREF_SIZE, size)
            background = p.getInt(PREF_BACKGROUND, background)
            lives = p.getInt(PREF_LIVES, lives)
            isHapticFeedbackEnabled = p.getBoolean(PREF_HAPTICS, true)
            isSoundEffectsEnabled = p.getBoolean(PREF_SOUND, true)
            isMusicEnabled = p.getBoolean(PREF_MUSIC, isMusicEnabled)
            isMusicEnabledData.observe(this@BlindManActivity) {
                mPlayer.toggle(it)
            }
        }
        ColoredPart.getAllFromPreferences(p, PREF_COL_)
        // Show the help dialog at the first run only
        val showHelp = p.getBoolean(PREF_FIRST, true).also {
            if (it) p.edit { putBoolean(PREF_FIRST, false) }
        }
        Log.d(LOG_TAG, "Preferences loaded")

        // Volume control. Must be after loading preferences.
        setVolumeControlStream()

        // This is needed because of test devices
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        )
        MobileAds.initialize(this)

        // Layout, bindings, initializations
        if (BuildConfig.USE_COMPOSE) {
            Effect.loadDynamicElements(this, null)
            val menuItems = mutableMapOf<MenuItem, (() -> Unit)?>()
            with(PopupMenu(this@BlindManActivity, null).menu) {
                menuInflater.inflate(R.menu.menu_main, this)
                forEach {
                    if (it.isVisible)
                        menuItems[it] = { onMenuItemSelected(it) }
                }
            }
            setContent {
                BlindManTheme {
                    BlindManLayout(
                        modifier = Modifier,
                        menuItems = menuItems,
                        onAdViewCreated = {
                            adView = it
                        },
                        onLayoutCompleted = {
                            if (showHelp) activeDialogId = R.id.help
                        }
                    )
                }
            }
        } else {
            addMenuProvider(this)
            with(MainBinding.inflate(layoutInflater)) {
                bmViewModel.messageTextData.observe(this@BlindManActivity) {
                    textView.text = it
                }
                setContentView(root)
                setSupportActionBar(toolBar)
                adView.loadAd(AdRequest.Builder().build())
                this@BlindManActivity.adView = adView
                if (showHelp) showDialogFragment(R.id.help)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mPlayer.start()
    }

    override fun onPause() {
        super.onPause()
        adView?.pause()
        mPlayer.pause()

        // This is the recommended place to save
        // persistent settings (not onStop)
        getPreferences(MODE_PRIVATE).edit {
            with(bmViewModel) {
                putInt(PREF_LEVEL, level)
                putInt(PREF_SIZE, size)
                putInt(PREF_BACKGROUND, background)
                putInt(PREF_LIVES, lives)
                putBoolean(PREF_HAPTICS, isHapticFeedbackEnabled)
                putBoolean(PREF_SOUND, isSoundEffectsEnabled)
                putBoolean(PREF_MUSIC, isMusicEnabled)
            }
            ColoredPart.putAllToPreferences(this, PREF_COL_)
        }
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
        mPlayer.resume()
        setVolumeControlStream()
    }

    override fun onStop() {
        super.onStop()
        mPlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        adView?.destroy()
        adView = null
    }

    // Menu provider and click handling
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_main, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (val id = menuItem.itemId) {
            R.id.quit -> finish()
            else -> {
                if (BuildConfig.USE_COMPOSE)
                    activeDialogId = id
                else
                    showDialogFragment(id)
            }
        }
        return true
    }

    // Show the dialog fragment
    private fun showDialogFragment(id: Int) {
        BlindManDialogFragment.newInstance(id).show(supportFragmentManager, "dialog")
    }

    // Fill the fragment with the chosen dialog
    fun createDialog(id: Int): AppCompatDialog {
        // First case: Return the color picker dialog directly.
        if (id < ColoredPart.entries.size) {
            val title = resources.getStringArray(R.array.items_colors)[id]
            val cp = ColoredPart.entries[id]
            return ColorPickerView.createDialog(
                this, title, cp.color
            ) { dialog, which ->
                dialog.dismiss()
                cp.color = which
                bmViewModel.invalidateCounter++
                showDialogFragment(R.id.colors)
            }
        }

        // In all other cases, dialogs are built as an AlertDialog.
        // Custom ArrayAdapter layouts are used for all list choice types
        // because the native layouts have design inconsistencies.
        val b = AlertDialog.Builder(this)
        var d: AlertDialog? = null

        when (id) {
            // Single choice dialogs
            R.id.level -> {
                b.setTitle(R.string.title_level)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        resources.getStringArray(R.array.items_level)
                    ), bmViewModel.level - 1
                ) { dialog, which ->
                    dialog.dismiss()
                    bmViewModel.level = which + 1
                }
            }

            R.id.size -> {
                b.setTitle(R.string.title_size)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        resources.getStringArray(R.array.items_size)
                    ), bmViewModel.size - 1
                ) { dialog, which ->
                    dialog.dismiss()
                    bmViewModel.size = which + 1
                }
            }

            R.id.lives -> {
                b.setTitle(R.string.title_lives)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        BlindManViewModel.ALLOWED_LIVES.map {
                            if (it == 0) "∞" else it.toString()
                        }
                    ), BlindManViewModel.ALLOWED_LIVES.indexOf(bmViewModel.lives)
                ) { dialog, which ->
                    dialog.dismiss()
                    bmViewModel.lives = BlindManViewModel.ALLOWED_LIVES[which]
                }
            }

            R.id.background -> {
                b.setTitle(R.string.title_background)
                b.setSingleChoiceItems(
                    ArrayAdapter(
                        this, R.layout.dialog_singlechoice_item,
                        resources.getStringArray(R.array.items_background)
                    ), bmViewModel.background
                ) { dialog, which ->
                    dialog.dismiss()
                    bmViewModel.background = which
                    bmViewModel.invalidateCounter++
                }
            }

            // List item dialog
            R.id.colors -> {
                b.setTitle(R.string.title_colors)
                // Avoid dismissing the dialog when a list item is clicked
                b.setAdapter(
                    ArrayAdapter(
                        this, R.layout.dialog_list_item,
                        resources.getStringArray(R.array.items_colors)
                    ), null
                )
                b.setPositiveButton(R.string.title_close, null)
                d = b.create().apply {
                    with(listView) {
                        itemsCanFocus = false
                        setOnItemClickListener { _, _, position, _ ->
                            if (position == ColoredPart.entries.size) {
                                // Reset colors
                                ColoredPart.resetAll()
                                bmViewModel.invalidateCounter++
                            } else {
                                // Call the color picker dialog
                                dismiss()
                                showDialogFragment(position)
                            }
                        }
                    }
                }
            }

            // Multi choice dialog
            R.id.sound -> {
                b.setTitle(R.string.title_sound)
                b.setAdapter(
                    ArrayAdapter(
                        this, R.layout.dialog_multichoice_item,
                        resources.getStringArray(R.array.items_effects)
                    ), null
                )
                b.setPositiveButton(R.string.title_close, null)
                d = b.create().apply {
                    with(listView) {
                        // Set initial states and click listener. Both explicit here.
                        // The OnShowListener is THE way to set initial states.
                        choiceMode = ListView.CHOICE_MODE_MULTIPLE
                        itemsCanFocus = false
                        setOnShowListener {
                            setItemChecked(0, bmViewModel.isHapticFeedbackEnabled)
                            setItemChecked(1, bmViewModel.isSoundEffectsEnabled)
                            setItemChecked(2, bmViewModel.isMusicEnabled)
                        }
                        setOnItemClickListener { _, view, position, _ ->
                            val c = view as Checkable
                            when (position) {
                                0 -> bmViewModel.isHapticFeedbackEnabled = c.isChecked

                                1 -> {
                                    bmViewModel.isSoundEffectsEnabled = c.isChecked
                                    setVolumeControlStream()
                                }

                                2 -> {
                                    bmViewModel.isMusicEnabled = c.isChecked
                                    setVolumeControlStream()
                                }

                                else -> dismiss()
                            }
                        }
                    }
                }
            }

            // Message dialogs
            R.id.about -> {
                b.setTitle(R.string.title_about)
                b.setMessage(
                    resources.getString(R.string.text_about) +
                            " ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})"
                )
                b.setPositiveButton(android.R.string.ok, null)
            }

            R.id.midi -> {
                b.setTitle(R.string.title_midi)
                b.setMessage(R.string.text_midi)
                b.setPositiveButton(android.R.string.ok, null)
            }

            else -> {
                // "else" simply means help
                b.setTitle(R.string.title_help)
                b.setMessage(R.string.text_help)
                b.setPositiveButton(android.R.string.ok, null)
            }
        }
        // Return the dialog
        return d ?: b.create()
    }

    // Set the right channel for volume control
    fun setVolumeControlStream() {
        volumeControlStream =
            if (bmViewModel.isMusicEnabled || bmViewModel.isSoundEffectsEnabled)
                AudioManager.STREAM_MUSIC
            else
                AudioManager.USE_DEFAULT_STREAM_TYPE
    }
}
