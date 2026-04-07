package com.nekolaska.ktx.io

import okio.BufferedSink
import okio.buffer
import okio.sink
import org.luaj.LuaTable
import org.luaj.lib.jse.JsePlatform
import java.io.File

private fun loadLua(path: String) = runCatching {
    JsePlatform.standardGlobals().also {
        it.loadfile(path).call()
    } as LuaTable
}.getOrDefault(LuaTable.tableOf())

fun File.getChild(name: String): File? = resolve(name).takeIf { it.exists() }
fun File.haveChild(name: String): Boolean = resolve(name).exists()

val File.isLua: Boolean
    get() = name.endsWith(".lua", ignoreCase = true)

fun File.loadLua() = loadLua(absolutePath)

fun File.useBufferedSink(block: (BufferedSink) -> Unit) = sink().buffer().use(block)
fun File.mkdirsIfNotExists() = apply { if (!exists()) mkdirs() }
