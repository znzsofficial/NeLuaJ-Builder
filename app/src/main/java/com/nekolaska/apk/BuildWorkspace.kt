package com.nekolaska.apk

import android.content.Context
import com.nekolaska.ktx.io.mkdirsIfNotExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Coordinates APKEditor state and owns all temporary build directories. */
internal object BuildWorkspace {
    val mutex = Mutex()

    fun create(context: Context): File {
        val root = context.cacheDir.resolve("apk_builds").mkdirsIfNotExists()
        val marker = File.createTempFile("build-", ".tmp", root)
        if (!marker.delete() || !marker.mkdir()) {
            marker.deleteRecursively()
            throw IOException("Cannot create build workspace in ${root.absolutePath}")
        }
        return marker
    }

    fun decompileCache(context: Context): File =
        context.cacheDir.resolve("apk_decompile_cache")

    fun decompileCacheMarker(context: Context): File =
        context.cacheDir.resolve("apk_decompile_cache.marker")

    suspend fun clearCaches(context: Context): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            listOf(
                context.cacheDir.resolve("apk"),
                context.cacheDir.resolve("apk_editor"),
                context.cacheDir.resolve("apk_builds"),
                decompileCache(context),
                decompileCacheMarker(context)
            ).all { !it.exists() || it.deleteRecursively() }
        }
    }
}
