package ch.olivsoft.android.blindman;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/**
 * Handles the dialogs for Honeycomb and up.
 * Implemented exactly according to documentation.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class BlindManDialogFragment extends DialogFragment {
    private static final String ID = "id";

    public static BlindManDialogFragment newInstance(int id) {
        BlindManDialogFragment f = new BlindManDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ID, id);
        f.setArguments(args);
        return f;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        BlindManActivity a = (BlindManActivity) getActivity();
        int id = getArguments().getInt(ID);
        return a.createDialog(id);
    }
}
