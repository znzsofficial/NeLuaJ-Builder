package com.nekolaska.data

import android.content.Context
import android.os.Parcelable
import com.nekolaska.Builder.R
import com.nekolaska.ktx.collection.stringList
import com.nekolaska.ktx.io.loadLua
import com.nekolaska.ktx.io.useBufferedSink
import com.nekolaska.ktx.value.toFile
import com.nekolaska.ktx.value.ifIsTable
import com.nekolaska.ktx.value.toBool
import com.nekolaska.ktx.value.toIntOr
import com.nekolaska.ktx.value.toStringOr
import kotlinx.parcelize.Parcelize
import org.luaj.LuaTable
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
            return parse(context, file.loadLua())
        }

        private fun parse(context: Context, config: LuaTable): InitConfig {
            val notFound = context.getString(R.string.not_found)
            return InitConfig(
                config["app_name"] toStringOr notFound,
                config["package_name"] toStringOr notFound,
                config["ver_name"] toStringOr notFound,
                config["ver_code"] toIntOr 100,
                config["debug_mode"].toBool(),
                config["target_sdk"] toIntOr 29,
                config["min_sdk"] toIntOr 21,
                config["NeLuaJ_Theme"] toStringOr "Theme_NeLuaJ_Compat",
                config["user_permission"].ifIsTable()?.stringList()
                    ?: emptyList()
            )
        }

    }

    fun dumpToFile(path: String) = runCatching {
        val targetFile = path.toFile()
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        // 先写入临时文件，成功后再替换，避免写入中途崩溃导致数据丢失
        tempFile.useBufferedSink {
            it.writeUtf8("app_name = \"${appName}\"\n")
            it.writeUtf8("package_name = \"${packageName}\"\n")
            it.writeUtf8("ver_name = \"${versionName}\"\n")
            it.writeUtf8("ver_code = \"${versionCode}\"\n")
            it.writeUtf8("debug_mode = ${debuggable}\n")
            it.writeUtf8("target_sdk = \"${targetSDK}\"\n")
            it.writeUtf8("min_sdk = \"${minSDK}\"\n")
            it.writeUtf8("NeLuaJ_Theme = \"${theme}\"\n")
            it.writeUtf8("user_permission = {")
            userPermission.forEachIndexed { index, item ->
                it.writeUtf8("\"$item\"")
                if (index != userPermission.size - 1)
                    it.writeUtf8(",")
            }
            it.writeUtf8("}")
        }
        tempFile.renameTo(targetFile)
    }.isSuccess
}