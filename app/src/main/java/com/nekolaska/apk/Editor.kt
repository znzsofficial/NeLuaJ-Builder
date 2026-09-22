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
import java.io.IOException
import java.security.MessageDigest

fun interface LogCallback {
    operator fun invoke(msg: String)
}

class Editor(private val context: Context, workspace: File, val logCallback: LogCallback) {
    val cacheDir = workspace.resolve("decoded").mkdirsIfNotExists()

    /** 反编译缓存目录，用于跳过重复反编译 */
    private val decompileCache = BuildWorkspace.decompileCache(context)
    private val decompileCacheMarker = BuildWorkspace.decompileCacheMarker(context)

    /**
     * 检查反编译缓存是否可用。
     * 通过记录源 APK 的路径、大小和内容摘要来判断缓存是否有效。
     */
    private fun isDecompileCacheValid(
        sourceApkPath: String,
        apkSize: Long,
        apkHash: String,
        deDex: Boolean
    ): Boolean {
        if (!decompileCacheMarker.isFile ||
            !decompileCache.resolve("AndroidManifest.xml").isFile
        ) return false
        return runCatching {
            val lines = decompileCacheMarker.readLines()
            val cachedPath = lines[0]
            val cachedSize = lines[1].toLong()
            val cachedHash = lines[2]
            val cachedDeDex = lines[3].toBooleanStrict()
            sourceApkPath == cachedPath
                    && apkSize == cachedSize
                    && apkHash == cachedHash
                    && deDex == cachedDeDex
        }.getOrDefault(false)
    }

    private fun writeDecompileCacheMarker(
        sourceApkPath: String,
        apkSize: Long,
        apkHash: String,
        deDex: Boolean
    ) {
        decompileCacheMarker.writeText(
            "$sourceApkPath\n$apkSize\n$apkHash\n$deDex"
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val bytes = digest.digest()
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    fun decode(
        apkInputPath: String,
        sourceApkIdentity: String = apkInputPath,
        oldPackage: String,
        config: InitConfig,
        project: ProjectItem,
        compile: Boolean,
        keepIcon: Boolean,
        keepOpt: Boolean,
        keepWService: Boolean,
        keepAService: Boolean,
        keepLuaService: Boolean,
        keepNotificationService: Boolean,
        deDex: Boolean
    ) {
        val apkFile = File(apkInputPath)
        if (!apkFile.isFile) {
            throw IOException("Base APK does not exist: ${apkFile.absolutePath}")
        }
        val sourceApkPath = File(sourceApkIdentity).canonicalPath
        val apkSize = apkFile.length()
        val apkHash = sha256(apkFile)

        // 检查反编译缓存：如果基础 APK 没变且 deDex 选项一致，直接复用缓存
        if (isDecompileCacheValid(sourceApkPath, apkSize, apkHash, deDex)) {
            logCallback("Restoring from cache...")
            if (cacheDir.exists() && !cacheDir.deleteRecursively()) {
                throw IOException("Cannot clear decompile workspace: ${cacheDir.absolutePath}")
            }
            if (!decompileCache.copyRecursively(cacheDir, overwrite = true)) {
                throw IOException("Cannot restore decompile cache")
            }
        } else {
            logCallback("Decompiling base APK...")
            val args = mutableListOf("-i", apkInputPath, "-o", cacheDir.absolutePath, "-f")
            if (!deDex) args.add("-dex")
            Decompiler2.execute(logCallback, *args.toTypedArray())

            // 保存反编译结果到缓存
            logCallback("Caching decompiled result...")
            decompileCacheMarker.delete()
            if (decompileCache.exists() && !decompileCache.deleteRecursively()) {
                throw IOException("Cannot clear old decompile cache")
            }
            if (!cacheDir.copyRecursively(decompileCache, overwrite = true)) {
                decompileCache.deleteRecursively()
                throw IOException("Cannot save decompile cache")
            }
            writeDecompileCacheMarker(sourceApkPath, apkSize, apkHash, deDex)
        }

        logCallback("Modifying package name...")
        getJson {
            val json = source().buffer().readUtf8()
            val pattern = Regex("(\"package_name\"\\s*:\\s*\")((?:\\\\.|[^\"\\\\])*)(\")")
            val match = pattern.find(json)
                ?: throw IOException("package_name is missing from package.json")
            val replacement = match.groupValues[1] +
                    config.packageName.toJsonString() +
                    match.groupValues[3]
            val updated = json.replaceRange(match.range, replacement)
            updated.writeTo(this)
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
                keepLuaService,
                keepNotificationService,
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
        if (project.file.resolve("welcome.lua").isFile) {
            logCallback("Generating welcome screen...")
            WelcomeXml.apply(project.file, cacheDir.join("resources"))
        }
    }

    fun build(apkOutputPath: String) {
        logCallback("Building APK...")
        Builder.execute(logCallback, "-i", cacheDir.absolutePath, "-o", apkOutputPath, "-f")
    }

    private fun String.writeTo(file: File) {
        file.useBufferedSink { it.writeUtf8(this) }
    }

    private fun String.toJsonString() = buildString(length) {
        for (char in this@toJsonString) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
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
