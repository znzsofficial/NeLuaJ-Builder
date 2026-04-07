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

    private fun loadConfig(cfg: InitConfig) = binding.apply {
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
            config.apply {
                appName = binding.editName.text.toString()
                packageName = binding.editPackage.text.toString()
                versionName = binding.editVersionName.text.toString()
                versionCode = binding.editVersionCode.text.toString().toIntOrNull() ?: versionCode
                targetSDK = binding.editTarget.text.toString().toIntOrNull() ?: targetSDK
                minSDK = binding.editMin.text.toString().toIntOrNull() ?: minSDK
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