package com.nekolaska

import android.Manifest
import android.content.Intent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.ActivityMainBinding
import com.nekolaska.Builder.databinding.DialogSetBaseBinding
import com.nekolaska.apk.BuildWorkspace
import com.nekolaska.apk.MySigner
import com.nekolaska.fragments.NeedPermissionFragment
import com.nekolaska.fragments.ProjectListFragment
import com.nekolaska.fragments.ProjectDetailFragment
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.ktx.io.getChild
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
import java.io.IOException

class MainActivity : AppCompatActivity() {
    companion object {
        const val STORAGE_PERMISSION_CODE: Int = 23
        const val ACTION_OPEN_PROJECT = "com.nekolaska.Builder.action.OPEN_PROJECT"
        const val EXTRA_PROJECT_PATH = "com.nekolaska.Builder.extra.PROJECT_PATH"
        private const val STATE_PENDING_PROJECT = "pending_project_path"
    }

    private lateinit var binding: ActivityMainBinding
    private var pendingProjectPath: String? = null
    private var projectOpenInProgress = false
    val storageActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Android 11 (R) 或更高版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                if (!isFinishing && !supportFragmentManager.isStateSaved) {
                    initFragment(ProjectListFragment())
                }
                openPendingProject()
            }
        }
    private val exportSigningKeyLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val exported = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                        MySigner(this@MainActivity).exportKey(output)
                    } ?: throw IOException("Cannot open the selected document")
                }.isSuccess
            }
            Toaster.instance.show(
                getString(
                    if (exported) R.string.sign_export_success
                    else R.string.sign_export_failed
                )
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            if (!isFinishing && !supportFragmentManager.isStateSaved) {
                initFragment(ProjectListFragment())
            }
            openPendingProject()
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
        pendingProjectPath = savedInstanceState?.getString(STATE_PENDING_PROJECT)
        if (savedInstanceState == null) {
            initFragment(if (havePermission()) ProjectListFragment() else NeedPermissionFragment())
            handleProjectIntent(intent)
        }
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

    private fun initFragment(fragment: Fragment) {
        if (isFinishing || supportFragmentManager.isStateSaved) return
        supportFragmentManager.transaction {
            replace(R.id.fragment_container, fragment)
        }
    }

    fun setFragment(fragment: Fragment) {
        if (isFinishing || supportFragmentManager.isStateSaved) return
        supportFragmentManager.transaction {
            replace(R.id.fragment_container, fragment)
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            addToBackStack(null)
        }
    }

    fun havePermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
                    action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
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
                        val cleared = BuildWorkspace.clearCaches(this@MainActivity)
                        if (cleared) withContext(Dispatchers.Main) {
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

            R.id.action_export_signing_key -> exportSigningKey()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun exportSigningKey() {
        lifecycleScope.launch {
            val keyReady = withContext(Dispatchers.IO) {
                runCatching { MySigner(this@MainActivity).ensureKey() }.isSuccess
            }
            if (!keyReady) {
                Toaster.instance.show(getString(R.string.sign_export_failed))
                return@launch
            }
            runCatching {
                exportSigningKeyLauncher.launch(MySigner.DEFAULT_EXPORT_FILE_NAME)
            }.onFailure {
                Toaster.instance.show(getString(R.string.sign_export_failed))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleProjectIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (havePermission() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            openPendingProject()
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        outState.putString(STATE_PENDING_PROJECT, pendingProjectPath)
        super.onSaveInstanceState(outState)
    }

    private fun openPendingProject() {
        if (projectOpenInProgress || supportFragmentManager.isStateSaved) return
        pendingProjectPath?.let { path ->
            projectOpenInProgress = true
            window.decorView.post {
                lifecycleScope.launch {
                    try {
                        if (openProject(path) && pendingProjectPath == path) {
                            pendingProjectPath = null
                        }
                    } finally {
                        projectOpenInProgress = false
                        if (pendingProjectPath != null) openPendingProject()
                    }
                }
            }
        }
    }

    private fun handleProjectIntent(intent: Intent) {
        if (intent.action != ACTION_OPEN_PROJECT) return
        val path = intent.getStringExtra(EXTRA_PROJECT_PATH) ?: return
        intent.action = null
        intent.removeExtra(EXTRA_PROJECT_PATH)
        if (havePermission()) {
            pendingProjectPath = path
            openPendingProject()
        } else {
            pendingProjectPath = path
        }
    }

    private suspend fun openProject(path: String): Boolean {
        val projectDir = withContext(Dispatchers.IO) {
            runCatching { java.io.File(path).canonicalFile }.getOrNull()
        }
        if (projectDir == null || !projectDir.isDirectory) {
            Toaster.instance.show(getString(R.string.error_project_not_found))
            return true
        }
        val projectRoot = withContext(Dispatchers.IO) {
            Environment.getExternalStorageDirectory()
                .resolve("LuaJ")
                .resolve("Projects")
                .canonicalFile
        }
        if (!projectDir.toPath().startsWith(projectRoot.toPath())) {
            Toaster.instance.show(getString(R.string.error_project_not_found))
            return true
        }
        val initFile = projectDir.getChild("init.lua")
        if (initFile == null || !initFile.isFile) {
            Toaster.instance.show(getString(R.string.error_no_init_lua))
            return true
        }
        val project = try {
            withContext(Dispatchers.IO) {
                ProjectItem(
                    projectDir,
                    projectDir.getChild("icon.png")?.absolutePath,
                    InitConfig.parse(this@MainActivity, initFile).packageName
                )
            }
        } catch (_: Exception) {
            Toaster.instance.show(getString(R.string.error_invalid_config))
            return true
        }
        if (pendingProjectPath != path) return true
        if (isFinishing || supportFragmentManager.isStateSaved) return false
        setFragment(ProjectDetailFragment.newInstance(project))
        return true
    }
}
