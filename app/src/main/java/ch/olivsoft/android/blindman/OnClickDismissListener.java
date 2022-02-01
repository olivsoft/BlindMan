package ch.olivsoft.android.blindman;

import android.content.DialogInterface;

/**
 * Implements {@link DialogInterface.OnClickListener} dismissing the dialog and delegating
 * the callback to reduced-signature methods, which can be overridden instead of implementing
 * the original interfaces. Thereby, if no action apart from dismissing the dialog is required,
 * no overriding needs to be done.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class OnClickDismissListener implements DialogInterface.OnClickListener {

    // Override this method if desired
    public void onClick(int which) {
    }

    @Override
    public final void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
        this.onClick(which);
    }
}
