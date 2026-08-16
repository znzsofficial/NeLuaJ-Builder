package com.nekolaska.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.DialogConfigBinding
import com.nekolaska.data.InitConfig

@SuppressLint("SetTextI18n")
class ConfigDialog(context: Context, config: InitConfig, onOk: () -> Unit) :
    MaterialAlertDialogBuilder(context) {
    private val binding = DialogConfigBinding.inflate(LayoutInflater.from(context))
    private val default = config.copy()
    private val packageNamePattern =
        Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    private fun loadConfig(cfg: InitConfig) = binding.apply {
        layoutName.error = null
        layoutPackage.error = null
        layoutVersionName.error = null
        layoutVersionCode.error = null
        layoutTarget.error = null
        layoutMin.error = null
        editName.setText(cfg.appName)
        editPackage.setText(cfg.packageName)
        editVersionName.setText(cfg.versionName)
        editVersionCode.setText(cfg.versionCode.toString())
        editTarget.setText(cfg.targetSDK.toString())
        editMin.setText(cfg.minSDK.toString())
        editDebug.isChecked = cfg.debuggable
    }

    init {
        loadConfig(config)
        setTitle("Config")
        setView(binding.root)
        // 使用空按钮占位，在 show() 后重写点击逻辑以阻止自动关闭
        setPositiveButton(android.R.string.ok, null)
        setNegativeButton(android.R.string.cancel, null)
        setNeutralButton(R.string.reset, null)
        val dialog = show()
        // 重写按钮点击，Reset 不关闭对话框
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val appName = binding.editName.text.toString().trim()
            val packageName = binding.editPackage.text.toString().trim()
            val versionName = binding.editVersionName.text.toString().trim()
            val versionCode = binding.editVersionCode.text.toString().toIntOrNull()
            val targetSdk = binding.editTarget.text.toString().toIntOrNull()
            val minSdk = binding.editMin.text.toString().toIntOrNull()

            binding.layoutName.error = if (appName.isBlank() || appName.contains('/') || appName.contains('\\')) {
                context.getString(R.string.error_invalid_app_name)
            } else null
            binding.layoutPackage.error = if (packageNamePattern.matches(packageName)) null
            else context.getString(R.string.error_invalid_package)
            binding.layoutVersionName.error = if (versionName.isBlank()) context.getString(R.string.error_required) else null
            binding.layoutVersionCode.error = if (versionCode != null && versionCode > 0) null
            else context.getString(R.string.error_positive_number)
            binding.layoutTarget.error = if (targetSdk != null && targetSdk > 0) null
            else context.getString(R.string.error_positive_number)
            binding.layoutMin.error = when {
                minSdk == null || minSdk < 1 -> context.getString(R.string.error_positive_number)
                targetSdk != null && minSdk > targetSdk -> context.getString(R.string.error_sdk_order)
                else -> null
            }
            if (listOf(
                    binding.layoutName,
                    binding.layoutPackage,
                    binding.layoutVersionName,
                    binding.layoutVersionCode,
                    binding.layoutTarget,
                    binding.layoutMin
                ).any { it.error != null }
            ) return@setOnClickListener

            config.apply {
                this.appName = appName
                this.packageName = packageName
                this.versionName = versionName
                this.versionCode = versionCode!!
                targetSDK = targetSdk!!
                minSDK = minSdk!!
                debuggable = binding.editDebug.isChecked
            }
            onOk()
            dialog.dismiss()
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            loadConfig(default)
        }
    }
}
