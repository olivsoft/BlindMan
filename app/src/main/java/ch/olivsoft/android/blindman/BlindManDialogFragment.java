package ch.olivsoft.android.blindman;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

/**
 * Handles the dialogs for Honeycomb and up.
 * Implemented exactly according to documentation.
 * Added layout hack for centering later.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class BlindManDialogFragment extends DialogFragment {
    private static final String ID = "id";
    private static final String CENTER = "center";

    public static BlindManDialogFragment newInstance(int id, boolean center) {
        BlindManDialogFragment f = new BlindManDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ID, id);
        args.putBoolean(CENTER, center);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        BlindManActivity a = (BlindManActivity) getActivity();
        Bundle args = getArguments();
        @SuppressWarnings("all")
        int id = args.getInt(ID);
        boolean center = args.getBoolean(CENTER);
        @SuppressWarnings("all")
        Dialog d = a.createDialog(id);
        Window w = d.getWindow();
        // Layout hack. We would prefer this could be done via xml resources.
        if (w != null && center)
            w.setGravity(Gravity.CENTER);
        return d;
    }
}
