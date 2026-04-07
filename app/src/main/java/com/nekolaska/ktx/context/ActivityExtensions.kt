package com.nekolaska.ktx.context

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.viewbinding.ViewBinding
import com.nekolaska.utils.Toaster

@Suppress("NOTHING_TO_INLINE")
inline fun Activity.requestPermissionsCompat(requestCode: Int, vararg permissions: String) =
    ActivityCompat.requestPermissions(this, permissions, requestCode)

/*
* Starts the Activity [A], in a more concise way, while still allowing to configure the [Intent] in
* the optional [configIntent] lambda.
*/
inline fun <reified A : Activity> Context.start(configIntent: Intent.() -> Unit = {}) {
    startActivity(Intent(this, A::class.java).apply(configIntent))
}
