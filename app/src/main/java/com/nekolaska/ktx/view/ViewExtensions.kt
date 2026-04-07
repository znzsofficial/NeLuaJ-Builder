package com.nekolaska.ktx.view

import android.view.View
import android.widget.ImageView
import androidx.annotation.AttrRes
import com.google.android.material.color.MaterialColors

fun ImageView.setColorFilterFromAttr(@AttrRes colorAttributeResId: Int) {
    setColorFilter(MaterialColors.getColor(this, colorAttributeResId))
}
/**
 * Registers the [block] lambda as [View.OnClickListener] to this View.
 *
 * If this View is not clickable, it becomes clickable.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun View.onClick(block: View.OnClickListener) = setOnClickListener(block)