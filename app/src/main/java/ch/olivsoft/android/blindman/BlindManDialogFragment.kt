package ch.olivsoft.android.blindman;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

/**
 * Handles the dialogs for Honeycomb and up.
 * Implemented exactly according to documentation.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class BlindManDialogFragment extends AppCompatDialogFragment {
    private static final String ID = "id";

    private BlindManActivity blindManActivity;

    public static BlindManDialogFragment newInstance(int id) {
        BlindManDialogFragment f = new BlindManDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ID, id);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        blindManActivity = (BlindManActivity) context;
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        Bundle args = getArguments();
        int id = args != null ? args.getInt(ID) : 0;
        return blindManActivity.createDialog(id);
    }
}
