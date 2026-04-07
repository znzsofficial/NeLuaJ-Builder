package com.nekolaska.ktx.graphic

import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.AttrRes
import com.google.android.material.color.MaterialColors


fun Drawable.setColorFilterFromAttr(context: Context, @AttrRes colorAttributeResId: Int) {
    val color = MaterialColors.getColor(context, colorAttributeResId, 0)
    colorFilter =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 对于Android Q及以上版本，使用BlendModeColorFilter
            BlendModeColorFilter(color, BlendMode.SRC_IN)
        } else {
            // 对于Android Q以下版本，使用PorterDuffColorFilter
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
//    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
//            color,
//    BlendModeCompat.SRC_IN
//    )
}