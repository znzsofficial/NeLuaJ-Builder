package com.nekolaska.ktx.dialog

import androidx.appcompat.app.AlertDialog

inline fun AlertDialog.Builder.negativeButton(
    id: Int,
    crossinline onClick: (dialog: AlertDialog) -> Unit
): AlertDialog.Builder {
    return this.setNegativeButton(id) { dialog, _ ->
        onClick(dialog as AlertDialog)
    }
}

inline fun AlertDialog.Builder.positiveButton(
    id: Int,
    crossinline onClick: (dialog: AlertDialog) -> Unit
): AlertDialog.Builder {
    return this.setPositiveButton(id) { dialog, _ ->
        onClick(dialog as AlertDialog)
    }
}

inline fun AlertDialog.Builder.negativeButton(
    text: String,
    crossinline onClick: (dialog: AlertDialog) -> Unit
): AlertDialog.Builder {
    return this.setNegativeButton(text) { dialog, _ ->
        onClick(dialog as AlertDialog)
    }
}

inline fun AlertDialog.Builder.positiveButton(
    text: String,
    crossinline onClick: (dialog: AlertDialog) -> Unit
): AlertDialog.Builder {
    return this.setPositiveButton(text) { dialog, _ ->
        onClick(dialog as AlertDialog)
    }
}

inline fun AlertDialog.Builder.onCancel(
    crossinline onCancel: (dialog: AlertDialog) -> Unit
): AlertDialog.Builder {
    return this.setOnCancelListener {
        onCancel(it as AlertDialog)
    }
}