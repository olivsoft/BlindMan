package ch.olivsoft.android.blindman;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class BlindManActivity extends Activity implements OnErrorListener
{
    // Constants
    private static final String PREF_LEVEL = "PREF_LEVEL";
    private static final String PREF_SIZE = "PREF_SIZE";
    private static final String PREF_LIVES = "PREF_LIVES";
    private static final String PREF_HAPTICS = "PREF_HAPTICS";
    private static final String PREF_SOUND = "PREF_SOUND";
    private static final String PREF_MUSIC = "PREF_MUSIC";
    private static final String PREF_DRAG = "PREF_DRAG";
    private static final String PREF_COL = "PREF_COL_";
    private static final String PREF_BACKGROUND = "PREF_BACKGROUND";

    private static final int DIALOG_LEVEL = 1;
    private static final int DIALOG_SIZE = 2;
    private static final int DIALOG_LIVES = 3;
    private static final int DIALOG_COLORS = 4;
    private static final int DIALOG_BACKGROUND = 5;
    private static final int DIALOG_SETTINGS = 6;
    private static final int DIALOG_DRAG = 7;
    private static final int DIALOG_MIDI = 11;
    private static final int DIALOG_HELP = 21;
    private static final int DIALOG_ABOUT = 31;
    private static final int DIALOG_MASK_COLORS = 101;

    // Currently, the choice of music is available in debug mode only...
    private static final int NUM_SETTINGS = BuildConfig.DEBUG ? 3 : 2;

    // Tag for debug messages
    private static final String LOG_TAG = BlindManActivity.class.getSimpleName();

    // Variables and methods are only declared private if they are not accessed
    // by inner classes. This is done for efficiency (see Android documentation).
    BlindManView bmView;

    // Music and volume control
    MusicPlayer musicPlayer;

    void setVolumeControlStream()
    {
        setVolumeControlStream(musicPlayer.isMusicEnabled || bmView.isSoundEffectsEnabled()
                ? AudioManager.STREAM_MUSIC : AudioManager.USE_DEFAULT_STREAM_TYPE);
    }

    // Life cycle
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Set content view
        setContentView(R.layout.main);

        // Store references of layout objects
        bmView = (BlindManView) findViewById(R.id.blindman_view);
        bmView.textView = (TextView) findViewById(R.id.text_view);

        // Initialize ad banner
        ((AdView) findViewById(R.id.ad_view)).loadAd(new AdRequest.Builder()
                 .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                 .addTestDevice("123C1D971287214B908BEAE01E695885")
                 .addTestDevice("899B88182C1B0A44F6C9FE826FDDF1FE")
                 .addTestDevice("475799E0A9494751AE4153C279821A83")
                 .addTestDevice("B3539F1C6D561C077278F859328F71F6")
                 .addTestDevice("98DDF74ECDE599B008274ED3B5C5DCA5")
                 .addTestDevice("54A8240637407DBE6671033FDA2C7FCA").build());

        // Create the music player
        musicPlayer = new MusicPlayer(this, R.raw.nervous, true, this);

        // Assign saved preference values to their variables.
        // Use default values from their declarations if available.
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        bmView.level = p.getInt(PREF_LEVEL, bmView.level);
        bmView.size = p.getInt(PREF_SIZE, bmView.size);
        bmView.background = p.getInt(PREF_BACKGROUND, bmView.background);
        bmView.setLives(p.getInt(PREF_LIVES, bmView.getLives()));
        bmView.setDragDelay(p.getInt(PREF_DRAG, bmView.getDragDelay()));
        bmView.setHapticFeedbackEnabled(p.getBoolean(PREF_HAPTICS, true));
        bmView.setSoundEffectsEnabled(p.getBoolean(PREF_SOUND, true));
        musicPlayer.isMusicEnabled = p.getBoolean(PREF_MUSIC, musicPlayer.isMusicEnabled);
        for (ColoredPart c : ColoredPart.values())
            c.color = p.getInt(PREF_COL + c.name(), c.defaultColor);
        Log.d(LOG_TAG, "Preferences loaded");
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        musicPlayer.start();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        musicPlayer.pause();

        // This is the recommended place to save persistent
        // settings (not onStop)
        SharedPreferences.Editor e = getPreferences(MODE_PRIVATE).edit();
        e.putInt(PREF_LEVEL, bmView.level);
        e.putInt(PREF_SIZE, bmView.size);
        e.putInt(PREF_BACKGROUND, bmView.background);
        e.putInt(PREF_LIVES, bmView.getLives());
        e.putInt(PREF_DRAG, bmView.getDragDelay());
        e.putBoolean(PREF_HAPTICS, bmView.isHapticFeedbackEnabled());
        e.putBoolean(PREF_SOUND, bmView.isSoundEffectsEnabled());
        e.putBoolean(PREF_MUSIC, musicPlayer.isMusicEnabled);
        for (ColoredPart c : ColoredPart.values())
            e.putInt(PREF_COL + c.name(), c.color);
        e.apply();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        musicPlayer.resume();

        // Here it makes sense to adjust the volume control stream
        setVolumeControlStream();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        musicPlayer.stop();
    }

    // Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menus, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
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

            case R.id.menu_settings:
                doDialog(DIALOG_SETTINGS);
                break;

            case R.id.menu_drag:
                doDialog(DIALOG_DRAG);
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
    protected void doDialog(int id)
    {
        BlindManDialogFragment.newInstance(id).show(getFragmentManager(), "dialog");
    }

    // This is the relevant dialog creation method.
    Dialog createDialog(int id)
    {
        // First we check for masks
        if (id >= DIALOG_MASK_COLORS) {
            int cid = id - DIALOG_MASK_COLORS;
            final ColoredPart cp = ColoredPart.values()[cid];

            // We can embed our nice color picker view into a regular dialog.
            // For that we use the provided factory method.
            Dialog d = ColorPickerView.createDialog(this, cp.color, new OnActionDismissListener()
            {
                @Override
                public void onClick(int which)
                {
                    cp.color = which;
                    bmView.invalidate();
                }
            });
            d.setTitle(getResources().getStringArray(R.array.items_colors)[cid]);
            return d;
        }

        // Now we treat all the cases which can easily be built as an AlertDialog.
        // For readability throughout the many cases we don't use chaining.
        AlertDialog.Builder b = new AlertDialog.Builder(this);

        switch (id) {
            case DIALOG_LEVEL:
                b.setTitle(R.string.menu_level);
                b.setSingleChoiceItems(R.array.items_level, bmView.level - 1, new OnActionDismissListener()
                {
                    @Override
                    public void onClick(int which)
                    {
                        bmView.newGame(which + 1);
                    }
                });
                break;

            case DIALOG_SIZE:
                b.setTitle(R.string.menu_size);
                b.setSingleChoiceItems(R.array.items_size, bmView.size - 1, new OnActionDismissListener()
                {
                    @Override
                    public void onClick(int which)
                    {
                        bmView.initField(which + 1);
                        bmView.newGame(0);
                    }
                });
                break;

            case DIALOG_LIVES:
                b.setTitle(R.string.menu_lives);
                // We use an array adapter in order to be able to relate the selected
                // value to its list position. NOTE: For unclear reasons, passing this
                // adapter even with an additional text view layout id to the builder
                // does not give the same layout as passing the resource id directly.
                // So, we do use the resource id again for that purpose in the call
                // to setSingleChoiceItems.
                final ArrayAdapter<CharSequence> a =
                        ArrayAdapter.createFromResource(this, R.array.items_lives, android.R.layout.select_dialog_singlechoice);
                int liv = bmView.getLives();
                int pos = (liv == 0) ? a.getCount() - 1 : a.getPosition(Integer.toString(liv));
                b.setSingleChoiceItems(R.array.items_lives, pos, new OnActionDismissListener()
                {
                    @Override
                    public void onClick(int which)
                    {
                        bmView.setLives((which == a.getCount() - 1) ? 0 : Integer.parseInt(a.getItem(which).toString()));
                    }
                });
                break;

            case DIALOG_COLORS:
                b.setTitle(R.string.menu_colors);
                b.setItems(R.array.items_colors, new OnActionDismissListener()
                {
                    @Override
                    public void onClick(int which)
                    {
                        if (which == ColoredPart.values().length) {
                            // Reset colors
                            ColoredPart.resetAll();
                            bmView.invalidate();
                        } else {
                            // Call color picker dialog
                            doDialog(which + DIALOG_MASK_COLORS);
                        }
                    }
                });
                break;

            case DIALOG_BACKGROUND:
                b.setTitle(R.string.menu_background);
                b.setSingleChoiceItems(R.array.items_background, bmView.background, new OnActionDismissListener()
                {
                    @Override
                    public void onClick(int which)
                    {
                        bmView.background = which;
                        bmView.invalidate();
                    }
                });
                break;

            case DIALOG_SETTINGS:
                b.setTitle(R.string.menu_settings);
                // Special treatment for eventually using only a subset of the menu items
                String[] menuIts = new String[NUM_SETTINGS];
                boolean[] menuSts = new boolean[NUM_SETTINGS];
                System.arraycopy(
                        getResources().getStringArray(R.array.items_settings),
                        0, menuIts, 0, NUM_SETTINGS);
                System.arraycopy(
                        new boolean[]{bmView.isHapticFeedbackEnabled(), bmView.isSoundEffectsEnabled(), musicPlayer.isMusicEnabled},
                        0, menuSts, 0, NUM_SETTINGS);
                b.setMultiChoiceItems(menuIts, menuSts, new DialogInterface.OnMultiChoiceClickListener()
                {
                    public void onClick(DialogInterface dialog, int which, boolean isChecked)
                    {
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
                    }
                });
                b.setPositiveButton(android.R.string.ok, new OnActionDismissListener());
                break;

            case DIALOG_DRAG:
                b.setTitle(R.string.menu_drag);
                b.setSingleChoiceItems(DragDelay.getDelays(), bmView.getDragDelay(), new OnActionDismissListener()
                {
                    @Override
                    public void onClick(int which)
                    {
                        bmView.setDragDelay(which);
                    }
                });
                break;

            case DIALOG_MIDI:
                b.setTitle(R.string.title_midi);
                b.setMessage(R.string.text_midi);
                b.setOnKeyListener(new OnActionDismissListener());
                b.setPositiveButton(android.R.string.ok, new OnActionDismissListener());
                break;

            case DIALOG_ABOUT:
                b.setTitle(R.string.menu_about);
                b.setMessage(R.string.text_about);
                b.setOnKeyListener(new OnActionDismissListener());
                b.setPositiveButton(android.R.string.ok, new OnActionDismissListener());
                break;

            case DIALOG_HELP:
            default:
                b.setTitle(R.string.menu_help);
                b.setMessage(R.string.text_help);
                b.setOnKeyListener(new OnActionDismissListener());
                b.setPositiveButton(android.R.string.ok, new OnActionDismissListener());
                break;
        }

        return b.create();
    }

    // Keys are treated here
    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event)
    {
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
                super.onBackPressed();
                break;

            default:
                return super.onKeyDown(keyCode, event);
        }

        return true;
    }

    // MediaPlayer interface
    public boolean onError(MediaPlayer mp, int what, int extra)
    {
        doDialog(DIALOG_MIDI);
        return true;
    }
}
