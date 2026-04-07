package com.nekolaska.ktx.fragment

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

fun FragmentManager.transaction(block: FragmentTransaction.() -> Unit) {
    beginTransaction().also {
        block(it)
    }.commit()
}