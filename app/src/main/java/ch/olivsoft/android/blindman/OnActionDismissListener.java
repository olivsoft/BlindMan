package ch.olivsoft.android.blindman;

import android.content.DialogInterface;
import android.view.KeyEvent;

/**
 * Implements {@link DialogInterface.OnClickListener} and {@link DialogInterface.OnKeyListener}
 * dismissing the dialog and delegating the callback to reduced-signature methods, which can
 * be overridden instead of implementing the original interfaces. Thereby, if no action
 * apart from dismissing the dialog is required, no overriding needs to be done.
 *
 * @author Oliver Fritz, OlivSoft
 */
public class OnActionDismissListener
        implements DialogInterface.OnClickListener, DialogInterface.OnKeyListener
{
    // Override one of the following two methods depending on the required listener interface
    public void onClick(int which)
    {
    }

    public boolean onKey(int keyCode, KeyEvent event)
    {
        return true;
    }

    public final void onClick(DialogInterface dialog, int which)
    {
        dialog.dismiss();
        this.onClick(which);
    }

    public final boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event)
    {
        dialog.dismiss();
        return this.onKey(keyCode, event);
    }
}
