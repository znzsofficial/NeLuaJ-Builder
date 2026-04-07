package com.nekolaska.ktx.context

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.target.ImageViewTarget
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nekolaska.dialog.ProgressDialog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

suspend fun Context.showProgressDialogAndExecute(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    action: suspend () -> Unit
) {
    val dialog = ProgressDialog(this)
    dialog.show()
    try {
        withContext(dispatcher) {
            action()
        }
    } finally {
        dialog.dismiss()
    }
}

fun Context.imageRequestOf(data: Any, onSuccess: (Drawable) -> Unit) = ImageRequest.Builder(this)
    .data(data)
    .target { onSuccess(it.asDrawable(resources)) }
    .build()


fun Context.imageRequestOf(data: Any, target: ImageView) =
    ImageRequest.Builder(this)
        .data(data)
        .target(ImageViewTarget(target))
        .build()

@Suppress("NOTHING_TO_INLINE")
inline fun Context.checkSelfPermissionCompat(permission: String) =
    ContextCompat.checkSelfPermission(this, permission)

fun Context.checkSelfPermissionGranted(permission: String) =
    checkSelfPermissionCompat(permission) == PackageManager.PERMISSION_GRANTED

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Context.getSystemServiceCompat(service: Class<T>) =
    ContextCompat.getSystemService(this, service)

/**
 * Instantiates an [AlertDialog.Builder] for the [Context], sets the passed [title] and [message] ,
 * applies the [dialogConfig] lambda to it, then creates an [AlertDialog] from
 * the builder, and returns it, so you can call [AlertDialog.show] on the created dialog.
 */
@OptIn(ExperimentalContracts::class)
inline fun Context.alertDialog(
    title: CharSequence? = null,
    message: CharSequence? = null,
    isCancellable: Boolean = true,
    dialogConfig: AlertDialog.Builder.() -> Unit = {}
): AlertDialog {
    contract { callsInPlace(dialogConfig, InvocationKind.EXACTLY_ONCE) }
    return MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(isCancellable)
        .apply(dialogConfig)
        .show()
}

@Suppress("NOTHING_TO_INLINE")
inline fun Context.getDrawableCompat(@DrawableRes id: Int) =
    AppCompatResources.getDrawable(this, id)