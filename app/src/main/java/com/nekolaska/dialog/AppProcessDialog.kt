package com.nekolaska.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import androidx.core.view.isVisible
import com.nekolaska.Builder.databinding.DialogProcessBinding
import androidx.core.graphics.drawable.toDrawable

class AppProcessDialog(context: Context) : Dialog(context) {
    val binding = DialogProcessBinding.inflate(layoutInflater).apply {
        setContentView(root)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    init {
        window?.apply {
            setBackgroundDrawable(TRANSPARENT.toDrawable())
            attributes?.width = (context.resources.displayMetrics.widthPixels * 0.8f).toInt()
        }
        binding.dialogText.isVisible = true
    }

    fun setMessage(message: String) {
        binding.dialogText.post {
            binding.dialogText.text = message
        }
    }
}