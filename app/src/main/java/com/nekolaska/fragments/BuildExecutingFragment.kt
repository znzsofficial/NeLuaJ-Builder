package com.nekolaska.fragments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import coil3.load
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.FragmentBuildExecutingBinding
import com.nekolaska.apk.BuildWorkspace
import com.nekolaska.apk.Editor
import com.nekolaska.apk.MySigner
import com.nekolaska.base.ProviderFragment
import com.nekolaska.data.BuildOptions
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.dialog.SelectDialog
import com.nekolaska.ktx.io.mkdirsIfNotExists
import com.nekolaska.ktx.io.replaceWith
import com.nekolaska.ktx.value.toFile
import com.nekolaska.ktx.view.onClick
import com.nekolaska.utils.Toaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class BuildExecutingFragment : ProviderFragment() {
    companion object {
        private const val ARG_PROJECT = "arg_project"
        private const val ARG_CONFIG = "arg_config"
        private const val ARG_OPTIONS = "arg_options"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun newInstance(
            project: ProjectItem,
            config: InitConfig,
            options: BuildOptions
        ) = BuildExecutingFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_PROJECT, project)
                putParcelable(ARG_CONFIG, config)
                putParcelable(ARG_OPTIONS, options)
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
    private val options: BuildOptions by lazy {
        @Suppress("DEPRECATION")
        requireArguments().getParcelable(ARG_OPTIONS)!!
    }

    private var _binding: FragmentBuildExecutingBinding? = null
    private val binding get() = _binding!!

    private val session by lazy { ViewModelProvider(this)[BuildSession::class.java] }
    private var pendingInstallPath: String? = null
    private var classDialog: SelectDialog? = null

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
            ?.getString("baseApkPackage", "github.znzsofficial.neluaj") ?: "github.znzsofficial.neluaj"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuildExecutingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.category_compile)

        // 绑定工程基本信息
        binding.projectName.text = config.appName.ifBlank { project.file.name }
        binding.projectPackageVersion.text =
            "${config.packageName} · ${config.versionName} (${config.versionCode})"
        project.iconPath?.let { binding.projectIcon.load(it) }

        // 拦截返回键（构建中禁止返回退出）
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!session.running) {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        // 折叠日志处理
        var logExpanded = false
        binding.logHeader.onClick {
            logExpanded = !logExpanded
            binding.logContainer.visibility = if (logExpanded) View.VISIBLE else View.GONE
            binding.logArrow.animate()
                .rotation(if (logExpanded) 90f else 0f)
                .setDuration(200)
                .start()
        }

        // 成功状态按钮
        binding.btnCancel.onClick { cancelBuild() }
        binding.btnInstall.onClick {
            session.successFile?.let { installApk(it) }
        }
        binding.btnShare.onClick {
            session.successFile?.let { shareApk(it) }
        }
        binding.btnOpenFolder.onClick {
            openFolder(Environment.getExternalStorageDirectory().resolve("LuaJ").resolve("Builds"))
        }
        binding.btnDone.onClick {
            parentFragmentManager.popBackStack()
        }

        // 失败状态按钮
        binding.btnCopyError.onClick {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Build Error", session.failureDetail))
            Toaster.instance.show(getString(R.string.build_error_copied))
        }
        binding.btnRetry.onClick {
            parentFragmentManager.popBackStack()
        }

        session.listener = { render() }
        render()
        when {
            session.job != null -> Unit
            savedInstanceState != null -> showInterrupted()
            else -> startBuild()
        }
    }

    private fun showInterrupted() {
        session.running = false
        session.failureStep = getString(R.string.build_step_base)
        session.failureMessage = getString(R.string.build_interrupted)
        render()
    }

    private fun cancelBuild() {
        session.job?.cancel()
        if (isAdded) parentFragmentManager.popBackStack()
    }

    private fun render() {
        if (_binding == null) return
        binding.logText.text = session.logText()
        binding.currentStepTitle.text = session.stepTitle.ifBlank { getString(R.string.build_step_base) }
        binding.stepProgress.setProgressCompat(session.stepIndex, true)
        binding.stepSummary.text = "${session.stepIndex} / 5"
        binding.cardBuilding.visibility = if (session.running) View.VISIBLE else View.GONE
        binding.cardSuccess.visibility = if (session.successFile != null) View.VISIBLE else View.GONE
        binding.cardFailure.visibility = if (session.failureStep != null) View.VISIBLE else View.GONE
        session.successFile?.let { file ->
            binding.successApkName.text = file.name
            val sizeMb = file.length().toDouble() / (1024 * 1024)
            binding.successApkSize.text = getString(
                R.string.build_artifact_size,
                String.format(Locale.US, "%.2f MB", sizeMb)
            )
            binding.successApkPath.text = getString(R.string.build_artifact_path, file.absolutePath)
        }
        session.failureStep?.let { binding.failureStep.text = getString(R.string.build_failed_at, it) }
        binding.failureMessage.text = session.failureMessage
        showClassPickerIfNeeded()
    }

    private fun showClassPickerIfNeeded() {
        val dir = session.pendingSmaliDir ?: return
        if (session.classSelection.isCompleted || classDialog != null) return
        classDialog = SelectDialog(requireContext(), dir, {
            classDialog = null
            session.finishClassSelection()
        }) { node ->
            node.getAllCheckedItems().forEach { it.file.delete() }
        }
    }

    private fun appendLog(text: String) {
        session.append(text)
        session.requestRender(immediate = false)
    }

    private fun updateStep(stepIndex: Int, stepTitle: String) {
        session.stepIndex = stepIndex
        session.stepTitle = stepTitle
        session.requestRender(immediate = true)
    }

    private fun startBuild() {
        val appContext = requireContext().applicationContext
        val packageManager = appContext.packageManager
        val selectedBasePackage = baseApkPackage
        val buildsDir = Environment.getExternalStorageDirectory().resolve("LuaJ").resolve("Builds")
        val apkName = createApkName(config.appName, config.versionName)

        session.job = session.scope.launch {
            var currentStepName = getString(R.string.build_step_base)
            var workspace: File? = null
            try {
                BuildWorkspace.mutex.withLock {
                    withContext(Dispatchers.IO) {
                        workspace = BuildWorkspace.create(appContext)
                    }
                    val buildWorkspace = checkNotNull(workspace)
                    val apkEditor = Editor(appContext, buildWorkspace) { log ->
                        // 过滤高频微观文件日志，仅记录里程碑与关键信息
                        if (isSignificantLog(log)) {
                            appendLog(log)
                        }
                    }

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

                        // Step 1: Base Runtime
                        updateStep(1, getString(R.string.build_step_base))
                        currentStepName = getString(R.string.build_step_base)
                        val baseApk = copyBaseApk(inputDir, packageManager, selectedBasePackage)

                        try {
                            currentCoroutineContext().ensureActive()
                            // Step 2: Decode
                            updateStep(2, getString(R.string.build_step_decode))
                            currentStepName = getString(R.string.build_step_decode)
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
                                deDex = options.deDex,
                                skipSmaliComment = options.skipSmaliComment,
                                skipDexDebug = options.skipDexDebug
                            )

                            currentCoroutineContext().ensureActive()
                            if (options.deDex && smaliDir.exists()) {
                                session.beginClassSelection(smaliDir)
                                session.classSelection.await()
                            }

                            // Step 3: Compile / Build
                            currentCoroutineContext().ensureActive()
                            updateStep(3, getString(R.string.build_step_compile))
                            currentStepName = getString(R.string.build_step_compile)
                            apkEditor.build(tempFile.absolutePath)

                            // Step 4: Sign
                            currentCoroutineContext().ensureActive()
                            updateStep(4, getString(R.string.build_step_sign))
                            currentStepName = getString(R.string.build_step_sign)
                            appendLog("Signing APK with schemes: " +
                                listOfNotNull(
                                    "V1".takeIf { options.v1 },
                                    "V2".takeIf { options.v2 },
                                    "V3".takeIf { options.v3 },
                                    "V4".takeIf { options.v4 }
                                ).joinToString(", ")
                            )
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

                            // Step 5: Export
                            currentCoroutineContext().ensureActive()
                            updateStep(5, getString(R.string.build_step_export))
                            currentStepName = getString(R.string.build_step_export)
                            if (options.v4 && !sidecar.isFile) {
                                throw IOException("V4 signing did not produce ${sidecar.absolutePath}")
                            }
                            val finalApk = buildsDir.resolve(apkName)
                            exportArtifacts(
                                resultFile,
                                sidecar.takeIf { options.v4 },
                                finalApk,
                                buildsDir.resolve("$apkName.idsig")
                            )
                            session.successFile = finalApk
                        } finally {
                            tempFile.delete()
                            resultFile.delete()
                            sidecar.delete()
                            baseApk.file.delete()
                        }
                    }

                    onBuildSuccess()
                }
            } catch (error: PackageManager.NameNotFoundException) {
                onBuildFailure(
                    currentStepName,
                    error,
                    getString(R.string.error_base_apk_not_found_message)
                )
            } catch (error: Throwable) {
                if (error is CancellationException) return@launch
                onBuildFailure(currentStepName, error, error.message ?: error.toString())
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    workspace?.deleteRecursively()
                }
            }
        }
    }

    private fun isSignificantLog(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("error", ignoreCase = true) ||
            trimmed.contains("exception", ignoreCase = true) ||
            trimmed.contains("failed", ignoreCase = true) ||
            trimmed.contains("warn", ignoreCase = true)
        ) return true

        // 只屏蔽具体文件路径（res/、smali/、package_1/）的细碎进度日志，保留阶段提示
        val fileProgress = trimmed.startsWith("Encoding:") ||
            trimmed.startsWith("Decoding:") ||
            trimmed.startsWith("Adding:") ||
            trimmed.startsWith("Writing:") ||
            trimmed.startsWith("Reading:") ||
            trimmed.startsWith("Baksmali:") ||
            trimmed.startsWith("Inflating:") ||
            trimmed.startsWith("Extracting:") ||
            trimmed.startsWith("Scanning:") ||
            trimmed.startsWith("Sanitizing:") ||
            trimmed.startsWith("Compiling ")
        // XmlCoder 会打出不带前缀的裸资源路径行，一并视为噪声
        val barePath = trimmed.startsWith("res/") ||
            trimmed.startsWith("smali") ||
            trimmed.startsWith("assets/") ||
            trimmed.startsWith("root/") ||
            trimmed.startsWith("package_1/")
        if (!fileProgress && !barePath) return true
        return !(trimmed.contains("res/") ||
            trimmed.contains("smali/") ||
            trimmed.contains("package_1/"))
    }

    private fun onBuildSuccess() {
        session.running = false
        session.requestRender(immediate = true)
        Toaster.instance.show(getString(R.string.build_success))
    }

    private fun onBuildFailure(step: String, error: Throwable, message: String) {
        session.running = false
        session.failureStep = step
        session.failureMessage = message
        session.failureDetail = "[$step] $message\n\n${error.stackTraceToString()}"
        appendLog("[ERROR] ${session.failureDetail}")
        session.requestRender(immediate = true)
        Toaster.instance.show(getString(R.string.build_failed))
    }

    private fun installApk(file: File) {
        if (!file.isFile) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !requireContext().packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallPath = file.absolutePath
            unknownSourcesLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
            return
        }
        launchInstaller(file)
    }

    private fun launchInstaller(file: File) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toaster.instance.show(getString(R.string.install_failed))
        }
    }

    private fun shareApk(file: File) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.build_share)))
        }
    }

    private fun openFolder(folder: File) {
        val relative = folder.absolutePath
            .removePrefix(Environment.getExternalStorageDirectory().absolutePath)
            .trimStart('/', '\\')
            .replace('\\', '/')
        val documentId = "primary:$relative"
        val uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            documentId
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            Toaster.instance.show(folder.absolutePath)
        }
    }

    private data class CopiedApk(val file: File, val sourceIdentity: String)

    private fun copyBaseApk(targetDir: File, pm: PackageManager, packageName: String): CopiedApk {
        val info = pm.getApplicationInfo(packageName, 0)
        if (!info.splitSourceDirs.isNullOrEmpty()) {
            throw IOException("Split base APKs are not supported: $packageName")
        }
        val sourceApk = info.sourceDir?.toFile()
            ?: throw IOException("Base APK source is unavailable for $packageName")
        if (!sourceApk.isFile) {
            throw IOException("Base APK does not exist: ${sourceApk.absolutePath}")
        }
        val target = targetDir.resolve("base.apk")
        val temp = targetDir.resolve("base.apk.tmp")
        try {
            sourceApk.copyTo(temp, overwrite = true)
            target.replaceWith(temp)
        } finally {
            temp.delete()
        }
        return CopiedApk(target, sourceApk.canonicalPath)
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
                if (apkTarget.exists() && !apkTarget.renameTo(apkBackup)) {
                    throw IOException("Cannot back up ${apkTarget.absolutePath}")
                }
                apkBackedUp = apkBackup.exists()
                if (sidecarTarget.exists() && !sidecarTarget.renameTo(sidecarBackup)) {
                    throw IOException("Cannot back up ${sidecarTarget.absolutePath}")
                }
                sidecarBackedUp = sidecarBackup.exists()
                if (sidecarStage != null) {
                    if (!sidecarStage.renameTo(sidecarTarget)) {
                        throw IOException("Cannot export ${sidecarTarget.absolutePath}")
                    }
                    sidecarInstalled = true
                } else {
                    sidecarTarget.delete()
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

    override fun onDestroyView() {
        classDialog?.dismiss()
        classDialog = null
        session.listener = null
        super.onDestroyView()
        _binding = null
    }
}

class BuildSession : ViewModel() {
    private val log = StringBuilder()
    private val logLock = Any()
    private var lineCount = 0
    private val maxLines = 300
    private val scopeJob = SupervisorJob()
    val scope = CoroutineScope(scopeJob + Dispatchers.Main.immediate)
    var stepIndex = 1
    var stepTitle = ""
    var running = true
    var successFile: File? = null
    var failureStep: String? = null
    var failureMessage: String? = null
    var failureDetail = ""
    var job: kotlinx.coroutines.Job? = null
    var listener: (() -> Unit)? = null
    var pendingSmaliDir: File? = null
    var classSelection = CompletableDeferred<Unit>()
    private var renderScheduled = false
    private val renderRunnable = Runnable {
        renderScheduled = false
        listener?.invoke()
    }

    fun beginClassSelection(dir: File) {
        pendingSmaliDir = dir
        if (classSelection.isCompleted) classSelection = CompletableDeferred()
        requestRender(immediate = true)
    }

    fun finishClassSelection() {
        pendingSmaliDir = null
        if (!classSelection.isCompleted) classSelection.complete(Unit)
    }

    fun requestRender(immediate: Boolean) {
        if (immediate) {
            mainHandler.removeCallbacks(renderRunnable)
            renderScheduled = false
            mainHandler.post { listener?.invoke() }
            return
        }
        if (renderScheduled) return
        renderScheduled = true
        mainHandler.postDelayed(renderRunnable, 200)
    }

    fun append(text: String) {
        synchronized(logLock) {
            if (lineCount >= maxLines) {
                // 滑动窗口清理旧日志，保留前 100 行空间
                val firstBreak = log.indexOf('\n', log.length / 3)
                if (firstBreak != -1) {
                    log.delete(0, firstBreak + 1)
                    lineCount = log.count { it == '\n' }
                }
            }
            if (log.isNotEmpty()) log.append('\n')
            log.append(text)
            lineCount++
        }
    }

    fun logText(): String = synchronized(logLock) { log.toString() }

    override fun onCleared() {
        scopeJob.cancel()
    }
}

private val mainHandler = Handler(Looper.getMainLooper())
