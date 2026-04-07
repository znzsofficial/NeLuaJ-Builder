package com.nekolaska.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.DialogSignConfigBinding
import com.nekolaska.apk.MySigner
import com.nekolaska.utils.Toaster

class SignConfigDialog(context: Context, onChanged: () -> Unit) :
    MaterialAlertDialogBuilder(context) {

    private val binding = DialogSignConfigBinding.inflate(LayoutInflater.from(context))
    private val prefs = context.getSharedPreferences(MySigner.PREFS_NAME, Context.MODE_PRIVATE)

    init {
        setTitle(R.string.sign_config)
        setView(binding.root)

        // 加载已保存的配置
        binding.editAlias.setText(prefs.getString("alias", MySigner.DEFAULT_ALIAS))
        binding.editKeyPass.setText(prefs.getString("keyPass", MySigner.DEFAULT_KEY_PASS))
        binding.editStorePass.setText(prefs.getString("storePass", MySigner.DEFAULT_STORE_PASS))
        binding.editCn.setText(prefs.getString("cn", MySigner.DEFAULT_CN))
        binding.editOrg.setText(prefs.getString("org", MySigner.DEFAULT_ORG))
        binding.editCountry.setText(prefs.getString("country", MySigner.DEFAULT_COUNTRY))
        binding.editValidity.setText(prefs.getInt("validityYears", MySigner.DEFAULT_VALIDITY_YEARS).toString())

        binding.btnRegenerate.setOnClickListener {
            saveConfig()
            // 删除旧密钥，下次签名时会自动重新生成
            MySigner(context).deleteKey()
            Toaster.instance.show(context.getString(R.string.sign_key_regenerated))
            onChanged()
        }

        setPositiveButton(android.R.string.ok, null)
        setNegativeButton(android.R.string.cancel, null)

        val dialog = show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            saveConfig()
            Toaster.instance.show(context.getString(R.string.sign_saved))
            onChanged()
            dialog.dismiss()
        }
    }

    private fun saveConfig() {
        prefs.edit {
            putString("alias", binding.editAlias.text.toString().ifBlank { MySigner.DEFAULT_ALIAS })
            putString("keyPass", binding.editKeyPass.text.toString().ifBlank { MySigner.DEFAULT_KEY_PASS })
            putString("storePass", binding.editStorePass.text.toString().ifBlank { MySigner.DEFAULT_STORE_PASS })
            putString("cn", binding.editCn.text.toString().ifBlank { MySigner.DEFAULT_CN })
            putString("org", binding.editOrg.text.toString().ifBlank { MySigner.DEFAULT_ORG })
            putString("country", binding.editCountry.text.toString().ifBlank { MySigner.DEFAULT_COUNTRY })
            putInt("validityYears", binding.editValidity.text.toString().toIntOrNull() ?: MySigner.DEFAULT_VALIDITY_YEARS)
        }
    }
}
