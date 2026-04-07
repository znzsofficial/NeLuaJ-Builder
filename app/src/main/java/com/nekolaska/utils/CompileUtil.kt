package com.nekolaska.utils

import org.luaj.LuaClosure
import org.luaj.compiler.DumpState
import org.luaj.lib.jse.JsePlatform
import java.io.ByteArrayOutputStream
import java.io.File

object CompileUtil {
    private val globals by lazy { JsePlatform.standardGlobals() }

    private fun compile(file: File): ByteArray {
        val closure = globals.loadfile(file.absolutePath).checkfunction(1) as LuaClosure
        return ByteArrayOutputStream().use { stream ->
            DumpState.dump(closure.c, stream, true)
            stream.toByteArray()
        }
    }

    /**
     * 将 .lua 文件编译为字节码并原地替换。
     * 使用临时文件写入，成功后再替换原文件，避免编译失败时丢失源文件。
     */
    fun dump(input: File) {
        val bytecode = compile(input)
        val tempFile = File(input.parent, "${input.name}.tmp")
        try {
            tempFile.writeBytes(bytecode)
            input.delete()
            tempFile.renameTo(input)
        } catch (e: Exception) {
            tempFile.delete() // 清理临时文件
            throw e
        }
    }
}
