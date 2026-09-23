package com.nekolaska.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil3.load
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.FragmentDetailBinding
import com.nekolaska.MainActivity
import com.nekolaska.base.ProviderFragment
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.dialog.ConfigDialog
import com.nekolaska.dialog.PermissionDialog
import com.nekolaska.ktx.context.alertDialog
import com.nekolaska.ktx.dialog.negativeButton
import com.nekolaska.ktx.dialog.positiveButton
import com.nekolaska.ktx.io.getChild
import com.nekolaska.ktx.view.onClick
import com.nekolaska.utils.PermissionHelper
import com.nekolaska.utils.Toaster
import kotlin.properties.Delegates

class ProjectDetailFragment : ProviderFragment() {
    companion object {
        private const val ARG_PROJECT = "arg_project"
        fun newInstance(project: ProjectItem) = ProjectDetailFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_PROJECT, project) }
        }
    }

    private val project: ProjectItem by lazy {
        @Suppress("DEPRECATION")
        requireArguments().getParcelable(ARG_PROJECT)!!
    }
    private var colorPrimary by Delegates.notNull<Int>()
    private lateinit var config: InitConfig
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = "Config"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        colorPrimary = MaterialColors.getColor(
            context,
            android.R.attr.colorPrimary,
            0
        )
    }

    private fun refreshConfigViews() {
        binding.valAppName.text = config.appName.ifBlank { project.file.name }
        binding.valPackageName.text = config.packageName
        binding.valVersion.text = "${config.versionName} (${config.versionCode})"
        binding.valSdk.text = "Min: ${config.minSDK}  |  Target: ${config.targetSDK}"
        binding.valDebug.text = config.debuggable.toString()
    }

    private fun refreshPermissions() {
        val permissionList = config.userPermission
        val chipGroup = binding.permissionChipGroup
        chipGroup.removeAllViews()
        if (permissionList.isEmpty()) {
            binding.noPermissionHint.visibility = View.VISIBLE
            chipGroup.visibility = View.GONE
        } else {
            binding.noPermissionHint.visibility = View.GONE
            chipGroup.visibility = View.VISIBLE
            val helper = PermissionHelper.instance
            permissionList.forEach { perm ->
                val chip = Chip(requireContext()).apply {
                    val friendlyName = helper.getName("android.permission.$perm")
                    text = if (friendlyName.isNotBlank() && friendlyName != perm) "$friendlyName ($perm)" else perm
                    isCheckable = false
                    isClickable = false
                    setChipBackgroundColorResource(android.R.color.transparent)
                    chipStrokeWidth = 1f.dp
                    chipStrokeColor = ColorStateList.valueOf(
                        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
                    )
                    shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                        .setAllCornerSizes(12f.dp)
                        .build()
                }
                chipGroup.addView(chip)
            }
        }
    }

    private fun getConfigFromFile() =
        InitConfig.parse(requireContext(), project.file.getChild("init.lua")!!)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val loadedConfig = runCatching { getConfigFromFile() }.getOrNull()
        if (loadedConfig == null) {
            Toaster.instance.show(R.string.error_invalid_config.strRes())
            parentFragmentManager.popBackStack()
            return
        }
        config = loadedConfig
        applyBottomBarInsets()

        // 顶部项目卡片
        binding.projectDirName.text = project.file.name
        binding.projectPath.text = project.file.absolutePath
        project.iconPath?.let { binding.projectIcon.load(it) }

        // 编辑配置入口
        val openConfigEditor = {
            ConfigDialog(requireActivity(), config) {
                refreshConfigViews()
            }
        }
        binding.btnEditConfig.onClick { openConfigEditor() }

        // 编辑权限入口
        val openPermissionEditor = {
            PermissionDialog(requireActivity(), config) {
                refreshPermissions()
            }
        }
        binding.btnEditPermissions.onClick { openPermissionEditor() }

        // 保存文件
        binding.saveButton.onClick {
            val file = project.file.getChild("init.lua")!!
            val ok = config.dumpToFile(file.absolutePath)
            Toaster.instance.show(
                if (ok) R.string.save_success.strRes()
                else R.string.save_fail.strRes()
            )
        }

        // 下一步：前往打包配置
        binding.buildButton.onClick {
            activity<MainActivity> {
                val validationErrors = config.validationErrors()
                if (validationErrors.isNotEmpty()) {
                    alertDialog(
                        R.string.error_invalid_config.strRes(),
                        validationErrors.joinToString("\n")
                    ) {
                        positiveButton(android.R.string.ok) { it.dismiss() }
                    }
                    return@activity
                }
                val fileConfig = runCatching { getConfigFromFile() }.getOrNull()
                if (fileConfig == null) {
                    Toaster.instance.show(R.string.error_invalid_config.strRes())
                    return@activity
                }
                if (config != fileConfig) {
                    alertDialog(
                        R.string.config_not_save.strRes(),
                        R.string.config_not_save_message.strRes()
                    ) {
                        positiveButton(R.string.save) {
                            if (config.dumpToFile(project.file.getChild("init.lua")!!.absolutePath)) {
                                setFragment(AppProcessFragment.newInstance(project, config))
                            } else {
                                Toaster.instance.show(R.string.save_fail.strRes())
                            }
                        }
                        negativeButton(android.R.string.cancel) {
                            it.dismiss()
                        }
                    }
                } else {
                    setFragment(AppProcessFragment.newInstance(project, config))
                }
            }
        }

        refreshConfigViews()
        refreshPermissions()
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
