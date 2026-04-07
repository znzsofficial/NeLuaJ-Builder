package com.nekolaska.ktx.view

import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.widget.TextView

fun TextView.buildStyledText(builderAction: SpannableStringBuilder.() -> Unit) {
    setText(SpannableStringBuilder().apply(builderAction), TextView.BufferType.SPANNABLE)
}

fun TextView.enableScrollMovement() {
    movementMethod = ScrollingMovementMethod.getInstance()
}

fun TextView.scrollToBottom() {
    val offset = lineCount * lineHeight
    if (offset > height) scrollTo(0, offset - height)
}