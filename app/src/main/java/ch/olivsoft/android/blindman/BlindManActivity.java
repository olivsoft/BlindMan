package ch.olivsoft.android.blindman;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.Toolbar;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;

import java.util.ArrayList;
import java.util.List;

import ch.olivsoft.android.blindman.databinding.MainBinding;

public class BlindManActivity extends AppCompatActivity {

    // Constants
    private static final String LOG_TAG = BlindManActivity.class.getSimpleName();

    private static final String PREF_FIRST = "PREF_FIRST";
    private static final String PREF_LEVEL = "PREF_LEVEL";
    private static final String PREF_SIZE = "PREF_SIZE";
    private static final String PREF_LIVES = "PREF_LIVES";
    private static final String PREF_HAPTICS = "PREF_HAPTICS";
    private static final String PREF_SOUND = "PREF_SOUND";
    private static final String PREF_MUSIC = "PREF_MUSIC";
    private static final String PREF_COL = "PREF_COL_";
    private static final String PREF_BACKGROUND = "PREF_BACKGROUND";

    private static final int DIALOG_LEVEL = 1;
    private static final int DIALOG_SIZE = 2;
    private static final int DIALOG_LIVES = 3;
    private static final int DIALOG_COLORS = 4;
    private static final int DIALOG_BACKGROUND = 5;
    private static final int DIALOG_SOUND = 6;
    private static final int DIALOG_MIDI = 11;
    private static final int DIALOG_HELP = 21;
    private static final int DIALOG_ABOUT = 31;
    private static final int DIALOG_MASK_COLORS = 101;

    // View and Music Player
    private BlindManView bmView;
    private MusicPlayer musicPlayer;

    // Set the right channel for volume control
    private void setVolumeControlStream() {
        setVolumeControlStream(musicPlayer.isMusicEnabled || bmView.isSoundEffectsEnabled()
                ? AudioManager.STREAM_MUSIC : AudioManager.USE_DEFAULT_STREAM_TYPE);
    }

    // Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set content view and toolbar
        MainBinding binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);

        // Store references of layout objects
        bmView = binding.gameView;
        bmView.textView = binding.textView;

        // Initialize ad banner. Test devices are older and current phones.
        List<String> testDeviceIds = List.of(
                "98DDF74ECDE599B008274ED3B5C5DCA5",
                "54A8240637407DBE6671033FDA2C7FCA",
                "B6B9CB212805EAF05227298CC384418C");
        RequestConfiguration configuration =
                new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
        MobileAds.setRequestConfiguration(configuration);
        AdView adView = binding.adView;
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        // Create music player (needed in preferences)
        Player.Listener listener = new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(LOG_TAG, "MusicPlayer error", error);
                musicPlayer.toggle(false);
                doDialog(DIALOG_MIDI);
            }
        };
        musicPlayer = new MusicPlayer(this, R.raw.nervous_cubase, true, listener);

        // Assign saved preference values to their variables.
        // Use default values from their declarations if available.
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        bmView.level = p.getInt(PREF_LEVEL, bmView.level);
        bmView.size = p.getInt(PREF_SIZE, bmView.size);
        bmView.background = p.getInt(PREF_BACKGROUND, bmView.background);
        bmView.setLives(p.getInt(PREF_LIVES, bmView.getLives()));
        bmView.setHapticFeedbackEnabled(p.getBoolean(PREF_HAPTICS, true));
        bmView.setSoundEffectsEnabled(p.getBoolean(PREF_SOUND, true));
        musicPlayer.isMusicEnabled = p.getBoolean(PREF_MUSIC, musicPlayer.isMusicEnabled);
        for (ColoredPart c : ColoredPart.values())
            c.color = p.getInt(PREF_COL + c.name(), c.defaultColor);
        Log.d(LOG_TAG, "Preferences loaded");

        // Set volume control
        setVolumeControlStream();

        // Show help dialog at very first execution
        if (p.getBoolean(PREF_FIRST, true))
            doDialog(DIALOG_HELP);
    }

    @Override
    protected void onStart() {
        super.onStart();
        musicPlayer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        musicPlayer.pause();

        // This is the recommended place to save persistent
        // settings (not onStop)
        SharedPreferences.Editor e = getPreferences(MODE_PRIVATE).edit();
        e.putInt(PREF_LEVEL, bmView.level);
        e.putInt(PREF_SIZE, bmView.size);
        e.putInt(PREF_BACKGROUND, bmView.background);
        e.putInt(PREF_LIVES, bmView.getLives());
        e.putBoolean(PREF_HAPTICS, bmView.isHapticFeedbackEnabled());
        e.putBoolean(PREF_SOUND, bmView.isSoundEffectsEnabled());
        e.putBoolean(PREF_MUSIC, musicPlayer.isMusicEnabled);
        for (ColoredPart c : ColoredPart.values())
            e.putInt(PREF_COL + c.name(), c.color);
        e.putBoolean(PREF_FIRST, false);
        e.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        musicPlayer.resume();
        setVolumeControlStream();
    }

    @Override
    protected void onStop() {
        super.onStop();
        musicPlayer.stop();
    }

    // Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menus, menu);
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_level:
                doDialog(DIALOG_LEVEL);
                break;

            case R.id.menu_size:
                doDialog(DIALOG_SIZE);
                break;

            case R.id.menu_lives:
                doDialog(DIALOG_LIVES);
                break;

            case R.id.menu_colors:
                doDialog(DIALOG_COLORS);
                break;

            case R.id.menu_background:
                doDialog(DIALOG_BACKGROUND);
                break;

            case R.id.menu_sound:
                doDialog(DIALOG_SOUND);
                break;

            case R.id.menu_about:
                doDialog(DIALOG_ABOUT);
                break;

            case R.id.menu_help:
                doDialog(DIALOG_HELP);
                break;

            case R.id.menu_quit:
                finish();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // Call the selected dialog
    private void doDialog(int id) {
        BlindManDialogFragment.newInstance(id).show(getSupportFragmentManager(), "dialog");
    }

    // This is the relevant dialog creation method. It is called through the dialog fragment.
    AppCompatDialog createDialog(int id) {
        // We use/need this to apply custom styles to the dialogs
        ContextThemeWrapper ctw = new ContextThemeWrapper(this, R.style.BlindmanDialogTheme);

        // First we treat the color picker
        if (id >= DIALOG_MASK_COLORS) {
            int icp = id - DIALOG_MASK_COLORS;
            String title = getResources().getStringArray(R.array.items_colors)[icp];
            ColoredPart cp = ColoredPart.values()[icp];

            // We can embed our nice color picker view into a regular dialog.
            // For that we use the provided factory method.
            return ColorPickerView.createDialog(ctw, title, cp.color, (dialog, which) -> {
                dialog.dismiss();
                cp.color = which;
                bmView.invalidate();
            });
        }

        // Now we treat all the cases which can easily be built as an AlertDialog.
        // For readability throughout the many cases we don't use chaining.
        AlertDialog.Builder b = new AlertDialog.Builder(ctw);

        switch (id) {
            case DIALOG_LEVEL:
                b.setTitle(R.string.menu_level);
                b.setSingleChoiceItems(R.array.items_level, bmView.level - 1, (dialog, which) -> {
                    dialog.dismiss();
                    bmView.newGame(which + 1);
                });
                break;

            case DIALOG_SIZE:
                b.setTitle(R.string.menu_size);
                b.setSingleChoiceItems(R.array.items_size, bmView.size - 1, (dialog, which) -> {
                    dialog.dismiss();
                    bmView.initField(which + 1);
                    bmView.newGame(0);
                });
                break;

            case DIALOG_LIVES:
                b.setTitle(R.string.menu_lives);
                // The ArrayAdapter does not produce the same layout as a direct call with R... or a bare array.
                // Therefore, a more general approach is used to create the list of choices.
                List<String> items = new ArrayList<>(BlindManView.ALLOWED_LIVES.size());
                for (int i : BlindManView.ALLOWED_LIVES)
                    items.add(i != 0 ? String.valueOf(i) : "∞");
                int currSel = BlindManView.ALLOWED_LIVES.indexOf(bmView.getLives());
                b.setSingleChoiceItems(items.toArray(new String[0]), currSel, (dialog, which) -> {
                    dialog.dismiss();
                    bmView.setLives(BlindManView.ALLOWED_LIVES.get(which));
                });
                break;

            case DIALOG_COLORS:
                b.setTitle(R.string.menu_colors);
                b.setItems(R.array.items_colors, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == ColoredPart.values().length) {
                        // Reset colors
                        ColoredPart.resetAll();
                        bmView.invalidate();
                    } else {
                        // Call color picker dialog
                        doDialog(which + DIALOG_MASK_COLORS);
                    }
                });
                break;

            case DIALOG_BACKGROUND:
                b.setTitle(R.string.menu_background);
                b.setSingleChoiceItems(R.array.items_background, bmView.background, (dialog, which) -> {
                    dialog.dismiss();
                    bmView.background = which;
                    bmView.invalidate();
                });
                break;

            case DIALOG_SOUND:
                b.setTitle(R.string.menu_sound);
                boolean[] effEnabled = new boolean[]{
                        bmView.isHapticFeedbackEnabled(),
                        bmView.isSoundEffectsEnabled(),
                        musicPlayer.isMusicEnabled};
                b.setMultiChoiceItems(R.array.items_effects, effEnabled, (dialog, which, isChecked) -> {
                    switch (which) {
                        case 0:
                            bmView.setHapticFeedbackEnabled(isChecked);
                            break;

                        case 1:
                            bmView.setSoundEffectsEnabled(isChecked);
                            setVolumeControlStream();
                            break;

                        case 2:
                            musicPlayer.toggle(isChecked);
                            setVolumeControlStream();
                            break;

                        default:
                            dialog.dismiss();
                    }
                });
                // A button press in an AlertDialog includes dismiss (!)
                b.setPositiveButton(android.R.string.ok, null);
                break;

            case DIALOG_MIDI:
                b.setTitle(R.string.title_midi);
                b.setMessage(R.string.text_midi);
                b.setPositiveButton(android.R.string.ok, null);
                break;

            case DIALOG_ABOUT:
                b.setTitle(R.string.menu_about);
                b.setMessage(R.string.text_about);
                b.setPositiveButton(android.R.string.ok, null);
                break;

            case DIALOG_HELP:
            default:
                b.setTitle(R.string.menu_help);
                b.setMessage(R.string.text_help);
                b.setPositiveButton(android.R.string.ok, null);
                break;
        }

        return b.create();
    }

    // Keys are treated here
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN)
            return false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_NUMPAD_6:
            case KeyEvent.KEYCODE_SOFT_RIGHT:
                bmView.makeMove(1, 0);
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_NUMPAD_4:
            case KeyEvent.KEYCODE_SOFT_LEFT:
                bmView.makeMove(-1, 0);
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_NUMPAD_8:
            case KeyEvent.KEYCODE_PAGE_UP:
                bmView.makeMove(0, -1);
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_NUMPAD_2:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                bmView.makeMove(0, 1);
                break;

            case KeyEvent.KEYCODE_BACK:
                super.getOnBackPressedDispatcher().onBackPressed();
                break;

            default:
                return super.onKeyDown(keyCode, event);
        }

        return true;
    }
}
