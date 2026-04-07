package com.nekolaska.dialog

import android.content.Context
import android.view.LayoutInflater
import com.f3401pal.FileNode
import com.f3401pal.TreeNode
import com.f3401pal.TreeNodeFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nekolaska.Builder.databinding.DialogSelectBinding
import com.nekolaska.ktx.dialog.negativeButton
import com.nekolaska.ktx.dialog.positiveButton
import java.io.File
import com.nekolaska.Builder.R
import com.nekolaska.ktx.dialog.onCancel

class SelectDialog(
    context: Context,
    file: File,
    resume: () -> Unit,
    onOk: (TreeNode<FileNode>) -> Unit
) :
    MaterialAlertDialogBuilder(context) {

    private val binding = DialogSelectBinding.inflate(LayoutInflater.from(context))

    init {
        setTitle(context.getString(R.string.select_file))
        setView(binding.root)
        val node = TreeNodeFactory.buildFileTree(file)
        node.isExpanded = true
        binding.tree.setRoots(listOf(node))
        positiveButton(android.R.string.ok) {
            onOk(node)
            resume()
        }
        negativeButton(android.R.string.cancel) {
            resume()
        }
        onCancel {
            resume()
        }
        val dialog = show()
        // 让对话框尽可能大，方便浏览文件树
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.75).toInt()
        )
    }

}