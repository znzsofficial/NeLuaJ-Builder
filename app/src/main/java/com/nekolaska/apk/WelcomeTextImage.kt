package com.nekolaska.apk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/** Draws splash text into a PNG. Window backgrounds cannot contain a TextView. */
internal class WelcomeTextImage(
    private val density: Float,
    private val fontScale: Float
) : WelcomeXml.TextPainter {
    override fun paint(text: String, textSize: String, textColor: String, file: File): Pair<String, String> {
        val scale = density.coerceAtLeast(1f)
        val pixels = textSize.removeSuffix("sp").toFloat() * scale * fontScale.coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(textColor)
            this.textSize = pixels
            typeface = Typeface.DEFAULT
        }
        val lines = text.split('\n')
        val metrics = paint.fontMetrics
        val lineHeight = ceil(metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
        val pad = ceil(4f * scale).toInt()
        val width = ceil(lines.maxOf { paint.measureText(it) }).toInt() + pad * 2
        val height = lineHeight * lines.size + pad * 2
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var baseline = pad - metrics.ascent
        lines.forEach { line ->
            canvas.drawText(line, pad.toFloat(), baseline, paint)
            baseline += lineHeight
        }
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val widthDp = (bitmap.width / scale).toInt().coerceAtLeast(1)
        val heightDp = (bitmap.height / scale).toInt().coerceAtLeast(1)
        bitmap.recycle()
        return "${widthDp}dp" to "${heightDp}dp"
    }
}
