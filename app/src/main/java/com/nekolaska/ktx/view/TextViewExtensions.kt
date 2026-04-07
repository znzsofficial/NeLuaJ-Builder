package com.nekolaska.ktx.view

import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.widget.TextView

fun TextView.buildStyledText(builderAction: SpannableStringBuilder.() -> Unit) {
    setText(SpannableStringBuilder().apply(builderAction), TextView.BufferType.SPANNABLE)
}

@SuppressLint("ClickableViewAccessibility")
fun TextView.enableScrollMovement() {
    movementMethod = ScrollingMovementMethod.getInstance()
    // 当内容可滚动时，阻止父 NestedScrollView 拦截触摸事件
    setOnTouchListener { v, event ->
        if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        false
    }
}

fun TextView.scrollToBottom() {
    val offset = lineCount * lineHeight
    if (offset > height) scrollTo(0, offset - height)
}