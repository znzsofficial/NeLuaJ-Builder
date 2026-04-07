package com.nekolaska.utils

import android.content.Context
import android.widget.Toast
import com.nekolaska.Builder.BuildConfig

class Toaster private constructor(private val context: Context) {
    companion object : SingletonHolder<Toaster, Context>(::Toaster)

    fun show(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showLong(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun debugShow(message: String) {
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}