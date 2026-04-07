package com.nekolaska.ktx.value

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.io.File

/**
 * 扩展函数：为字符串的前面指定范围的字符设置粗体和颜色
 *
 * @param color 要设置的颜色
 * @return 应用了样式的 SpannableStringBuilder 实例
 */
fun String.toStyledSpannable(color: Int) = SpannableStringBuilder(this).apply {
    val length = length
    // 设置颜色
    setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    // 设置粗体
    setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}

fun SpannableStringBuilder.appendStyled(text: String, color: Int) {
    append(text.toStyledSpannable(color))
}

fun String.toFile() = File(this)

fun SpannableStringBuilder.appendStyledLine(text: String, color: Int) {
    appendLine(text.toStyledSpannable(color))
}