package io.proximi.navigationdemo.ui.main.dialogs


import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import io.proximi.navigationdemo.R

import kotlinx.android.synthetic.main.fragment_permission_dialog.view.*

/**
 * Generic [DialogFragment] that is used as template for several different dialogs. See:
 * - [getLimitedFunctionalityLocationPermissionInstance]
 * - [getLimitedFunctionalityBluetoothInstance]
 * - [getAccessibilitySettingsInstance]
 */
class DialogFragmentFactory : DialogFragment() {

    var drawableId: Int = 0
    var titleId: Int = 0
    var descriptionId: Int = 0
    var acceptButtonId: Int = 0
    var declineButtonId: Int = 0
    lateinit var onAccept: (permissionDialogFragment: DialogFragmentFactory) -> Unit
    lateinit var onDecline: (permissionDialogFragment: DialogFragmentFactory) -> Unit

    companion object {
        @JvmStatic
        fun newInstance(
            drawableId: Int,
            descriptionId: Int,
            acceptButtonTextId: Int,
            declineButtonTextId: Int,
            onAccept: (permissionDialogFragment: DialogFragmentFactory) -> Unit,
            onDecline: (permissionDialogFragment: DialogFragmentFactory) -> Unit
        ) = DialogFragmentFactory().apply {
            this.drawableId = drawableId
            this.titleId = titleId
            this.descriptionId = descriptionId
            this.acceptButtonId = acceptButtonTextId
            this.declineButtonId = declineButtonTextId
            this.onAccept = onAccept
            this.onDecline = onDecline
        }

        fun getLimitedFunctionalityLocationPermissionInstance(onAccept: (permissionDialogFragment: DialogFragmentFactory) -> Unit, onDecline: (permissionDialogFragment: DialogFragmentFactory) -> Unit) =
            newInstance(
                R.drawable.ic_limited_functionality,
                R.string.dialog_limited_functionality_location_description,
                R.string.dialog_accept,
                R.string.dialog_decline,
                onAccept,
                onDecline
            )

        fun getLimitedFunctionalityBluetoothInstance(onAccept: (permissionDialogFragment: DialogFragmentFactory) -> Unit, onDecline: (permissionDialogFragment: DialogFragmentFactory) -> Unit) =
            newInstance(
                R.drawable.ic_dialog_bluetooth,
                R.string.dialog_limited_functionality_bluetooth_description,
                R.string.dialog_accept,
                R.string.dialog_decline,
                onAccept,
                onDecline
            )

        fun getAccessibilitySettingsInstance(onAccept: (permissionDialogFragment: DialogFragmentFactory) -> Unit, onDecline: (permissionDialogFragment: DialogFragmentFactory) -> Unit) =
            newInstance(
                R.drawable.ic_accesibility_settings,
                R.string.dialog_accessibility_description,
                R.string.dialog_accept,
                R.string.dialog_decline,
                onAccept,
                onDecline
            )

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_permission_dialog, container, false)
        view.titleView.setImageResource(drawableId)
        view.descriptionTextView.setText(descriptionId)
        view.acceptButton.setText(acceptButtonId)
        view.declineButton.setText(declineButtonId)
        dialog?.window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.setGravity(Gravity.CENTER)
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.requestFeature(Window.FEATURE_NO_TITLE)
        }
        view.acceptButton.setOnClickListener { onAccept.invoke(this) }
        view.declineButton.setOnClickListener { onDecline.invoke(this) }
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
