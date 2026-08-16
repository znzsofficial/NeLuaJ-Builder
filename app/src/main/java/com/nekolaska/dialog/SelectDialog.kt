package com.nekolaska.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import com.f3401pal.FileNode
import com.f3401pal.TreeNode
import com.f3401pal.TreeNodeFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.DialogSelectBinding
import java.io.File

class SelectDialog(
    context: Context,
    file: File,
    resume: () -> Unit,
    onOk: (TreeNode<FileNode>) -> Unit
) {
    private var completed = false
    private val dialog: androidx.appcompat.app.AlertDialog
    private val onComplete = resume

    init {
        val binding = DialogSelectBinding.inflate(LayoutInflater.from(context))

        val node = TreeNodeFactory.buildFileTree(file)
        node.isExpanded = true
        binding.tree.setRoots(listOf(node))

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.select_file))
            .setView(binding.root)
            .setCancelable(true)
            .setOnCancelListener { complete() }
            .create()

        binding.btnOk.setOnClickListener {
            runCatching { onOk(node) }
                .onFailure { it.printStackTrace() }
            dialog.dismiss()
            complete()
        }
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            complete()
        }

        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.75).toInt()
        )
    }

    fun dismiss() {
        dialog.dismiss()
    }

    private fun complete() {
        if (completed) return
        completed = true
        onComplete()
    }
}
