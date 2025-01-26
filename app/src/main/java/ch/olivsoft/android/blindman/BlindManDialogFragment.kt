package ch.olivsoft.android.blindman

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.app.AppCompatDialogFragment

/**
 * Handles the dialogs for Honeycomb and up.
 * Implemented exactly according to documentation.
 *
 * @author Oliver Fritz, OlivSoft
 */
class BlindManDialogFragment : AppCompatDialogFragment() {

    private lateinit var blindManActivity: BlindManActivity

    companion object {
        private const val ID = "id"

        fun newInstance(id: Int): BlindManDialogFragment {
            val f = BlindManDialogFragment()
            val args = Bundle()
            args.putInt(ID, id)
            f.arguments = args
            return f
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        blindManActivity = context as BlindManActivity
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AppCompatDialog {
        super.onCreateDialog(savedInstanceState)
        val args = arguments
        val id = args?.getInt(ID) ?: 0
        return blindManActivity.createDialog(id)
    }
}
