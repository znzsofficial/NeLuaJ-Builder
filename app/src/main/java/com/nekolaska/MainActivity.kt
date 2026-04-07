package com.nekolaska

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.ActivityMainBinding
import com.nekolaska.Builder.databinding.DialogSetBaseBinding
import com.nekolaska.fragments.NeedPermissionFragment
import com.nekolaska.fragments.ProjectListFragment
import com.nekolaska.ktx.context.alertDialog
import com.nekolaska.ktx.context.checkSelfPermissionGranted
import com.nekolaska.ktx.context.requestPermissionsCompat
import com.nekolaska.ktx.dialog.positiveButton
import com.nekolaska.ktx.fragment.transaction
import com.nekolaska.ktx.result.launch
import com.nekolaska.utils.PermissionHelper
import com.nekolaska.utils.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        const val STORAGE_PERMISSION_CODE: Int = 23
    }

    private lateinit var binding: ActivityMainBinding
    val storageActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Android 11 (R) 或更高版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
                initFragment(ProjectListFragment())
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE)
            if (grantResults.isNotEmpty()) {
                val write = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val read = grantResults[1] == PackageManager.PERMISSION_GRANTED
                if (read && write) {
                    initFragment(ProjectListFragment())
                }
            }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)
            setSupportActionBar(toolbar)
        }
        PermissionHelper.init(this)
        initFragment(
            if (havePermission()) ProjectListFragment()
            else NeedPermissionFragment()
        )
        onBackPressedDispatcher.addCallback(object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    // 如果没有 fragment 在堆栈中
                    finish()
                }
            }
        })
    }

    private fun initFragment(fragment: Fragment) = supportFragmentManager.transaction {
        replace(R.id.fragment_container, fragment)
    }

    fun setFragment(fragment: Fragment) = supportFragmentManager.transaction {
        replace(R.id.fragment_container, fragment)
        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        addToBackStack(null)
    }

    fun havePermission() =
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermissionGranted(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) && checkSelfPermissionGranted(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

    fun requestForStoragePermissions() {
        //Android is 11 (R) or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                storageActivityResultLauncher.launch {
                    action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    setData(Uri.fromParts("package", packageName, null))
                }
            }.onFailure {
                storageActivityResultLauncher.launch {
                    action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                }
            }
        } else {
            //Below android 11
            requestPermissionsCompat(
                STORAGE_PERMISSION_CODE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear -> alertDialog(getString(R.string.ask_clear_cache)) {
                positiveButton(android.R.string.ok) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (cacheDir.resolve("apk_editor").deleteRecursively()) withContext(
                            Dispatchers.Main
                        ) {
                            Toaster.instance.show(getString(R.string.clear_success))
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }

            R.id.action_set_base -> alertDialog(getString(R.string.ask_modify_base)) {
                val dialogBinding = DialogSetBaseBinding.inflate(layoutInflater)
                val preference = getSharedPreferences("config", 0)
                setView(dialogBinding.root)
                positiveButton(android.R.string.ok) {
                    val baseApkPackage = dialogBinding.etDialogSetBase.text.toString()
                    preference.edit {
                        if (baseApkPackage.isEmpty()) {
                            remove("baseApkPackage")
                        } else {
                            putString("baseApkPackage", baseApkPackage)
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
                dialogBinding.etDialogSetBase.setText(
                    preference.getString(
                        "baseApkPackage",
                        "github.znzsofficial.neluaj"
                    )
                )
            }

            R.id.action_sign_config -> {
                com.nekolaska.dialog.SignConfigDialog(this) {}
            }
        }
        return super.onOptionsItemSelected(item)
    }
}