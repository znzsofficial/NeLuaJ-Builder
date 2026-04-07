package com.nekolaska.apk

import android.content.Context
import com.android.apksigner.ApkSignerTool
import com.android.apksigner.GenerateSignKey
import java.io.FileOutputStream

class MySigner(private val context: Context) {
    companion object {
        const val PREFS_NAME = "sign_config"
        const val DEFAULT_ALIAS = "Alias@neluaj"
        const val DEFAULT_KEY_PASS = "KeyPass@neluaj"
        const val DEFAULT_STORE_PASS = "StorePass@neluaj"
        const val DEFAULT_CN = "NeLuaJ"
        const val DEFAULT_ORG = "NeLuaJ"
        const val DEFAULT_COUNTRY = "CN"
        const val DEFAULT_VALIDITY_YEARS = 60
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyFile = context.filesDir.resolve("neluaj.jks")

    private val alias get() = prefs.getString("alias", DEFAULT_ALIAS)!!
    private val keyPass get() = prefs.getString("keyPass", DEFAULT_KEY_PASS)!!
    private val storePass get() = prefs.getString("storePass", DEFAULT_STORE_PASS)!!
    private val cn get() = prefs.getString("cn", DEFAULT_CN)!!
    private val org get() = prefs.getString("org", DEFAULT_ORG)!!
    private val country get() = prefs.getString("country", DEFAULT_COUNTRY)!!
    private val validityYears get() = prefs.getInt("validityYears", DEFAULT_VALIDITY_YEARS)

    private fun initKey() {
        val gsk = GenerateSignKey("JKS").apply {
            setAlias(alias)
            setKeyPass(keyPass)
            setStorePass(storePass)
            setSigAlg("SHA256WithRSA")
            setKeyAlg("RSA")
            setKeySize(2048)
            setValidityDay(30 * 12 * validityYears)
            setCommonName(cn)
            setOrganization(org)
            setCountry(country)
        }
        FileOutputStream(keyFile.absolutePath).use {
            it.write(gsk.generateKey())
        }
    }

    fun deleteKey() {
        if (keyFile.exists()) keyFile.delete()
    }

    fun start(
        input: String, output: String,
        v1: Boolean = true,
        v2: Boolean = true,
        v3: Boolean = true,
        v4: Boolean = false
    ) {
        if (!keyFile.exists()) initKey()
        ApkSignerTool.main(
            arrayOf(
                "sign", "--verbose",
                "--v1-signing-enabled", v1.toString(),
                "--v2-signing-enabled", v2.toString(),
                "--v3-signing-enabled", v3.toString(),
                "--v4-signing-enabled", v4.toString(),
                "--v1-signer-name", "XCERT",
                "--ks", keyFile.absolutePath,
                "--ks-pass", "pass:$storePass",
                "--key-pass", "pass:$keyPass",
                "--ks-key-alias", alias,
                "--ks-type", "jks",
                "--out", output,
                "--in", input
            )
        )
    }
}