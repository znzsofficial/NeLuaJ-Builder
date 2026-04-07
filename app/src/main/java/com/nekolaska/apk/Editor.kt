package com.nekolaska.apk

import android.content.Context
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.ktx.io.isLua
import com.nekolaska.ktx.io.mkdirsIfNotExists
import com.nekolaska.ktx.io.useBufferedSink
import com.nekolaska.utils.CompileUtil
import com.reandroid.apkeditor.compile.Builder
import com.reandroid.apkeditor.decompile.Decompiler2
import okio.buffer
import okio.source
import java.io.File

fun interface LogCallback {
    operator fun invoke(msg: String)
}

class Editor(context: Context, val logCallback: LogCallback) {
    val cacheDir = context.cacheDir.resolve("apk_editor").mkdirsIfNotExists()

    /** 反编译缓存目录，用于跳过重复反编译 */
    private val decompileCache = context.cacheDir.resolve("apk_decompile_cache")

    /**
     * 检查反编译缓存是否可用。
     * 通过记录源 APK 的大小和最后修改时间来判断缓存是否有效。
     */
    private fun isDecompileCacheValid(apkFile: File, deDex: Boolean): Boolean {
        val marker = decompileCache.resolve(".cache_marker")
        if (!marker.exists() || !decompileCache.resolve("AndroidManifest.xml").exists()) return false
        return runCatching {
            val lines = marker.readLines()
            val cachedSize = lines[0].toLong()
            val cachedModified = lines[1].toLong()
            val cachedDeDex = lines[2].toBoolean()
            apkFile.length() == cachedSize
                    && apkFile.lastModified() == cachedModified
                    && deDex == cachedDeDex
        }.getOrDefault(false)
    }

    private fun writeDecompileCacheMarker(apkFile: File, deDex: Boolean) {
        decompileCache.resolve(".cache_marker").writeText(
            "${apkFile.length()}\n${apkFile.lastModified()}\n$deDex"
        )
    }

    fun decode(
        apkInputPath: String,
        oldPackage: String,
        config: InitConfig,
        project: ProjectItem,
        compile: Boolean,
        keepIcon: Boolean,
        keepOpt: Boolean,
        keepWService: Boolean,
        keepAService: Boolean,
        deDex: Boolean
    ) {
        val apkFile = File(apkInputPath)

        // 检查反编译缓存：如果基础 APK 没变且 deDex 选项一致，直接复用缓存
        if (isDecompileCacheValid(apkFile, deDex)) {
            logCallback("Restoring from cache...")
            decompileCache.copyRecursively(cacheDir, overwrite = true)
        } else {
            logCallback("Decompiling base APK...")
            val args = mutableListOf("-i", apkInputPath, "-o", cacheDir.absolutePath, "-f")
            if (!deDex) args.add("-dex")
            Decompiler2.execute(logCallback, *args.toTypedArray())

            // 保存反编译结果到缓存
            logCallback("Caching decompiled result...")
            if (decompileCache.exists()) decompileCache.deleteRecursively()
            cacheDir.copyRecursively(decompileCache, overwrite = true)
            writeDecompileCacheMarker(apkFile, deDex)
        }

        logCallback("Modifying package name...")
        getJson {
            val json = source().buffer().readUtf8()
            val pattern = "\"package_name\": \"([^\"]*)\""
            json.replace(pattern.toRegex(), "\"package_name\": \"${config.packageName}\"")
                .writeTo(this)
        }

        logCallback("Modifying app name...")
        val replaced = getStrings {
            replaceAppName(this, config.appName)
        }

        logCallback("Modifying manifest...")
        getManifest {
            ManifestReplacer2.process(
                source().buffer().readUtf8(),
                config.userPermission,
                config.versionName,
                config.versionCode,
                config.targetSDK,
                config.minSDK,
                config.debuggable,
                keepWService,
                keepAService,
                config.appName,
                replaced,
                config.packageName,
                oldPackage
            ).writeTo(this)
        }

        logCallback("Clearing assets...")
        clearAssets(exceptDexOpt = keepOpt)

        logCallback("Copying project assets...")
        copyAssets(project.file, skipIcon = !keepIcon)

        if (compile) compileAllLua(logCallback)

        logCallback("Replacing icon...")
        replaceIcon(project.file.resolve("icon.png"))
    }

    fun build(apkOutputPath: String) {
        logCallback("Building APK...")
        Builder.execute(logCallback, "-i", cacheDir.absolutePath, "-o", apkOutputPath, "-f")
    }

    private fun String.writeTo(file: File) {
        file.useBufferedSink { it.writeUtf8(this) }
    }

    private fun File.join(vararg pathSegments: String): File {
        val path = pathSegments.joinToString(File.separator)
        return File(this, path)
    }

    private fun getJson(block: File.() -> Unit) =
        cacheDir.join("resources", "package_1", "package.json").block()

    private fun getManifest(block: File.() -> Unit) =
        cacheDir.resolve("AndroidManifest.xml").block()

    private fun getStrings(block: File.() -> Boolean): Boolean =
        cacheDir.join("resources", "package_1", "res", "values", "strings.xml").block()

    private fun clearAssets(exceptDexOpt: Boolean) {
        val folder = cacheDir.join("root", "assets")
        if (!folder.isDirectory) return
        folder.listFiles()?.forEach { file ->
            if (!exceptDexOpt || file.name != "dexopt") {
                file.deleteRecursively()
            }
        }
    }

    private fun copyAssetsInner(sourceDir: File, targetDir: File, skipIcon: Boolean) {
        if (!sourceDir.isDirectory) return
        targetDir.mkdirsIfNotExists()
        val iconFile = sourceDir.resolve("icon.png")
        sourceDir.listFiles()?.forEach { file ->
            val targetPath = targetDir.resolve(file.name)
            if (file.isDirectory) {
                copyAssetsInner(file, targetPath, false)
            } else if (!(skipIcon && file.absolutePath == iconFile.absolutePath)) {
                file.copyTo(targetPath, overwrite = true)
            }
        }
    }

    private fun copyAssets(assets: File, skipIcon: Boolean) {
        copyAssetsInner(assets, cacheDir.join("root", "assets"), skipIcon)
    }

    private fun compileAllLua(logCallback: LogCallback) {
        traverseAndCompile(cacheDir.join("root", "assets"), logCallback)
    }

    private fun traverseAndCompile(dir: File, logCallback: LogCallback) {
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                traverseAndCompile(file, logCallback)
            } else if (file.isLua) {
                logCallback("Compiling ${file.name}")
                CompileUtil.dump(file)
            }
        }
    }

    private fun replaceIcon(iconFile: File) {
        if (iconFile.exists()) {
            val target = cacheDir.join("resources", "package_1", "res", "drawable", "icon.png")
            iconFile.copyTo(target, overwrite = true)
        }
    }
}