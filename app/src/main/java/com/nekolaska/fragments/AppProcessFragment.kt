package com.nekolaska.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.FragmentProcessBinding
import com.nekolaska.apk.BuildWorkspace
import com.nekolaska.apk.Editor
import com.nekolaska.apk.MySigner
import com.nekolaska.base.ProviderFragment
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.dialog.AppProcessDialog
import com.nekolaska.dialog.SelectDialog
import com.nekolaska.ktx.context.alertDialog
import com.nekolaska.ktx.dialog.positiveButton
import com.nekolaska.ktx.io.mkdirsIfNotExists
import com.nekolaska.ktx.io.replaceWith
import com.nekolaska.ktx.value.toFile
import com.nekolaska.ktx.view.onClick
import com.nekolaska.utils.Toaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

private val invalidApkNameChars = Regex("""[\\/:*?"<>|\u0000-\u001f\u007f]""")
private const val MAX_APK_BASENAME_BYTES = 180
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

internal fun createApkName(appName: String, versionName: String): String {
    val safeName = "${appName}_${versionName}"
        .replace(invalidApkNameChars, "_")
        .trim()
        .trim('.')
        .takeUtf8(MAX_APK_BASENAME_BYTES)
        .trimEnd('.')
        .ifBlank { "app" }
    return "$safeName.apk"
}

private fun String.takeUtf8(maxBytes: Int): String = buildString {
    var sourceIndex = 0
    var byteCount = 0
    while (sourceIndex < this@takeUtf8.length) {
        val codePoint = this@takeUtf8.codePointAt(sourceIndex)
        val text = String(Character.toChars(codePoint))
        val size = text.toByteArray(Charsets.UTF_8).size
        if (byteCount + size > maxBytes) break
        append(text)
        byteCount += size
        sourceIndex += Character.charCount(codePoint)
    }
}

private data class BuildOptions(
    val compileLua: Boolean,
    val deDex: Boolean,
    val keepIcon: Boolean,
    val keepOpt: Boolean,
    val keepAService: Boolean,
    val keepWService: Boolean,
    val keepLuaService: Boolean,
    val keepNotificationService: Boolean,
    val v1: Boolean,
    val v2: Boolean,
    val v3: Boolean,
    val v4: Boolean
)

private data class CopiedApk(
    val file: File,
    val sourceIdentity: String
)

class AppProcessFragment : ProviderFragment() {
    companion object {
        private const val ARG_PROJECT = "arg_project"
        private const val ARG_CONFIG = "arg_config"
        fun newInstance(project: ProjectItem, config: InitConfig) = AppProcessFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_PROJECT, project)
                putParcelable(ARG_CONFIG, config)
            }
        }
    }

    private val project: ProjectItem by lazy {
        @Suppress("DEPRECATION")
        requireArguments().getParcelable(ARG_PROJECT)!!
    }
    private val config: InitConfig by lazy {
        @Suppress("DEPRECATION")
        requireArguments().getParcelable(ARG_CONFIG)!!
    }
    private var _binding: FragmentProcessBinding? = null
    private val binding get() = _binding!!
    private var processDialog: AppProcessDialog? = null
    private var pendingInstallPath: String? = null
    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val path = pendingInstallPath
        pendingInstallPath = null
        if (path == null || !isAdded) return@registerForActivityResult
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            requireContext().packageManager.canRequestPackageInstalls()
        ) {
            launchInstaller(File(path))
        } else {
            Toaster.instance.show(getString(R.string.install_permission_required))
        }
    }
    private val baseApkPackage: String
        get() = "config".forPreference()
            ?.getString(
                "baseApkPackage",
                "github.znzsofficial.neluaj"
            )!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProcessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = "Build"
    }

    override fun onDestroyView() {
        processDialog?.dismiss()
        processDialog = null
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appContext = requireContext().applicationContext
        val packageManager = appContext.packageManager
        val selectedBasePackage = baseApkPackage
        val dialog = AppProcessDialog(requireContext())
        binding.startButton.onClick {
            binding.startButton.isEnabled = false
            processDialog = dialog
            dialog.show()
            var currentStep = "Copy base APK"
            val options = BuildOptions(
                compileLua = binding.compileSwitch.isChecked,
                deDex = binding.deDexSwitch.isChecked,
                keepIcon = binding.keepIcon.isChecked,
                keepOpt = binding.keepOpt.isChecked,
                keepAService = binding.keepAservice.isChecked,
                keepWService = binding.keepWservice.isChecked,
                keepLuaService = binding.keepLuaService.isChecked,
                keepNotificationService = binding.keepNotificationService.isChecked,
                v1 = binding.chipV1.isChecked,
                v2 = binding.chipV2.isChecked,
                v3 = binding.chipV3.isChecked,
                v4 = binding.chipV4.isChecked
            )
            viewLifecycleOwner.lifecycleScope.launch {
                var workspace: File? = null
                try {
                    BuildWorkspace.mutex.withLock {
                        withContext(Dispatchers.IO) {
                            workspace = BuildWorkspace.create(appContext)
                        }
                        val buildWorkspace = checkNotNull(workspace)
                        val apkEditor = Editor(appContext, buildWorkspace) {
                            dialog.setMessage(it)
                        }
                        val buildsDir =
                            Environment.getExternalStorageDirectory().resolve("LuaJ")
                                .resolve("Builds")
                        val apkName = createApkName(config.appName, config.versionName)
                        val inputDir = buildWorkspace.resolve("input").mkdirsIfNotExists()
                        val tempFile = buildWorkspace.resolve("unsigned.apk")
                        val resultFile = buildWorkspace.resolve("signed.apk")
                        val sidecar = buildWorkspace.resolve("signed.apk.idsig")
                        val smaliDir = apkEditor.cacheDir.resolve("smali")
                        withContext(Dispatchers.IO) {
                            buildsDir.mkdirsIfNotExists()
                            tempFile.delete()
                            resultFile.delete()
                            sidecar.delete()
                            val baseApk = copyBaseApk(
                                inputDir,
                                packageManager,
                                selectedBasePackage
                            )
                            try {
                                currentCoroutineContext().ensureActive()
                                currentStep = "Decode"
                                apkEditor.decode(
                                    apkInputPath = baseApk.file.path,
                                    sourceApkIdentity = baseApk.sourceIdentity,
                                    oldPackage = selectedBasePackage,
                                    config = config,
                                    project = project,
                                    compile = options.compileLua,
                                    keepIcon = options.keepIcon,
                                    keepOpt = options.keepOpt,
                                    keepAService = options.keepAService,
                                    keepWService = options.keepWService,
                                    keepLuaService = options.keepLuaService,
                                    keepNotificationService = options.keepNotificationService,
                                    deDex = options.deDex
                                )
                                currentCoroutineContext().ensureActive()
                                if (options.deDex && smaliDir.exists()) withContext(Dispatchers.Main) {
                                    suspendCancellableCoroutine { continuation ->
                                        var selectDialog: SelectDialog? = null
                                        val resume = {
                                            if (continuation.isActive) continuation.resume(Unit)
                                        }
                                        selectDialog = SelectDialog(
                                            requireContext(),
                                            smaliDir,
                                            resume
                                        ) {
                                            it.getAllCheckedItems().forEach { node ->
                                                node.file.delete()
                                            }
                                        }
                                        continuation.invokeOnCancellation {
                                            selectDialog.dismiss()
                                        }
                                    }
                                }

                                currentCoroutineContext().ensureActive()
                                currentStep = "Build"
                                apkEditor.build(tempFile.absolutePath)

                                currentCoroutineContext().ensureActive()
                                currentStep = "Sign"
                                apkEditor.logCallback("Signing APK...")
                                MySigner(appContext).start(
                                    tempFile.absolutePath,
                                    resultFile.absolutePath,
                                    v1 = options.v1,
                                    v2 = options.v2,
                                    v3 = options.v3,
                                    v4 = options.v4,
                                )
                                if (!resultFile.isFile) {
                                    throw IOException("Signing did not produce ${resultFile.absolutePath}")
                                }

                                currentCoroutineContext().ensureActive()
                                currentStep = "Export"
                                apkEditor.logCallback("Exporting APK...")
                                if (options.v4 && !sidecar.isFile) {
                                    throw IOException("V4 signing did not produce ${sidecar.absolutePath}")
                                }
                                exportArtifacts(
                                    resultFile,
                                    sidecar.takeIf { options.v4 },
                                    buildsDir.resolve(apkName),
                                    buildsDir.resolve("$apkName.idsig")
                                )

                                apkEditor.logCallback("Cleaning up...")
                            } finally {
                                tempFile.delete()
                                resultFile.delete()
                                sidecar.delete()
                                baseApk.file.delete()
                            }
                        }
                        if (!isAdded) return@withLock
                        requireContext().alertDialog(
                            R.string.build_success.strRes(),
                            R.string.save_to.strRes() + buildsDir.resolve(apkName)
                        ) {
                            setPositiveButton(R.string.install) { _, _ ->
                                installApk(buildsDir.resolve(apkName))
                            }
                            setNegativeButton(android.R.string.cancel, null)
                        }
                    }
                } catch (error: PackageManager.NameNotFoundException) {
                    if (!isAdded) return@launch
                    requireContext().alertDialog(
                        title = R.string.error_base_apk_not_found.strRes(),
                        message = R.string.error_base_apk_not_found_message.strRes() +
                                "\n" + error.message,
                        isCancellable = false
                    ) {
                        positiveButton(android.R.string.ok) {
                            activity?.supportFragmentManager?.apply {
                                if (backStackEntryCount > 0) popBackStack()
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val errorMsg = buildString {
                        appendLine("Failed at: $currentStep")
                        appendLine()
                        append(error.stackTraceToString())
                    }
                    if (isAdded) requireContext().alertDialog("Build Error", errorMsg) {
                        setPositiveButton(android.R.string.ok, null)
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        workspace?.deleteRecursively()
                    }
                    dialog.dismiss()
                    if (processDialog === dialog) processDialog = null
                    _binding?.startButton?.isEnabled = true
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        if (!apkFile.isFile) {
            Toaster.instance.show(getString(R.string.install_failed))
            return
        }
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallPath = apkFile.absolutePath
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching { unknownSourcesLauncher.launch(intent) }
                .onFailure {
                    pendingInstallPath = null
                    Toaster.instance.show(getString(R.string.install_failed))
                }
            return
        }
        launchInstaller(apkFile)
    }

    private fun launchInstaller(apkFile: File) {
        val context = requireContext()
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        }.getOrElse {
            Toaster.instance.show(getString(R.string.install_failed))
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toaster.instance.show(getString(R.string.install_failed))
        } catch (_: SecurityException) {
            Toaster.instance.show(getString(R.string.install_failed))
        }
    }


    private fun copyBaseApk(
        apkDir: File,
        packageManager: PackageManager,
        packageName: String
    ): CopiedApk {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        if (!applicationInfo.splitSourceDirs.isNullOrEmpty()) {
            throw IOException("Split base APKs are not supported: $packageName")
        }
        val sourceFile = applicationInfo.sourceDir?.toFile()
            ?: throw IOException("Base APK source is unavailable for $packageName")
        if (!sourceFile.isFile) {
            throw IOException("Base APK does not exist: ${sourceFile.absolutePath}")
        }
        val targetFile = apkDir.resolve("base.apk")
        val tempFile = apkDir.resolve("base.apk.tmp")
        try {
            sourceFile.copyTo(tempFile, overwrite = true)
            targetFile.replaceWith(tempFile)
        } finally {
            tempFile.delete()
        }
        return CopiedApk(targetFile, sourceFile.canonicalPath)
    }

    private fun exportArtifacts(
        apkSource: File,
        sidecarSource: File?,
        apkTarget: File,
        sidecarTarget: File
    ) {
        val token = System.nanoTime()
        val apkStage = apkTarget.resolveSibling(".${apkTarget.name}.$token.tmp")
        val sidecarStage = sidecarSource?.let {
            sidecarTarget.resolveSibling(".${sidecarTarget.name}.$token.tmp")
        }
        val apkBackup = apkTarget.resolveSibling(".${apkTarget.name}.$token.bak")
        val sidecarBackup = sidecarTarget.resolveSibling(".${sidecarTarget.name}.$token.bak")
        try {
            apkSource.copyTo(apkStage, overwrite = true)
            sidecarSource?.copyTo(sidecarStage!!, overwrite = true)

            var apkBackedUp = false
            var sidecarBackedUp = false
            var apkInstalled = false
            var sidecarInstalled = false
            try {
                if (apkTarget.exists()) {
                    if (!apkTarget.renameTo(apkBackup)) {
                        throw IOException("Cannot back up ${apkTarget.absolutePath}")
                    }
                    apkBackedUp = true
                }
                if (sidecarTarget.exists()) {
                    if (!sidecarTarget.renameTo(sidecarBackup)) {
                        throw IOException("Cannot back up ${sidecarTarget.absolutePath}")
                    }
                    sidecarBackedUp = true
                }
                if (sidecarStage != null) {
                    if (!sidecarStage.renameTo(sidecarTarget)) {
                        throw IOException("Cannot export ${sidecarTarget.absolutePath}")
                    }
                    sidecarInstalled = true
                }
                if (!apkStage.renameTo(apkTarget)) {
                    throw IOException("Cannot export ${apkTarget.absolutePath}")
                }
                apkInstalled = true
                apkBackup.delete()
                sidecarBackup.delete()
            } catch (error: Exception) {
                if (apkInstalled) apkTarget.delete()
                if (sidecarInstalled) sidecarTarget.delete()
                if (apkBackedUp && !apkBackup.renameTo(apkTarget)) {
                    error.addSuppressed(IOException("Cannot restore ${apkTarget.absolutePath}"))
                }
                if (sidecarBackedUp && !sidecarBackup.renameTo(sidecarTarget)) {
                    error.addSuppressed(IOException("Cannot restore ${sidecarTarget.absolutePath}"))
                }
                throw error
            }
        } finally {
            apkStage.delete()
            sidecarStage?.delete()
        }
    }

}
