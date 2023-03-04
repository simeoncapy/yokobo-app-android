package com.gvlab.yokobo.ui.config

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import com.gvlab.yokobo.R

class WifiPasswordDialogFragment : DialogFragment() {
    internal lateinit var listener: WifiPasswordDialogListener

    interface WifiPasswordDialogListener {
        fun onDialogPositiveClick(dialog: DialogFragment)
        fun onDialogNegativeClick(dialog: DialogFragment)
    }

    /*override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface

        try {
            // Instantiate the WifiPasswordDialogListener so we can send events to the host
            listener = context as WifiPasswordDialogListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException((context.toString() +
                    " must implement WifiPasswordDialogListener"))
        }
    }*/

    fun setListener(_listener : WifiPasswordDialogListener)
    {
        listener = _listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            // Get the layout inflater
            val inflater = requireActivity().layoutInflater;

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(inflater.inflate(R.layout.dialog_wifi_password, null))
                // Add action buttons
                .setPositiveButton(R.string.validation,
                    DialogInterface.OnClickListener { dialog, id ->
                        // sign in the user ...
                        Log.i("dialog", "ok")
                        listener.onDialogPositiveClick(this)
                    })
                .setNegativeButton(R.string.cancel,
                    DialogInterface.OnClickListener { dialog, id ->
                        //getDialog().cancel()
                        Log.i("dialog", "Cancel")
                        listener.onDialogNegativeClick(this)
                    })
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}