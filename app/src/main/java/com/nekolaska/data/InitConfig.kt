package com.nekolaska.data

import android.content.Context
import android.os.Parcelable
import com.nekolaska.Builder.R
import com.nekolaska.ktx.io.LuaConfig
import com.nekolaska.ktx.io.loadLuaOrThrow
import com.nekolaska.ktx.io.replaceWith
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class InitConfig(
    var appName: String,
    var packageName: String,
    var versionName: String,
    var versionCode: Int,
    var debuggable: Boolean,
    var targetSDK: Int,
    var minSDK: Int,
    var theme: String,
    var userPermission: List<String>
) : Parcelable {

    companion object {
        /**
         * 解析配置文件
         * @param context 上下文对象
         * @param file 配置文件
         * @return 解析后的配置对象
         */
        fun parse(context: Context, file: File): InitConfig {
            return parse(context, file.loadLuaOrThrow())
        }

        private fun parse(context: Context, config: LuaConfig): InitConfig {
            val unsupportedManagedKeys = config.unsupportedKeys.intersect(MANAGED_KEYS)
            require(unsupportedManagedKeys.isEmpty()) {
                "Managed config fields must use literal values: ${unsupportedManagedKeys.joinToString()}"
            }
            requireReadable(config, "app_name", arrayOf("app_name", "appname")) {
                string("app_name", "appname")
            }
            requireReadable(config, "package_name", arrayOf("package_name")) {
                string("package_name")
            }
            requireReadable(config, "ver_name", arrayOf("ver_name", "version_name")) {
                string("ver_name", "version_name")
            }
            requireReadable(config, "ver_code", arrayOf("ver_code", "version_code")) {
                int("ver_code", "version_code")
            }
            requireReadable(config, "debug_mode", arrayOf("debug_mode", "debugmode")) {
                boolean("debug_mode", "debugmode")
            }
            requireReadable(config, "target_sdk", arrayOf("target_sdk")) { int("target_sdk") }
            requireReadable(config, "min_sdk", arrayOf("min_sdk")) { int("min_sdk") }
            requireReadable(config, "NeLuaJ_Theme", arrayOf("NeLuaJ_Theme")) {
                string("NeLuaJ_Theme")
            }
            val theme = config.string("NeLuaJ_Theme")
                ?: config.string("theme")?.takeIf { it.startsWith("Theme_NeLuaJ_") }
                ?: "Theme_NeLuaJ_Compat"
            val notFound = context.getString(R.string.not_found)
            return InitConfig(
                config.string("app_name", "appname") ?: notFound,
                config.string("package_name") ?: notFound,
                config.string("ver_name", "version_name") ?: notFound,
                config.int("ver_code", "version_code") ?: 100,
                config.boolean("debug_mode", "debugmode") ?: false,
                config.int("target_sdk") ?: 29,
                config.int("min_sdk") ?: 21,
                theme,
                config.strictStringList("user_permission") ?: emptyList()
            )
        }

        private val MANAGED_KEYS = setOf(
            "app_name",
            "appname",
            "package_name",
            "ver_name",
            "version_name",
            "ver_code",
            "version_code",
            "debug_mode",
            "debugmode",
            "target_sdk",
            "min_sdk",
            "NeLuaJ_Theme",
            "user_permission"
        )

        private fun requireReadable(
            config: LuaConfig,
            name: String,
            keys: Array<String>,
            read: LuaConfig.() -> Any?
        ) {
            require(!config.hasValue(*keys) || config.read() != null) {
                "$name has an invalid literal value"
            }
        }

        private val PACKAGE_NAME =
            Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
        private val PERMISSION_NAME = Regex("[A-Z][A-Z0-9_]*")

    }

    fun validationErrors(): List<String> = buildList {
        if (appName.isBlank() || appName.contains('/') || appName.contains('\\')) {
            add("Application name is invalid")
        }
        if (!PACKAGE_NAME.matches(packageName)) add("Package name is invalid")
        if (versionName.isBlank()) add("Version name is empty")
        if (versionCode < 1) add("Version code must be positive")
        if (minSDK < 1) add("Minimum SDK must be positive")
        if (targetSDK < minSDK) add("Target SDK must not be lower than minimum SDK")
        if (userPermission.any { !PERMISSION_NAME.matches(it) }) {
            add("One or more permissions are invalid")
        }
    }

    fun dumpToFile(path: String) = runCatching {
        require(validationErrors().isEmpty())
        val targetFile = File(path)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val raw = if (targetFile.isFile) targetFile.readText(Charsets.UTF_8) else ""
        tempFile.writeText(InitConfigWriter.patch(raw, this), Charsets.UTF_8)
        targetFile.replaceWith(tempFile)
    }.onFailure {
        File(path).let { File(it.parentFile, "${it.name}.tmp") }.delete()
    }.isSuccess

}
