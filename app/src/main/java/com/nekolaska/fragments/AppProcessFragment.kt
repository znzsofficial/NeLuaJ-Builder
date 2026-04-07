package com.nekolaska.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.FragmentProcessBinding
import com.nekolaska.apk.Editor
import com.nekolaska.apk.MySigner
import com.nekolaska.base.ProviderFragment
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.dialog.AppProcessDialog
import com.nekolaska.dialog.SelectDialog
import com.nekolaska.ktx.context.alertDialog
import com.nekolaska.ktx.dialog.positiveButton
import com.nekolaska.ktx.io.getChild
import com.nekolaska.ktx.io.mkdirsIfNotExists
import com.nekolaska.ktx.value.toFile
import com.nekolaska.ktx.view.onClick
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dialog = AppProcessDialog(requireContext())
        val baseApkDir = context?.cacheDir?.resolve("apk")!!.mkdirsIfNotExists()
        val apkEditor = Editor(requireContext()) {
            dialog.setMessage(it)
        }
        binding.startButton.onClick {
            if (copyBaseApk(baseApkDir)) {
                dialog.show()
                val baseApkFile = baseApkDir.getChild("base.apk")!!
                lifecycleScope.launch(CoroutineExceptionHandler { _, e ->
                    dialog.dismiss()
                    context?.alertDialog("Error", e.stackTraceToString()) {
                        setPositiveButton(android.R.string.ok, null)
                    }
                }) {
                    val buildsDir =
                        Environment.getExternalStorageDirectory().resolve("LuaJ")
                            .resolve("Builds").mkdirsIfNotExists()
                    val apkName = "${config.appName}_${config.versionName}.apk"
                    val tempFile = baseApkDir.resolve("temp.apk")
                    val resultFile = baseApkDir.resolve(apkName)
                    // 反编译相关
                    val deDex = binding.deDexSwitch.isChecked
                    val smaliDir = apkEditor.cacheDir.resolve("smali")
                    withContext(Dispatchers.IO) {
                        apkEditor.decode(
                            apkInputPath = baseApkFile.path,
                            oldPackage = baseApkPackage,
                            config = config,
                            project = project,
                            compile = binding.compileSwitch.isChecked,
                            keepIcon = binding.keepIcon.isChecked,
                            keepOpt = binding.keepOpt.isChecked,
                            keepAService = binding.keepAservice.isChecked,
                            keepWService = binding.keepWservice.isChecked,
                            deDex = deDex
                        )
                        if (deDex && smaliDir.exists()) withContext(
                            Dispatchers.Main
                        ) {
                            suspendCoroutine { continuation ->
                                SelectDialog(
                                    requireContext(), smaliDir,
                                    {
                                        continuation.resume(Unit)
                                    }
                                ) {
                                    it.getAllCheckedItems().forEach { node ->
                                        node.file.delete()
                                    }
                                }
                            }
                        }
                        apkEditor.build(tempFile.absolutePath)
                        apkEditor.logCallback("Signing APK")
                        MySigner(requireContext()).start(
                            tempFile.absolutePath,
                            resultFile.absolutePath,
                            binding.v4Switch.isChecked,
                        )
                        apkEditor.logCallback("Moving APK")
                        // 将打包的 apk 复制到 builds 目录
                        resultFile.apply {
                            copyTo(
                                buildsDir.resolve(apkName),
                                overwrite = true
                            )
                            delete()
                        }
                        // 如果开启了V4签名，则复制签名文件
                        if (binding.v4Switch.isChecked) {
                            apkEditor.logCallback("Moving V4 Signature")
                            baseApkDir.resolve("$apkName.idsig").apply {
                                copyTo(
                                    buildsDir.resolve("$apkName.idsig"),
                                    overwrite = true
                                )
                                delete()
                            }
                        }
                        apkEditor.logCallback("Cleaning")
                        // 删除中间文件
                        tempFile.delete()
                        // 删除 base.apk
                        baseApkFile.delete()
                    }
                    dialog.dismiss()
                    context?.alertDialog(
                        R.string.build_success.strRes(),
                        R.string.save_to.strRes() + buildsDir.resolve(apkName)
                    ) {
                        setPositiveButton(android.R.string.ok, null)
                    }
                }
            }
        }
    }


    fun copyBaseApk(apkDir: File) = runCatching {
        val sourceFile = baseApkPackage.getApplicationInfo(0)?.sourceDir?.toFile()
        val targetFile = apkDir.resolve("base.apk")
        if (sourceFile != null) {
            // 检查目标文件是否存在，或者源文件和目标文件的大小是否不同
            if (!targetFile.exists() || sourceFile.length() != targetFile.length()) {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        }
    }.onFailure {
        if (it is PackageManager.NameNotFoundException)
            context?.alertDialog(
                title = R.string.error_base_apk_not_found.strRes(),
                message = R.string.error_base_apk_not_found_message.strRes() + "\n" + it.message,
                isCancellable = false
            ) {
                positiveButton(android.R.string.ok) {
                    activity?.supportFragmentManager?.apply {
                        if (backStackEntryCount > 0) {
                            popBackStack()
                        }
                    }
                }
            }
    }.isSuccess

}