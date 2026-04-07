package com.nekolaska.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nekolaska.Builder.databinding.FragmentPermissionBinding
import com.nekolaska.MainActivity
import com.nekolaska.base.ProviderFragment
import com.nekolaska.ktx.view.onClick


class NeedPermissionFragment : ProviderFragment() {
    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = "Permissions"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.grantButton.onClick {
            activity<MainActivity> {
                requestForStoragePermissions()
            }
        }
        binding.nextButton.onClick {
            activity<MainActivity> {
                setFragment(ProjectListFragment())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}