package com.f3401pal

import android.content.res.Resources
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.UiThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nekolaska.Builder.R

class CheckableTreeView<T : Checkable> : RecyclerView, CheckableTree<T> {
    val Int.px: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    private val adapter: TreeAdapter<T> by lazy {
        val indentation = indentation.px
        TreeAdapter<T>(indentation)
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, style: Int) : super(
        context,
        attributeSet,
        style
    )

    init {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        setAdapter(adapter)
    }

    @UiThread
    override fun setRoots(roots: List<TreeNode<T>>) = adapter.run {
        nodes.clear()
        roots.forEach { addNodeRecursive(it) }
        notifyDataSetChanged()
    }

    private fun TreeAdapter<T>.addNodeRecursive(node: TreeNode<T>) {
        nodes.add(node)
        if (node.isExpanded) {
            node.getChildren().forEach { addNodeRecursive(it) }
        }
    }

}

class TreeAdapter<T : Checkable>(private val indentation: Int) :
    RecyclerView.Adapter<TreeAdapter<T>.ViewHolder>() {

    internal val nodes: MutableList<TreeNode<T>> = mutableListOf()

    private val expandCollapseToggleHandler: (TreeNode<T>, ViewHolder) -> Unit =
        { node, viewHolder ->
            if (node.isExpanded) {
                collapse(viewHolder.bindingAdapterPosition)
            } else {
                expand(viewHolder.bindingAdapterPosition)
            }
            viewHolder.updateExpandIcon(node)
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return nodes[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_checkable_text, parent, false), indentation
        )
    }

    override fun getItemCount(): Int {
        return nodes.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(nodes[position])
    }

    @UiThread
    private fun expand(position: Int) {
        if (position >= 0) {
            // expand
            val node = nodes[position]
            val insertPosition = position + 1
            val insertedSize = node.getChildren().size
            nodes.addAll(insertPosition, node.getChildren())
            node.isExpanded = true
            notifyItemRangeInserted(insertPosition, insertedSize)
        }
    }

    @UiThread
    private fun collapse(position: Int) {
        // collapse
        if (position >= 0) {
            val node = nodes[position]
            var removeCount = 0
            fun removeChildrenFrom(cur: TreeNode<T>) {
                nodes.remove(cur)
                removeCount++
                if (cur.isExpanded) {
                    cur.getChildren().forEach { removeChildrenFrom(it) }
                    node.isExpanded = false
                }
            }
            node.getChildren().forEach { removeChildrenFrom(it) }
            node.isExpanded = false
            notifyItemRangeRemoved(position + 1, removeCount)
        }
    }

    inner class ViewHolder(view: View, private val indentation: Int) :
        RecyclerView.ViewHolder(view) {

        private val expandIndicator: ImageView = itemView.findViewById(R.id.expandIndicator)

        internal fun updateExpandIcon(node: TreeNode<T>) {
            if (node.isLeaf()) {
                // 叶子节点：隐藏箭头但保留占位，保持对齐
                expandIndicator.visibility = View.INVISIBLE
            } else {
                expandIndicator.visibility = View.VISIBLE
                expandIndicator.setImageResource(R.drawable.ic_chevron_right)
                // 展开时箭头朝下（90°），折叠时朝右（0°），带动画
                val targetRotation = if (node.isExpanded) 90f else 0f
                if (expandIndicator.rotation != targetRotation) {
                    expandIndicator.animate()
                        .rotation(targetRotation)
                        .setDuration(200)
                        .start()
                } else {
                    expandIndicator.rotation = targetRotation
                }
            }
        }

        internal fun bind(node: TreeNode<T>) {
            val indentationView = itemView.findViewById<View>(R.id.indentation)
            val checkText = itemView.findViewById<CheckBoxEx>(R.id.checkText)

            indentationView.minimumWidth = indentation * node.getLevel()

            checkText.text = node.getValue().toString()
            checkText.setOnCheckedChangeListener(null)
            val state = node.getCheckedStatus()
            checkText.isChecked = state.allChildrenChecked
            checkText.setIndeterminate(state.hasChildChecked)
            checkText.setOnCheckedChangeListener { _, isChecked ->
                node.setChecked(isChecked)
                notifyDataSetChanged()
            }

            updateExpandIcon(node)

            // 非叶子节点：点击箭头或整行都可以展开/折叠
            if (!node.isLeaf()) {
                val toggle = View.OnClickListener {
                    expandCollapseToggleHandler(node, this)
                }
                expandIndicator.setOnClickListener(toggle)
                itemView.setOnClickListener(toggle)
            } else {
                expandIndicator.setOnClickListener(null)
                // 叶子节点点击整行切换勾选
                itemView.setOnClickListener {
                    checkText.isChecked = !checkText.isChecked
                }
            }
        }

    }
}

