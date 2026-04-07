package com.nekolaska.utils

import android.Manifest
import android.content.Context
import android.content.pm.PermissionInfo
import com.nekolaska.data.PermissionItem

class PermissionHelper private constructor(context: Context) {
    companion object : SingletonHolder<PermissionHelper, Context>(::PermissionHelper)

    private val packageManager = context.packageManager

    // 获取所有的权限
    fun getPermissions(): MutableList<PermissionItem> {
        val ret = mutableListOf<PermissionItem>()
        val clazz = Manifest.permission::class.java
        clazz.declaredFields.forEach {
            val value = it.get(null) as String
            if (!value.isSystemPermission) {
                ret.add(PermissionItem(it.name, getName(value), false))
            }
        }
        ret.add(PermissionItem("MANAGE_EXTERNAL_STORAGE", "管理外部存储", false))
        return ret
    }

    fun getName(permission: String): String =
        runCatching {
            packageManager.getPermissionInfo(permission, 0).loadLabel(packageManager).toString()
        }.getOrDefault(permission) // 如果发生异常返回传入的权限名

    @Suppress("DEPRECATION")
    private val String.isSystemPermission: Boolean
        get() = runCatching {
            packageManager.getPermissionInfo(
                this,
                0
            ).protectionLevel and PermissionInfo.PROTECTION_SIGNATURE != 0
        }.getOrDefault(false)
}