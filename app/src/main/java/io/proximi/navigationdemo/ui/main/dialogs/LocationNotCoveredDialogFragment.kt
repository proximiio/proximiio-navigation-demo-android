package io.proximi.navigationdemo.ui.main.dialogs


import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.DialogFragment
import io.proximi.navigationdemo.R
import kotlinx.android.synthetic.main.fragment_help_dialog.view.*

/**
 * [DialogFragment] to inform user he is currently not on premises.
 */
class LocationNotCoveredDialogFragment : DialogFragment() {

    lateinit var onAccept: (customDialogFragmentLocation: LocationNotCoveredDialogFragment) -> Unit

    companion object {
        @JvmStatic
        fun newInstance(
            onAccept: (customDialogFragmentLocation: LocationNotCoveredDialogFragment) -> Unit
        ) = LocationNotCoveredDialogFragment().apply {
            this.onAccept = onAccept
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_not_covered_dialog, container, false)
        dialog?.window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.setGravity(Gravity.CENTER)
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.requestFeature(Window.FEATURE_NO_TITLE)
        }
        view.acceptButton.setOnClickListener { onAccept.invoke(this) }
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.setGravity(Gravity.CENTER)
        }
    }

}
