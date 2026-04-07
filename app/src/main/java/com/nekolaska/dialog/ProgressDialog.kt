package com.nekolaska.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color.TRANSPARENT
import com.nekolaska.Builder.databinding.DialogProgressBinding
import androidx.core.graphics.drawable.toDrawable

open class ProgressDialog(context: Context) : Dialog(context) {

    init {
        window?.setBackgroundDrawable(TRANSPARENT.toDrawable())
        DialogProgressBinding.inflate(layoutInflater).apply {
            setContentView(root)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }
    }

}