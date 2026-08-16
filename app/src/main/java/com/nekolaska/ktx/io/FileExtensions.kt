package com.nekolaska.ktx.io

import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

fun File.getChild(name: String): File? = resolve(name).takeIf { it.exists() }
fun File.haveChild(name: String): Boolean = resolve(name).exists()

val File.isLua: Boolean
    get() = name.endsWith(".lua", ignoreCase = true)

internal fun File.loadLuaOrThrow(): LuaConfig = LuaConfigParser.parse(this)

fun File.useBufferedSink(block: (BufferedSink) -> Unit) = sink().buffer().use(block)
fun File.mkdirsIfNotExists() = apply {
    if (isDirectory) return@apply
    if (exists() || !mkdirs()) {
        throw IOException("Cannot create directory: $absolutePath")
    }
}

fun File.replaceWith(tempFile: File) {
    if (!tempFile.isFile) {
        throw IOException("Replacement file does not exist: ${tempFile.absolutePath}")
    }
    if (tempFile.renameTo(this)) return
    if (!exists()) {
        throw IOException("Cannot move ${tempFile.absolutePath} to $absolutePath")
    }

    val backup = resolveSibling(".$name.${System.nanoTime()}.bak")
    if (!renameTo(backup)) {
        throw IOException("Cannot back up $absolutePath")
    }
    try {
        if (!tempFile.renameTo(this)) {
            throw IOException("Cannot move ${tempFile.absolutePath} to $absolutePath")
        }
    } catch (error: Exception) {
        if (!exists() && !backup.renameTo(this)) {
            error.addSuppressed(IOException("Cannot restore $absolutePath from ${backup.absolutePath}"))
        }
        throw error
    }
    backup.delete()
}

private fun File.resolveSibling(name: String) = parentFile?.resolve(name) ?: File(name)
