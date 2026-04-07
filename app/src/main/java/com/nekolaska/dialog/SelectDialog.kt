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
    init {
        val binding = DialogSelectBinding.inflate(LayoutInflater.from(context))

        val node = TreeNodeFactory.buildFileTree(file)
        node.isExpanded = true
        binding.tree.setRoots(listOf(node))

        var allSelected = false
        binding.btnSelectAll.setOnClickListener {
            allSelected = !allSelected
            node.setChecked(allSelected)
            binding.tree.setRoots(listOf(node)) // 刷新显示
            binding.btnSelectAll.setText(
                if (allSelected) R.string.deselect_all else R.string.select_all
            )
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.select_file))
            .setView(binding.root)
            .setCancelable(true)
            .setOnCancelListener { resume() }
            .create()

        binding.btnOk.setOnClickListener {
            onOk(node)
            dialog.dismiss()
            resume()
        }
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            resume()
        }

        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.75).toInt()
        )
    }
}