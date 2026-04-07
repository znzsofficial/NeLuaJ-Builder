package com.nekolaska.ktx.io

import okio.BufferedSink
import okio.buffer
import okio.sink
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.lib.jse.JsePlatform
import java.io.File

/**
 * 加载 Lua 文件为全局表。
 * 支持标准 Lua 赋值格式（app_name = "xxx"）和 return table 格式。
 * 兼容 UTF-8 BOM、不同换行符、尾部多余逗号等常见问题。
 */
private fun loadLua(path: String): LuaTable {
    val file = File(path)
    if (!file.exists() || !file.isFile) return LuaTable.tableOf()

    // 读取文件内容，去除 UTF-8 BOM
    var content = file.readText(Charsets.UTF_8).trimStart('\uFEFF')

    return runCatching {
        // 优先尝试标准方式执行
        val globals = JsePlatform.standardGlobals()
        val result = globals.load(content, file.name).call()
        // 如果脚本 return 了一个 table，直接使用
        if (result is LuaTable) result else globals as LuaTable
    }.recoverCatching {
        // 如果执行失败（语法错误等），尝试包裹为 return table 格式
        val globals = JsePlatform.standardGlobals()
        // 尝试将 key=value 格式包裹为 table
        val wrapped = "return {\n$content\n}"
        val result = globals.load(wrapped, file.name).call()
        if (result is LuaTable) result else LuaTable.tableOf()
    }.getOrDefault(LuaTable.tableOf())
}

fun File.getChild(name: String): File? = resolve(name).takeIf { it.exists() }
fun File.haveChild(name: String): Boolean = resolve(name).exists()

val File.isLua: Boolean
    get() = name.endsWith(".lua", ignoreCase = true)

fun File.loadLua() = loadLua(absolutePath)

fun File.useBufferedSink(block: (BufferedSink) -> Unit) = sink().buffer().use(block)
fun File.mkdirsIfNotExists() = apply { if (!exists()) mkdirs() }
