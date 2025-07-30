package dev.jdtech.jellyfin.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import dev.jdtech.jellyfin.core.R as CoreR

class SearchDialogFragment(private val onSearch: (String?) -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(CoreR.layout.dialog_search, null)

        val searchEditText = view.findViewById<EditText>(CoreR.id.search_edit_text)

        // Handle keyboard search button
        searchEditText.setOnEditorActionListener { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (keyEvent != null && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                onSearch(searchEditText.text.toString())
                dismiss()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        builder.setView(view)
            .setTitle(CoreR.string.search)
            .setPositiveButton(CoreR.string.search) { _, _ ->
                onSearch(searchEditText.text.toString())
            }
            .setNegativeButton(CoreR.string.cancel) { dialog, _ ->
                dialog.cancel()
            }

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        searchEditText.requestFocus()

        return dialog
    }
}