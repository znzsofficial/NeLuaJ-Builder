package com.nekolaska.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.nekolaska.ktx.context.imageRequestOf
import com.nekolaska.ktx.dialog.negativeButton
import com.nekolaska.ktx.dialog.positiveButton
import com.nekolaska.ktx.io.getChild
import com.nekolaska.ktx.value.appendStyled
import com.nekolaska.ktx.value.appendStyledLine
import com.nekolaska.ktx.view.buildStyledText
import com.nekolaska.ktx.view.enableScrollMovement
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

    fun readConfig() {
        binding.appConfig.buildStyledText {
            appendStyled(R.string.config_name.strRes(), colorPrimary)
            appendLine(config.appName)

            appendStyled(R.string.config_package.strRes(), colorPrimary)
            appendLine(config.packageName)

            appendStyled(R.string.config_versionName.strRes(), colorPrimary)
            appendLine(config.versionName)

            appendStyled(R.string.config_versionCode.strRes(), colorPrimary)
            appendLine(config.versionCode.toString())

            appendStyled(R.string.config_targetSdk.strRes(), colorPrimary)
            appendLine(config.targetSDK.toString())

            appendStyled(R.string.config_minSdk.strRes(), colorPrimary)
            appendLine(config.minSDK.toString())

            appendStyled(R.string.config_debug.strRes(), colorPrimary)
            append(config.debuggable.toString())
        }
    }

    fun loadProjectIcon() {
        imageLoader.enqueue(
            requireContext().imageRequestOf(
                project.iconPath ?: R.drawable.icon
            ) {
                // 转换 48dp 为像素
                val size = 48f.dp.toInt()

                // 设置 drawable 大小
                it.setBounds(0, 0, size, size)
                // 设置 drawable 到 TextView
                binding.title.setCompoundDrawables(it, null, null, null)
            }
        )
    }

    private fun getConfigFromFile() =
        InitConfig.parse(requireContext(), project.file.getChild("init.lua")!!)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 读取 init.lua 文件
        config = getConfigFromFile()
        val configUtil = PermissionHelper.instance
        fun updatePermissionText() = binding.appPermission.buildStyledText {
            val permissionList = config.userPermission
            permissionList.forEachIndexed { index, item ->
                appendLine(item)
                appendStyledLine(
                    configUtil.getName("android.permission.$item"),
                    colorPrimary
                )
            }
            // 删除最后的换行符
            toString().let { delete(it.length - 1, it.length) }
        }
        // 标题
        binding.title.text = project.file.name
        binding.appPermission.enableScrollMovement()
        binding.permissionButton.onClick {
            PermissionDialog(requireActivity(), config) {
                updatePermissionText()
            }
        }
        binding.configTitle.onClick {
            ConfigDialog(requireActivity(), config) {
                readConfig()
            }
        }
        binding.saveButton.onClick {
            Toaster.instance.show(
                if (config.dumpToFile(project.file.getChild("init.lua")!!.absolutePath)) R.string.save_success.strRes()
                else R.string.save_fail.strRes()
            )
        }

        // 加载图标
        loadProjectIcon()
        readConfig()
        updatePermissionText()

        binding.buildButton.onClick {
            activity<MainActivity> {
                if (config != getConfigFromFile()) {
                    alertDialog(
                        R.string.config_not_save.strRes(),
                        R.string.config_not_save_message.strRes()
                    ) {
                        positiveButton(R.string.save) {
                            config.dumpToFile(project.file.getChild("init.lua")!!.absolutePath)
                            setFragment(AppProcessFragment.newInstance(project, config))
                        }
                        setNeutralButton(R.string.config_not_save_continue) { _, _ ->
                            setFragment(AppProcessFragment.newInstance(project, config))
                        }
                        negativeButton(android.R.string.cancel) {
                            it.dismiss()
                        }
                    }
                } else setFragment(AppProcessFragment.newInstance(project, config))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}