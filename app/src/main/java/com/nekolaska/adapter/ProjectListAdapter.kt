package com.nekolaska.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.ItemProjectBinding
import com.nekolaska.data.ProjectItem
import com.nekolaska.ktx.context.getDrawableCompat
import com.nekolaska.ktx.graphic.setColorFilterFromAttr
import com.nekolaska.ktx.view.onClick
import me.zhanghai.android.fastscroll.PopupTextProvider


class ProjectListAdapter(
    private val itemList: List<ProjectItem>,
    private val onItemClick: (ProjectItem) -> Unit
) :
    RecyclerView.Adapter<ProjectListAdapter.ViewHolder>(), PopupTextProvider {

    inner class ViewHolder(private val binding: ItemProjectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun unbind() = binding.icon.dispose()

        fun bind(item: ProjectItem) {
            binding.title.text = item.file.name
            binding.description.text = item.packageName
            itemView.onClick { onItemClick.invoke(item) }
            val defaultIcon = itemView.context.getDrawableCompat(R.drawable.ic_project)!!.apply {
                setColorFilterFromAttr(
                    itemView.context,
                    android.R.attr.colorPrimary
                )
            }
            item.iconPath?.let { binding.icon.load(it) } ?: binding.icon.setImageDrawable(
                defaultIcon
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemProjectBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int = itemList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(itemList[position])

    override fun getPopupText(view: View, position: Int) =
        itemList[position].file.name.first().toString()

    override fun onViewRecycled(holder: ViewHolder) = holder.unbind()
}