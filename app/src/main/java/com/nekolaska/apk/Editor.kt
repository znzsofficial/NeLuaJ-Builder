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
        if (!deDex) Decompiler2.execute(
            logCallback,
            "-i",
            apkInputPath,
            "-o",
            cacheDir.absolutePath,
            "-f",
            "-dex"
        )
        else Decompiler2.execute(
            logCallback,
            "-i",
            apkInputPath,
            "-o",
            cacheDir.absolutePath,
            "-f"
        )
        logCallback("modify package name")
        getJson {
            val json = source().buffer().readUtf8()
            val pattern = "\"package_name\": \"([^\"]*)\""
            json.replace(pattern.toRegex(), "\"package_name\": \"${config.packageName}\"")
                .writeTo(this)
        }
        logCallback("modify name")
        val replaced = getStrings {
            replaceAppName(this, config.appName)
        }
        logCallback("modify manifest")
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
        logCallback("clear assets")
        clearAssets(exceptDexOpt = keepOpt)
        logCallback("copy assets")
        copyAssets(project.file, skipIcon = !keepIcon)
        if (compile) compileAllLua(logCallback)
        logCallback("replace icon")
        replaceIcon(project.file.resolve("icon.png"))
    }

    fun build(apkOutputPath: String) {
        Builder.execute(logCallback, "-i", cacheDir.absolutePath, "-o", apkOutputPath, "-f")
    }

    fun String.writeTo(file: File) = runCatching {
        file.useBufferedSink {
            it.writeUtf8(this)
        }
    }

    fun File.join(vararg pathSegments: String): File {
        val path = pathSegments.joinToString(File.separator)
        return File(this, path)
    }

    fun getJson(block: File.() -> Unit) =
        cacheDir.join("resources", "package_1", "package.json").block()

    fun getManifest(block: File.() -> Unit) = cacheDir.resolve("AndroidManifest.xml").block()

    fun getStrings(block: File.() -> Boolean): Boolean =
        cacheDir.join("resources", "package_1", "res", "values", "strings.xml").block()

    fun clearAssets(exceptDexOpt: Boolean) {
        val folder = cacheDir.join("root", "assets")
        if (exceptDexOpt) {
            if (folder.exists() && folder.isDirectory) {
                // 遍历文件夹中的所有文件和子文件夹
                folder.listFiles()?.forEach { file ->
                    // 排除名为 dexopt 的文件夹
                    if (file.name != "dexopt") {
                        // 删除文件或非 dexopt 文件夹
                        file.deleteRecursively()
                    }
                }
            }
        } else {
            if (folder.exists() && folder.isDirectory) {
                // 遍历文件夹中的所有文件和子文件夹
                folder.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            }
        }
    }

    private fun copyAssetsInner(sourceDir: File, targetDir: File, skipIcon: Boolean) {
        val iconFile = sourceDir.resolve("icon.png")
        if (sourceDir.exists() && sourceDir.isDirectory) {
            // 创建目标目录
            targetDir.mkdirsIfNotExists()
            sourceDir.listFiles()?.forEach { file ->
                val targetPath = targetDir.resolve(file.name)

                if (file.isDirectory) {
                    // 递归调用以复制子目录
                    copyAssetsInner(file, targetPath, false)
                } else {
                    // 跳过 icon 文件
                    if (skipIcon && file.absolutePath == iconFile.absolutePath) return@forEach
                    // 复制文件
                    file.copyTo(targetPath, overwrite = true)
                }
            }
        }
    }

    fun copyAssets(assets: File, skipIcon: Boolean) {
        val targetDir = cacheDir.join("root", "assets")
        copyAssetsInner(assets, targetDir, skipIcon)
    }

    fun compileAllLua(logCallback: LogCallback) {
        val rootDir = cacheDir.join("root", "assets")
        traverseAndCompile(rootDir, logCallback)
    }

    fun traverseAndCompile(dir: File, logCallback: LogCallback) {
        if (dir.exists() && dir.isDirectory) {
            // 遍历当前目录下的所有文件和文件夹
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // 如果是文件夹，递归调用
                    traverseAndCompile(file, logCallback)
                } else if (file.isLua) {
                    // 如果是 Lua 文件，进行编译
                    logCallback("compile ${file.name}")
                    CompileUtil.dump(file)
                }
            }
        }
    }

    fun replaceIcon(iconFile: File) {
        if (iconFile.exists()) {
            val iconTarget =cacheDir.join("resources", "package_1", "res", "drawable", "icon.png")
            iconFile.copyTo(iconTarget, overwrite = true)
        }
    }
}