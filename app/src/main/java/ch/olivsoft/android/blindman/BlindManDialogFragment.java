package ch.olivsoft.android.blindman;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;

/**
 * Handles the dialogs for Honeycomb and up.
 * Implemented exactly according to documentation.
 * Added layout hack for centering later.
 *
 * @author Oliver Fritz, OlivSoft
 */
@SuppressWarnings("deprecation")
public class BlindManDialogFragment extends DialogFragment {
    private static final String ID = "id";
    private static final String CENTER = "center";

    private BlindManActivity blindManActivity;

    public static BlindManDialogFragment newInstance(int id, boolean center) {
        BlindManDialogFragment f = new BlindManDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ID, id);
        args.putBoolean(CENTER, center);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        blindManActivity = (BlindManActivity) context;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        Bundle args = getArguments();
        int id = args.getInt(ID);
        boolean center = args.getBoolean(CENTER);
        Dialog d = blindManActivity.createDialog(id);
        Window w = d.getWindow();
        // Layout hack. We would prefer this could be done via xml resources.
        if (w != null && center)
            w.setGravity(Gravity.CENTER);
        return d;
    }
}
