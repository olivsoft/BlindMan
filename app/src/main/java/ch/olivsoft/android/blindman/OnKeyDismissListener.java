package ch.olivsoft.android.blindman;

import android.content.DialogInterface;
import android.view.KeyEvent;

/**
 * Implements {@link DialogInterface.OnKeyListener} dismissing the dialog and delegating
 * the callback to reduced-signature methods, which can be overridden instead of implementing
 * the original interfaces. Thereby, if no action apart from dismissing the dialog is required,
 * no overriding needs to be done.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class OnKeyDismissListener implements DialogInterface.OnKeyListener {

    // Override this method if desired
    @SuppressWarnings("UnusedParameters")
    public boolean onKey(int keyCode, KeyEvent event) {
        return true;
    }

    @Override
    public final boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (dialog != null)
            dialog.dismiss();
        return onKey(keyCode, event);
    }
}
