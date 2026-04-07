package com.nekolaska.dialog

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.internal.EdgeToEdgeUtils
import com.nekolaska.Builder.databinding.DialogPermissionBinding
import com.nekolaska.Builder.databinding.ItemPermissionBinding
import com.nekolaska.data.InitConfig
import com.nekolaska.data.PermissionItem
import com.nekolaska.ktx.context.showProgressDialogAndExecute
import com.nekolaska.ktx.view.fastScroller
import com.nekolaska.ktx.view.linearLayoutManager
import com.nekolaska.ktx.view.onClick
import com.nekolaska.utils.PermissionHelper
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.PopupTextProvider

@SuppressLint("RestrictedApi")
class PermissionDialog(
    context: FragmentActivity,
    config: InitConfig,
    onDismiss: () -> Unit
) : BottomSheetDialog(context) {
    private val binding: DialogPermissionBinding =
        DialogPermissionBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }
    lateinit var mList: MutableList<PermissionItem>

    init {
        dismissWithAnimation = true
        // 沉浸
        window?.let { EdgeToEdgeUtils.applyEdgeToEdge(it, true) }
        context.lifecycleScope.launch {
            context.showProgressDialogAndExecute {
                val configList = config.userPermission
                // 获取所有权限
                mList = PermissionHelper.instance.getPermissions()
                // 遍历所有权限，判断是否已选择
                mList.forEach { it.checked = configList.contains(it.key) }
            }
            binding.recyclerView.apply {
                linearLayoutManager()
                adapter = Adapter()
                // 快速滚动
                fastScroller().setPadding(0, 16, 4, 16).build()
            }
            setOnDismissListener {
                config.userPermission = mList.filter { it.checked }.map { it.key }.toList()
                onDismiss()
            }
            show()
        }
    }

    inner class ViewHolder(private val binding: ItemPermissionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PermissionItem) {
            binding.title.text = item.key
            binding.description.text = item.value
            binding.root.apply {
                isChecked = item.checked
                onClick {
                    isChecked = !isChecked
                    mList[bindingAdapterPosition].checked = isChecked
                }
                setOnLongClickListener {
                    callOnClick()
                    return@setOnLongClickListener true
                }
            }
        }
    }

    inner class Adapter :
        RecyclerView.Adapter<ViewHolder>(),
        PopupTextProvider {

        override fun onCreateViewHolder(parent: ViewGroup, p1: Int) =
            ViewHolder(
                ItemPermissionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun getItemCount() = mList.size
        override fun getPopupText(view: View, position: Int) =
            mList[position].key.first().toString()

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(mList[position])
        }

    }
}