package com.f3401pal

import java.io.File
import android.os.SystemClock

object IdGenerator {

    private val base by lazy {
        SystemClock.currentThreadTimeMillis()
    }
    private var count = 0

    fun generate(): Long {
        return base + count--
    }
}

object TreeNodeFactory {

    /**
     * 从给定的根目录开始构建文件树。
     * 只会包含目录和其中的文件。
     *
     * @param rootDir 起始目录（或单个文件）。
     * @return 文件树的根 TreeNode。
     * @throws IllegalArgumentException 如果 rootDir 不存在。
     */
    fun buildFileTree(rootDir: File): TreeNode<FileNode> {
        if (!rootDir.exists()) {
            throw IllegalArgumentException("根文件/目录不存在: ${rootDir.absolutePath}")
        }
        return buildNodeRecursive(rootDir, null)
    }

    private fun buildNodeRecursive(currentFile: File, parentNode: TreeNode<FileNode>?): TreeNode<FileNode> {
        val fileNodeValue = FileNode(currentFile) // 将 File 包装成 FileNode

        val currentNode: TreeNode<FileNode> = if (parentNode == null) {
            // 如果没有父节点，说明是根节点
            TreeNode(fileNodeValue)
        } else {
            // 否则，创建子节点并关联父节点
            TreeNode(fileNodeValue, parentNode)
        }

        if (currentFile.isDirectory) {
            // 如果当前文件是目录
            val childrenFiles = currentFile.listFiles() ?: emptyArray() // listFiles 可能返回 null
            val childTreeNodes = mutableListOf<TreeNode<FileNode>>()

            // 对文件和目录进行排序，以便获得一致的顺序（可选，但有助于用户体验）
            // 优先目录，然后按名称排序
            childrenFiles.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            for (childFile in childrenFiles) {
                // 递归为每个子文件/目录构建节点
                childTreeNodes.add(buildNodeRecursive(childFile, currentNode))
            }
            currentNode.setChildren(childTreeNodes) // 设置当前节点的子节点列表
        }
        // 如果 currentFile 是一个文件（而不是目录），其 children 列表将保持为空，这使其成为叶子节点。
        return currentNode
    }
}

data class FileNode(val file: File) : Checkable(false) {
    override fun toString(): String = file.nameWithoutExtension
    val path get() = file.path
}

class TreeNode<T : Checkable>(
    private val value: T,
    private val parent: TreeNode<T>?,
    private var children: List<TreeNode<T>>,
    override var isExpanded: Boolean = false
) : HasId, Expandable {

    override val id: Long by lazy {
        IdGenerator.generate()
    }

    // 根节点构造函数
    constructor(value: T) : this(value, null, emptyList())

    // 叶子节点构造函数
    constructor(value: T, parent: TreeNode<T>) : this(value, parent, emptyList())

    // 父节点构造函数 (如果 children 已经有 parent, 可能导致 parent 不一致，使用需谨慎)
    // constructor(value: T, children: List<TreeNode<T>>) : this(value, null, children)

    fun isTop(): Boolean {
        return parent == null
    }

    fun isLeaf(): Boolean {
        return children.isEmpty()
    }

    fun getValue(): T {
        return value
    }

    fun getParent(): TreeNode<T>? { // 获取父节点
        return parent
    }

    fun getLevel(): Int {
        fun stepUp(node: TreeNode<T>): Int {
            return node.parent?.let { 1 + stepUp(it) } ?: 0
        }
        return stepUp(this)
    }

    fun setChildren(children: List<TreeNode<T>>) {
        this.children = children
    }

    fun getChildren(): List<TreeNode<T>> {
        return children
    }

    fun setChecked(isChecked: Boolean) {
        value.checked = isChecked
        // 级联操作到子节点
        children.forEach {
            it.setChecked(isChecked)
        }
    }

    /**
     * 获取子树的勾选状态。
     * - hasChildChecked: 如果此节点的任何后代（子节点的子节点等）被勾选，则为 true。
     * - allChildrenChecked: 如果此节点的所有后代（所有子节点及其所有后代）都被勾选，则为 true。
     * 注意：此状态不直接反映当前节点自身的 value.checked 状态。
     */
    fun getCheckedStatus(): NodeCheckedStatus {
        if (isLeaf()) return NodeCheckedStatus(value.checked, value.checked)

        var hasChildCheckedRecursive = false
        var allChildrenCheckedRecursive = true

        if (children.isEmpty()) { // 明确处理没有子节点的情况（尽管 isLeaf() 也处理）
            return NodeCheckedStatus(false, true) // 没有子项可查，可认为“所有子项都已查”(空集性质)，或 (false, false)
        }

        children.forEach { childNode ->
            val childStatus = childNode.getCheckedStatus() // 递归获取子节点的检查状态
            hasChildCheckedRecursive = hasChildCheckedRecursive || childNode.value.checked || childStatus.hasChildChecked
            // allChildrenCheckedRecursive 需要当前子节点被勾选，并且其所有子孙节点也都被勾选
            allChildrenCheckedRecursive = allChildrenCheckedRecursive && childNode.value.checked && childStatus.allChildrenChecked
        }
        return NodeCheckedStatus(hasChildCheckedRecursive, allChildrenCheckedRecursive)
    }

    /**
     * 获取聚合后的值。
     * 逻辑：
     * - 如果是叶子节点：若已勾选，则返回其值。
     * - 如果是非叶子节点：
     *   - 若其所有子孙节点都被勾选（根据 getCheckedStatus().allChildrenChecked 判断），则聚合该节点的值。
     *   - 否则，从其子节点递归收集聚合值。
     * 注意：这个聚合逻辑比较特殊，父节点是否被聚合取决于其所有子孙的勾选状态，而不是父节点自身的勾选状态。
     */
    fun getAggregatedValues(): List<T> {
        return if (isLeaf()) {
            if (value.checked) listOf(value) else emptyList()
        } else {
            // getCheckedStatus().allChildrenChecked 表示所有子孙节点都勾选了
            if (getCheckedStatus().allChildrenChecked && children.isNotEmpty()) { // 添加 children.isNotEmpty() 避免空目录被聚合
                listOf(value)
            } else {
                val result = mutableListOf<T>()
                children.forEach {
                    result.addAll(it.getAggregatedValues())
                }
                result
            }
        }
    }

    /**
     * 新增方法：获取当前节点及其所有子孙节点中被勾选的项的列表。
     *
     * @return 一个包含所有被勾选的 T 类型对象的列表。
     */
    fun getAllCheckedItems(): List<T> {
        val checkedItems = mutableListOf<T>()

        // 1. 检查当前节点自身是否被勾选
        if (this.value.checked) {
            checkedItems.add(this.value)
        }

        // 递归地从所有子节点收集被勾选的项
        checkedItems.addAll(this.children.flatMap { it.getAllCheckedItems() })

        return checkedItems
    }

    override fun toString(): String { // 便于调试
        return "TreeNode(id=$id, value=$value, children_count=${children.size}, expanded=$isExpanded, level=${getLevel()})"
    }
}