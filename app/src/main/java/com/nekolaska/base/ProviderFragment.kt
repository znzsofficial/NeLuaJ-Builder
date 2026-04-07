package com.nekolaska.base

import androidx.core.util.TypedValueCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import coil3.imageLoader

open class ProviderFragment : Fragment() {
    protected val imageLoader get() = requireActivity().imageLoader
    protected inline fun <reified T : FragmentActivity> activity(action: T.() -> Unit) {
        (activity as? T)?.action()
    }

    protected val Float.dp get() = TypedValueCompat.dpToPx(this, resources.displayMetrics)
    protected fun Int.strRes() = getString(this)

    protected fun String.getApplicationInfo(flags: Int) =
        activity?.packageManager?.getApplicationInfo(this, flags)

    protected fun String.forPreference() = activity?.getSharedPreferences(this, 0)
}