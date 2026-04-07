package com.nekolaska.ktx.result

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

fun ActivityResultLauncher<Intent>.launch(block: Intent.() -> Unit) = launch(Intent().apply(block))