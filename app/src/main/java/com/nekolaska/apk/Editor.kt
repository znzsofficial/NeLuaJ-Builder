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

    private class DecompileMarker(
        val path: String,
        val size: Long,
        val modified: Long?,
        val hash: String,
        val deDex: Boolean,
        val smali: String?
    )

    /**
     * 新 marker：路径、大小、修改时间、摘要、deDex、smali 开关。
     * 旧 marker 没有修改时间，摘要仍在第 3 行。
     */
    private fun readDecompileMarker(): DecompileMarker? {
        if (!decompileCacheMarker.isFile ||
            !decompileCache.resolve("AndroidManifest.xml").isFile
        ) return null
        val lines = runCatching { decompileCacheMarker.readLines() }.getOrNull() ?: return null
        if (lines.size < 4) return null
        val size = lines[1].toLongOrNull() ?: return null
        val third = lines[2]
        val legacy = third.length == 64 && third.all { it in '0'..'9' || it in 'a'..'f' }
        return runCatching {
            if (legacy) {
                DecompileMarker(
                    lines[0], size, null, third,
                    lines[3].toBooleanStrict(),
                    lines.getOrNull(4)
                )
            } else {
                if (lines.size < 5) return@runCatching null
                val modified = third.toLongOrNull() ?: return@runCatching null
                DecompileMarker(
                    lines[0], size, modified, lines[3],
                    lines[4].toBooleanStrict(),
                    lines.getOrNull(5)
                )
            }
        }.getOrNull()
    }

    private fun writeDecompileCacheMarker(
        sourceApkPath: String,
        apkSize: Long,
        apkModified: Long,
        apkHash: String,
        deDex: Boolean,
        skipSmaliComment: Boolean,
        skipDexDebug: Boolean
    ) {
        decompileCacheMarker.writeText(
            "$sourceApkPath\n$apkSize\n$apkModified\n$apkHash\n$deDex\n$skipSmaliComment,$skipDexDebug"
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
        deDex: Boolean,
        skipSmaliComment: Boolean,
        skipDexDebug: Boolean
    ) {
        val apkFile = File(apkInputPath)
        if (!apkFile.isFile) {
            throw IOException("Base APK does not exist: ${apkFile.absolutePath}")
        }
        val sourceApkPath = File(sourceApkIdentity).canonicalPath
        val apkSize = apkFile.length()
        val apkModified = apkFile.lastModified()
        val marker = readDecompileMarker()
        val sameFile = marker != null &&
                marker.path == sourceApkPath &&
                marker.size == apkSize
        val identityFast = sameFile && marker.modified == apkModified
        val apkHash = if (identityFast) marker.hash else sha256(apkFile)
        val cacheValid = sameFile &&
                marker.hash == apkHash &&
                marker.deDex == deDex &&
                (!deDex || marker.smali == "$skipSmaliComment,$skipDexDebug")

        if (cacheValid) {
            if (!identityFast) {
                writeDecompileCacheMarker(
                    sourceApkPath, apkSize, apkModified, apkHash,
                    deDex, skipSmaliComment, skipDexDebug
                )
            }
            logCallback("Restoring from cache...")
            stripAssetsExceptDexOpt(decompileCache)
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
            else {
                if (skipSmaliComment) {
                    args.add("-comment-level")
                    args.add("off")
                }
                if (skipDexDebug) args.add("-no-dex-debug")
            }
            // load-dex 0 强制逐个解码 dex，避免 SmaliDecompiler 一次性加载全部 dex 导致 OOM
            args.add("-load-dex")
            args.add("0")
            Decompiler2.execute(logCallback, *args.toTypedArray())

            stripAssetsExceptDexOpt(cacheDir)
            logCallback("Caching decompiled result...")
            saveDecompileCache()
            writeDecompileCacheMarker(
                sourceApkPath, apkSize, apkModified, apkHash,
                deDex, skipSmaliComment, skipDexDebug
            )
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

    private fun stripAssetsExceptDexOpt(decodedRoot: File) {
        val folder = decodedRoot.join("root", "assets")
        if (!folder.isDirectory) return
        folder.listFiles()?.forEach { file ->
            if (file.name != "dexopt" && !file.deleteRecursively()) {
                throw IOException("Cannot strip cached assets: ${file.absolutePath}")
            }
        }
    }

    private fun saveDecompileCache() {
        decompileCacheMarker.delete()
        if (decompileCache.exists() && !decompileCache.deleteRecursively()) {
            throw IOException("Cannot clear old decompile cache")
        }
        try {
            if (!cacheDir.copyRecursively(decompileCache, overwrite = true)) {
                throw IOException("Cannot save decompile cache")
            }
        } catch (error: IOException) {
            decompileCache.deleteRecursively()
            decompileCacheMarker.delete()
            throw error
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
