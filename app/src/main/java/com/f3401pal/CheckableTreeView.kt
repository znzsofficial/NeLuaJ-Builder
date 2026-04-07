package com.f3401pal

import android.content.res.Resources
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        nodes.addAll(roots)
        notifyDataSetChanged()
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
            viewHolder.itemView.findViewById<ExpandToggleButton>(R.id.expandIndicator)
                .startToggleAnimation(node.isExpanded)
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

        internal fun bind(node: TreeNode<T>) {
            val indentationView = itemView.findViewById<View>(R.id.indentation)
            val checkText = itemView.findViewById<CheckBoxEx>(R.id.checkText)
            val expandIndicator = itemView.findViewById<ExpandToggleButton>(R.id.expandIndicator)

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

            if (node.isLeaf()) {
                expandIndicator.visibility = View.GONE
            } else {
                expandIndicator.visibility = View.VISIBLE
                expandIndicator.setOnClickListener {
                    expandCollapseToggleHandler(
                        node,
                        this
                    )
                }
            }
        }

    }
}

