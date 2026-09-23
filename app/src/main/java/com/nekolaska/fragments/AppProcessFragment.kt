package com.nekolaska.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil3.load
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.FragmentProcessBinding
import com.nekolaska.base.ProviderFragment
import com.nekolaska.data.BuildOptions
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.ktx.view.onClick

private val invalidApkNameChars = Regex("""[\\/:*?"<>|\u0000-\u001f\u007f]""")
private const val MAX_APK_BASENAME_BYTES = 180

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val selectedBasePackage = baseApkPackage
        applyBottomBarInsets()

        // 绑定顶部工程概览卡片
        binding.projectName.text = config.appName.ifBlank { project.file.name }
        val verStr = "${config.packageName} · ${config.versionName} (${config.versionCode})"
        binding.projectPackageVersion.text = verStr
        binding.baseRuntimeInfo.text = getString(R.string.base_runtime_status, selectedBasePackage)
        project.iconPath?.let { binding.projectIcon.load(it) }

        // 高级设置折叠逻辑
        var isAdvancedExpanded = false
        binding.advancedHeader.onClick {
            isAdvancedExpanded = !isAdvancedExpanded
            binding.advancedContent.visibility = if (isAdvancedExpanded) View.VISIBLE else View.GONE
            binding.advancedArrow.animate()
                .rotation(if (isAdvancedExpanded) 90f else 0f)
                .setDuration(200)
                .start()
        }

        // DEX 类移除子开关联动
        binding.deDexSwitch.setOnCheckedChangeListener { _, checked ->
            binding.deDexOptionsContainer.visibility = if (checked) View.VISIBLE else View.GONE
        }

        var buildStarted = false
        binding.startButton.onClick {
            if (buildStarted) return@onClick
            buildStarted = true
            binding.startButton.isEnabled = false
            val options = BuildOptions(
                compileLua = binding.compileSwitch.isChecked,
                deDex = binding.deDexSwitch.isChecked,
                skipSmaliComment = binding.skipSmaliCommentSwitch.isChecked,
                skipDexDebug = binding.skipDexDebugSwitch.isChecked,
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
            val mainActivity = activity as? com.nekolaska.MainActivity
            if (mainActivity != null) {
                mainActivity.setFragment(BuildExecutingFragment.newInstance(project, config, options))
            }
        }
    }

    private fun applyBottomBarInsets() {
        val scrollPadding = binding.contentScroll.paddingBottom
        val barPadding = binding.bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { bar, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bar.updatePadding(bottom = barPadding + bars.bottom)
            binding.contentScroll.updatePadding(bottom = scrollPadding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.bottomBar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
