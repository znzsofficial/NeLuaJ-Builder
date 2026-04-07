package com.nekolaska.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.f3401pal.FileNode
import com.f3401pal.TreeNode
import com.f3401pal.TreeNodeFactory
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.DialogSelectBinding
import java.io.File

class SelectDialog(
    context: Context,
    file: File,
    resume: () -> Unit,
    onOk: (TreeNode<FileNode>) -> Unit
) {
    init {
        val binding = DialogSelectBinding.inflate(android.view.LayoutInflater.from(context))
        val dialog = Dialog(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
        dialog.setContentView(binding.root)
        dialog.setTitle(context.getString(R.string.select_file))
        dialog.setCancelable(true)
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.75).toInt()
            )
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        val node = TreeNodeFactory.buildFileTree(file)
        node.isExpanded = true
        binding.tree.setRoots(listOf(node))

        binding.btnOk.setOnClickListener {
            onOk(node)
            dialog.dismiss()
            resume()
        }
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            resume()
        }
        dialog.setOnCancelListener {
            resume()
        }
        dialog.show()
    }
}