package com.nekolaska.fragments

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
import com.nekolaska.data.ProjectItem
import com.nekolaska.ktx.io.getChild
import com.nekolaska.ktx.io.haveChild
import com.nekolaska.ktx.io.loadLua
import com.nekolaska.ktx.io.mkdirsIfNotExists
import com.nekolaska.ktx.value.toStringOr
import com.nekolaska.ktx.view.fastScroller
import com.nekolaska.ktx.view.linearLayoutManager
import com.nekolaska.utils.Toaster
import java.io.File

class ProjectListFragment : ProviderFragment() {
    private val projectFile =
        Environment.getExternalStorageDirectory().resolve("LuaJ").resolve("Projects")
            .mkdirsIfNotExists()

    private var _binding: FragmentProjectListBinding? = null
    private val binding get() = _binding!!

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
        binding.recyclerView.adapter =
            ProjectListAdapter(projectFile.getProjectList()) {
                if (!it.file.haveChild("init.lua")) Toaster.instance.show(R.string.error_no_init_lua.strRes())
                else activity<MainActivity> { setFragment(ProjectDetailFragment.newInstance(it)) }
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
        super.onDestroyView()
        // Clear the binding reference to prevent memory leaks
        _binding = null
    }

    private val File.packageName: String
        get() {
            val initLuaFile = getChild("init.lua") ?: return R.string.directory.strRes()
            val value = initLuaFile.loadLua()["package_name"]
            return value toStringOr R.string.directory.strRes()
        }

    private fun File.getProjectList(): List<ProjectItem> {
        return listFiles()?.filter { it.isDirectory }
            ?.map { ProjectItem(it, it.getChild("icon.png")?.absolutePath, it.packageName) }
            ?: emptyList()
    }
}