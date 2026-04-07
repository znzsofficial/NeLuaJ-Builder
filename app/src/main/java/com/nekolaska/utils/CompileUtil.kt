package com.nekolaska.utils

import org.luaj.LuaClosure
import org.luaj.compiler.DumpState
import org.luaj.lib.jse.JsePlatform
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object CompileUtil {
    val mGlobals = JsePlatform.standardGlobals()

    private fun getByteArray(path: File): ByteArray {
        val closure = mGlobals.loadfile(path.absolutePath).checkfunction(1) as LuaClosure
        val stream = ByteArrayOutputStream()
        return try {
            DumpState.dump(closure.c, stream, true)
            stream.toByteArray()
        } catch (e: Exception) {
            throw e
        }
    }

    fun dump(input: File) {
        try {
            val output = File(input.absolutePath.replace(".lua", ".luac"))
            val fos = FileOutputStream(output)
            fos.write(getByteArray(input))
            fos.close()
            input.delete()
            output.renameTo(input)
        } catch (e: IOException) {
            throw e
        }
    }
}
