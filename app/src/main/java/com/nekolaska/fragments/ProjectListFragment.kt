package com.nekolaska.fragments

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nekolaska.Builder.R
import com.nekolaska.Builder.databinding.FragmentProjectListBinding
import com.nekolaska.MainActivity
import com.nekolaska.adapter.ProjectListAdapter
import com.nekolaska.base.ProviderFragment
import com.nekolaska.data.InitConfig
import com.nekolaska.data.ProjectItem
import com.nekolaska.ktx.io.getChild
import com.nekolaska.ktx.view.fastScroller
import com.nekolaska.ktx.view.linearLayoutManager
import com.nekolaska.utils.Toaster
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProjectListFragment : ProviderFragment() {
    private val projectFile =
        Environment.getExternalStorageDirectory().resolve("LuaJ").resolve("Projects")

    private var _binding: FragmentProjectListBinding? = null
    private val binding get() = _binding!!
    private var refreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = "Projects"
        refreshList()
    }

    private fun refreshList() {
        if (_binding == null) return
        refreshJob?.cancel()
        val appContext = requireContext().applicationContext
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            val projects = withContext(Dispatchers.IO) {
                if (!projectFile.exists() && !projectFile.mkdirs()) return@withContext emptyList()
                projectFile.getProjectList(appContext)
            }
            if (_binding == null) return@launch
            binding.recyclerView.adapter = ProjectListAdapter(projects) {
                if (!it.file.resolve("init.lua").isFile) {
                    Toaster.instance.show(R.string.error_no_init_lua.strRes())
                } else {
                    activity<MainActivity> { setFragment(ProjectDetailFragment.newInstance(it)) }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.apply {
            linearLayoutManager()
            fastScroller()
                .setPadding(0, 16, 4, 16)
                .build()
        }
    }

    override fun onDestroyView() {
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
        // Clear the binding reference to prevent memory leaks
        _binding = null
    }

    private fun File.packageName(context: Context): String {
        val initLuaFile = getChild("init.lua") ?: return R.string.directory.strRes()
        if (!initLuaFile.isFile) return R.string.directory.strRes()
        return runCatching {
            InitConfig.parse(context, initLuaFile).packageName
        }.getOrDefault(R.string.directory.strRes())
    }

    private fun File.getProjectList(context: Context): List<ProjectItem> {
        return listFiles()
            ?.filter(File::isDirectory)
            ?.map { ProjectItem(it, it.getChild("icon.png")?.absolutePath, it.packageName(context)) }
            ?: emptyList()
    }
}
