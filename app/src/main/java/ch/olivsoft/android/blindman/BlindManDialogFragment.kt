package ch.olivsoft.android.blindman

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf

/**
 * Handles the dialogs for Honeycomb and up.
 * Implemented exactly according to documentation.
 * Uses the calling activity context to create the dialog.
 *
 * @author Oliver Fritz, OlivSoft
 */
class BlindManDialogFragment : AppCompatDialogFragment() {

    private lateinit var blindManActivity: BlindManActivity

    companion object {
        private const val ID = "id"

        fun newInstance(id: Int): BlindManDialogFragment {
            return BlindManDialogFragment().apply {
                arguments = bundleOf(
                    ID to id
                )
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        blindManActivity = context as BlindManActivity
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AppCompatDialog {
        super.onCreateDialog(savedInstanceState)
        val id = arguments?.getInt(ID) ?: R.id.help
        return blindManActivity.createDialog(id)
    }
}
