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

    private data class SigningConfig(
        val alias: String,
        val keyPass: String,
        val storePass: String,
        val commonName: String,
        val organization: String,
        val country: String,
        val validityYears: Int
    )

    private val binding = DialogSignConfigBinding.inflate(LayoutInflater.from(context))
    private val prefs = context.getSharedPreferences(MySigner.PREFS_NAME, Context.MODE_PRIVATE)
    private var savedConfig = readSavedConfig()

    init {
        setTitle(R.string.sign_config)
        setView(binding.root)

        // 加载已保存的配置
        binding.editAlias.setText(savedConfig.alias)
        binding.editKeyPass.setText(savedConfig.keyPass)
        binding.editStorePass.setText(savedConfig.storePass)
        binding.editCn.setText(savedConfig.commonName)
        binding.editOrg.setText(savedConfig.organization)
        binding.editCountry.setText(savedConfig.country)
        binding.editValidity.setText(savedConfig.validityYears.toString())

        binding.btnRegenerate.setOnClickListener {
            saveConfig()
            MySigner(context).deleteKey()
            Toaster.instance.show(context.getString(R.string.sign_key_regenerated))
            onChanged()
        }

        setPositiveButton(android.R.string.ok, null)
        setNegativeButton(android.R.string.cancel, null)

        val dialog = show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (saveConfig()) MySigner(context).deleteKey()
            Toaster.instance.show(context.getString(R.string.sign_saved))
            onChanged()
            dialog.dismiss()
        }
    }

    private fun readSavedConfig() = SigningConfig(
        alias = prefs.getString("alias", MySigner.DEFAULT_ALIAS)!!,
        keyPass = prefs.getString("keyPass", MySigner.DEFAULT_KEY_PASS)!!,
        storePass = prefs.getString("storePass", MySigner.DEFAULT_STORE_PASS)!!,
        commonName = prefs.getString("cn", MySigner.DEFAULT_CN)!!,
        organization = prefs.getString("org", MySigner.DEFAULT_ORG)!!,
        country = prefs.getString("country", MySigner.DEFAULT_COUNTRY)!!,
        validityYears = prefs.getInt("validityYears", MySigner.DEFAULT_VALIDITY_YEARS)
    )

    private fun readFormConfig() = SigningConfig(
        alias = binding.editAlias.text.toString().ifBlank { MySigner.DEFAULT_ALIAS },
        keyPass = binding.editKeyPass.text.toString().ifBlank { MySigner.DEFAULT_KEY_PASS },
        storePass = binding.editStorePass.text.toString().ifBlank { MySigner.DEFAULT_STORE_PASS },
        commonName = binding.editCn.text.toString().ifBlank { MySigner.DEFAULT_CN },
        organization = binding.editOrg.text.toString().ifBlank { MySigner.DEFAULT_ORG },
        country = binding.editCountry.text.toString().ifBlank { MySigner.DEFAULT_COUNTRY },
        validityYears = binding.editValidity.text.toString().toIntOrNull()
            ?: MySigner.DEFAULT_VALIDITY_YEARS
    )

    private fun saveConfig(): Boolean {
        val config = readFormConfig()
        val changed = config != savedConfig
        prefs.edit {
            putString("alias", config.alias)
            putString("keyPass", config.keyPass)
            putString("storePass", config.storePass)
            putString("cn", config.commonName)
            putString("org", config.organization)
            putString("country", config.country)
            putInt("validityYears", config.validityYears)
        }
        savedConfig = config
        return changed
    }
}
