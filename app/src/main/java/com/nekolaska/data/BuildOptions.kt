package com.nekolaska.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BuildOptions(
    val compileLua: Boolean,
    val deDex: Boolean,
    val skipSmaliComment: Boolean,
    val skipDexDebug: Boolean,
    val keepIcon: Boolean,
    val keepOpt: Boolean,
    val keepAService: Boolean,
    val keepWService: Boolean,
    val keepLuaService: Boolean,
    val keepNotificationService: Boolean,
    val v1: Boolean,
    val v2: Boolean,
    val v3: Boolean,
    val v4: Boolean
) : Parcelable
