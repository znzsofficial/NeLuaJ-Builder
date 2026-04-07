package com.nekolaska.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class ProjectItem(val file: File, val iconPath: String?, val packageName: String) : Parcelable