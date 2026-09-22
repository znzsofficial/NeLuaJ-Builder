package com.nekolaska.apk

import org.luaj.Globals
import org.luaj.LoadState
import org.luaj.LuaError
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.compiler.LuaC
import org.luaj.lib.BaseLib
import org.luaj.lib.Bit32Lib
import org.luaj.lib.DebugLib
import org.luaj.lib.StringLib
import org.luaj.lib.TableLib
import org.luaj.lib.Utf8Lib
import org.luaj.lib.jse.JseMathLib
import java.io.File

/** Runs project welcome.lua in a bare Lua environment and writes the splash drawable. */
internal object WelcomeXml {
    private const val MAX_BYTES = 64 * 1024
    private const val MAX_INSTRUCTIONS = 100_000
    private const val MAX_SLOTS = 4
    private const val TEXT_DENSITY = 4f
    private var imageSlot = 0
    private val simpleFields = setOf(
        "background", "icon", "icon_size", "gravity", "image",
        "text", "text_size", "text_color", "text_gravity"
    )
    private val layerFields = setOf(
        "color", "shape", "corners", "gradient_start", "gradient_end", "gradient_angle",
        "icon", "image", "size", "gravity", "inset", "text", "text_size", "text_color"
    )
    private val textSizePattern = Regex("[1-9][0-9]{0,2}sp")
    private val colorPattern = Regex("#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{8}")
    private val size = Regex("[1-9][0-9]{0,3}dp")
    private val angle = Regex("0|45|90|135|180|225|270|315")
    private val fileName = Regex("[a-z][a-z0-9_]*\\.(png|webp|jpg|jpeg)")
    private val shapeName = setOf("rectangle", "oval")
    private val gravityToken = setOf(
        "center", "center_horizontal", "center_vertical",
        "top", "bottom", "left", "right", "start", "end",
        "fill", "fill_horizontal", "fill_vertical",
        "clip_horizontal", "clip_vertical"
    )

    fun interface TextPainter {
        fun paint(text: String, textSize: String, textColor: String, file: File): Pair<String, String>
    }

    fun apply(projectDir: File, resourcesRoot: File) {
        val spec = projectDir.resolve("welcome.lua")
        if (!spec.isFile) return
        val xml = render(
            spec.readText(Charsets.UTF_8),
            projectDir,
            slotDir(resourcesRoot),
            WelcomeTextImage(TEXT_DENSITY, 1f)
        )
        val targets = welcomeFiles(resourcesRoot).ifEmpty {
            listOf(resourcesRoot.resolve("package_1/res/drawable-v23/welcome.xml"))
        }
        targets.forEach { file ->
            file.parentFile?.mkdirs()
            file.writeText(xml)
        }
    }

    internal fun render(
        source: String,
        projectDir: File,
        drawableDir: File,
        painter: TextPainter = TextPainter { _, _, _, _ -> error("welcome.lua text requires the Android renderer") }
    ): String {
        imageSlot = 0
        val table = execute(source)
        val texts = TextFiles(drawableDir, painter)
        val layers = table.get("layers")
        return if (!layers.isnil()) renderLayers(table, layers, projectDir, drawableDir, texts)
        else renderSimple(table, projectDir, drawableDir, texts)
    }

    private fun execute(source: String): LuaTable {
        val bytes = source.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "welcome.lua is too large" }
        require(bytes.isEmpty() || bytes[0] != 0x1b.toByte()) { "welcome.lua must be Lua source" }
        val text = source.removePrefix("\uFEFF")
        val globals = sandbox()
        val result = try {
            globals.load(text, "@welcome.lua").call()
        } catch (error: LuaError) {
            throw IllegalArgumentException("welcome.lua failed: ${error.message}", error)
        }
        require(result.istable()) { "welcome.lua must return a table" }
        return result.checktable()
    }

    private fun sandbox(): Globals {
        val globals = Globals()
        globals.load(BaseLib())
        globals.load(Bit32Lib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(JseMathLib())
        globals.load(Utf8Lib())
        globals.load(InstructionLimit())
        LoadState.install(globals)
        LuaC.install(globals)
        listOf(
            "dofile", "loadfile", "collectgarbage", "gcinfo", "newproxy", "module", "require",
            "io", "os", "package", "debug", "luajava", "import"
        ).forEach { globals.set(it, LuaValue.NIL) }
        return globals
    }

    private class InstructionLimit : DebugLib() {
        private var count = 0

        override fun onInstruction(pc: Int, line: Int) {
            if (++count > MAX_INSTRUCTIONS) {
                throw LuaError("welcome.lua exceeded $MAX_INSTRUCTIONS instructions")
            }
        }
    }

    private fun renderSimple(
        table: LuaTable,
        projectDir: File,
        drawableDir: File,
        texts: TextFiles
    ): String {
        unknown(table, simpleFields, "welcome.lua")
        val background = colorValue(text(table, "background"), "?colorSurface", "background")
        val image = text(table, "image")?.let { install(projectDir, drawableDir, it) }
        val icon = if (image != null) null else icon(table, projectDir, drawableDir)
        val label = texts.paint(table, "welcome.lua")
        return document {
            appendBackground(background)
            if (image != null) appendBitmap(image, "fill", null, null, null)
            else if (icon != null) {
                val iconSize = text(table, "icon_size") ?: "160dp"
                require(size.matches(iconSize)) { "welcome.lua icon_size must be a dp value such as 160dp" }
                val gravity = text(table, "gravity") ?: "center"
                require(validGravity(gravity)) { "welcome.lua gravity is invalid" }
                appendBitmap(icon, gravity, iconSize, iconSize, null)
            }
            if (label != null) {
                val gravity = text(table, "text_gravity") ?: if (icon != null || image != null) "center|bottom" else "center"
                require(validGravity(gravity)) { "welcome.lua text_gravity is invalid" }
                appendBitmap(label.resource, gravity, label.width, label.height, null)
            }
        }
    }

    private fun renderLayers(
        table: LuaTable,
        layers: LuaValue,
        projectDir: File,
        drawableDir: File,
        texts: TextFiles
    ): String {
        unknown(table, setOf("layers"), "welcome.lua")
        require(layers.istable()) { "welcome.lua layers must be an array" }
        val items = layers.checktable()
        require(items.length() > 0) { "welcome.lua layers must be a non-empty array" }
        return document {
            for (index in 1..items.length()) {
                val layer = items.get(index)
                require(layer.istable()) { "welcome.lua layers[$index] must be a table" }
                appendLayer(layer.checktable(), index, projectDir, drawableDir, texts)
            }
        }
    }

    private fun StringBuilder.appendLayer(
        layer: LuaTable,
        index: Int,
        projectDir: File,
        drawableDir: File,
        texts: TextFiles
    ) {
        val label = "welcome.lua layers[$index]"
        unknown(layer, layerFields, label)
        val color = text(layer, "color")
        val shape = text(layer, "shape")
        val corners = text(layer, "corners")
        val gradientStart = text(layer, "gradient_start")
        val gradientEnd = text(layer, "gradient_end")
        val gradientAngle = text(layer, "gradient_angle") ?: "0"
        val icon = text(layer, "icon")
        val image = text(layer, "image")
        val layerSize = text(layer, "size")
        val gravity = text(layer, "gravity")
        val inset = text(layer, "inset")
        val caption = text(layer, "text")
        if (caption == null && (text(layer, "text_size") != null || text(layer, "text_color") != null)) {
            throw IllegalArgumentException("$label text_size and text_color require text")
        }
        val pictures = listOfNotNull(icon, image, caption)
        val paints = listOfNotNull(color, gradientStart ?: gradientEnd)
        require(pictures.size + (if (paints.isEmpty()) 0 else 1) == 1) {
            "$label needs one of color, gradient, icon, image, or text"
        }
        if (gradientStart != null || gradientEnd != null) {
            require(gradientStart != null && gradientEnd != null) {
                "$label gradient needs gradient_start and gradient_end"
            }
            require(colorPattern.matches(gradientStart) && colorPattern.matches(gradientEnd)) {
                "$label gradient color is invalid"
            }
            require(angle.matches(gradientAngle)) { "$label gradient_angle must be a multiple of 45" }
        }
        color?.let { require(colorPattern.matches(it)) { "$label color must be #RRGGBB or #AARRGGBB" } }
        shape?.let { require(it in shapeName) { "$label shape must be rectangle or oval" } }
        corners?.let { require(size.matches(it)) { "$label corners must be a dp value" } }
        layerSize?.let { require(size.matches(it)) { "$label size must be a dp value" } }
        inset?.let { require(size.matches(it)) { "$label inset must be a dp value" } }
        gravity?.let { require(validGravity(it)) { "$label gravity is invalid" } }
        val bitmap = image?.let { install(projectDir, drawableDir, it) }
            ?: icon?.let { installIcon(projectDir, drawableDir, it) }
        val renderedText = if (caption != null) texts.paint(layer, label) else null
        val itemGravity = gravity ?: if (image != null) "fill" else "center"
        val itemSize = renderedText?.let { it.width to it.height } ?: (layerSize to layerSize)
        appendLine("    <item")
        appendAttr("width", itemSize.first)
        appendAttr("height", itemSize.second)
        appendLine("        android:gravity=\"$itemGravity\"")
        if (inset != null) listOf("left", "top", "right", "bottom").forEach { appendAttr(it, inset) }
        appendLine("        >")
        if (bitmap != null || renderedText != null) {
            appendLine("        <bitmap android:src=\"@drawable/${renderedText?.resource ?: bitmap}\" />")
        } else {
            appendLine("        <shape android:shape=\"${shape ?: "rectangle"}\">")
            if (gradientStart != null && gradientEnd != null) {
                appendLine("            <gradient android:startColor=\"$gradientStart\" android:endColor=\"$gradientEnd\" android:angle=\"$gradientAngle\" />")
            } else {
                appendLine("            <solid android:color=\"$color\" />")
            }
            if (corners != null) appendLine("            <corners android:radius=\"$corners\" />")
            appendLine("        </shape>")
        }
        appendLine("    </item>")
    }

    private fun StringBuilder.appendBackground(background: String) {
        appendLine("    <item>")
        if (background.startsWith("#")) appendLine("        <shape><solid android:color=\"$background\" /></shape>")
        else appendLine("        <color android:color=\"$background\" />")
        appendLine("    </item>")
    }

    private fun StringBuilder.appendBitmap(
        resource: String,
        gravity: String,
        width: String?,
        height: String?,
        inset: String?
    ) {
        appendLine("    <item")
        appendAttr("width", width)
        appendAttr("height", height)
        appendLine("        android:gravity=\"$gravity\"")
        if (inset != null) listOf("left", "top", "right", "bottom").forEach { appendAttr(it, inset) }
        appendLine("        >")
        appendLine("        <bitmap android:src=\"@drawable/$resource\" />")
        appendLine("    </item>")
    }

    private fun StringBuilder.appendAttr(name: String, value: String?) {
        if (value != null) appendLine("        android:$name=\"$value\"")
    }

    private fun document(body: StringBuilder.() -> Unit): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<layer-list xmlns:android="http://schemas.android.com/apk/res/android">""")
        body()
        append("</layer-list>")
        appendLine()
    }

    private fun icon(table: LuaTable, projectDir: File, drawableDir: File): String? {
        val value = table.get("icon")
        return when {
            value.isnil() -> "icon"
            value.isboolean() -> {
                require(!value.toboolean()) { "welcome.lua icon must be a file name or false" }
                null
            }
            value.isstring() -> installIcon(projectDir, drawableDir, value.tojstring())
            else -> throw IllegalArgumentException("welcome.lua icon must be a file name or false")
        }
    }

    private fun colorValue(value: String?, fallback: String, field: String): String {
        if (value == null) return fallback
        require(colorPattern.matches(value)) { "welcome.lua $field must be #RRGGBB or #AARRGGBB" }
        return value
    }

    private fun text(table: LuaTable, key: String): String? {
        val value = table.get(key)
        if (value.isnil()) return null
        require(value.isstring()) { "welcome.lua $key must be a string" }
        return value.tojstring()
    }

    private fun unknown(table: LuaTable, allowed: Set<String>, label: String) {
        var key = LuaValue.NIL
        while (true) {
            val next = table.next(key)
            if (next.isnoneornil(1)) break
            key = next.arg1()
            if (key.isstring()) {
                val name = key.tojstring()
                require(name in allowed) { "$label has unknown field: $name" }
            }
        }
    }

    private fun installIcon(projectDir: File, drawableDir: File, name: String): String {
        if (name == "icon.png" && !projectDir.resolve(name).isFile) return "icon"
        return install(projectDir, drawableDir, name)
    }

    private fun install(projectDir: File, drawableDir: File, name: String): String {
        require(fileName.matches(name)) {
            "welcome.lua image file must be a lowercase resource name such as welcome.png"
        }
        val source = projectDir.resolve(name)
        require(source.isFile && source.parentFile?.canonicalFile == projectDir.canonicalFile) {
            "welcome.lua image is missing: $name"
        }
        if (name == "icon.png") {
            val target = findIcon(drawableDir)
            if (target != null) source.copyTo(target, overwrite = true)
            return "icon"
        }
        imageSlot++
        require(imageSlot <= MAX_SLOTS) { "welcome.lua supports at most $MAX_SLOTS custom images" }
        val slot = if (imageSlot == 1) "welcome_image" else "welcome_image_$imageSlot"
        val target = drawableDir.resolve("$slot.png")
        require(target.isFile) {
            "Base APK is missing res/drawable-nodpi/$slot.png. Install the latest NeLuaJ+ and package again."
        }
        source.copyTo(target, overwrite = true)
        return slot
    }

    private fun validGravity(value: String): Boolean {
        val parts = value.split('|')
        return parts.isNotEmpty() && parts.all { it in gravityToken }
    }

    private class TextImage(val resource: String, val width: String, val height: String)

    private class TextFiles(private val drawableDir: File, private val painter: TextPainter) {
        private var index = 0

        fun paint(table: LuaTable, label: String): TextImage? {
            val value = table.get("text")
            if (value.isnil()) {
                require(
                    text(table, "text_size") == null &&
                        text(table, "text_color") == null &&
                        text(table, "text_gravity") == null
                ) { "$label text_size, text_color, and text_gravity require text" }
                return null
            }
            require(value.isstring()) { "$label text must be a string" }
            val caption = value.tojstring()
            require(caption.isNotEmpty() && caption.length <= 80 && caption.lines().size <= 6) {
                "$label text must be 1 to 80 characters and at most 6 lines"
            }
            val textSize = text(table, "text_size") ?: "24sp"
            val textColor = text(table, "text_color") ?: "#FFFFFFFF"
            require(textSizePattern.matches(textSize)) { "$label text_size must be an sp value such as 24sp" }
            require(colorPattern.matches(textColor)) { "$label text_color must be #RRGGBB or #AARRGGBB" }
            index++
            require(index <= MAX_SLOTS) { "$label supports at most $MAX_SLOTS text layers" }
            val resource = if (index == 1) "welcome_text" else "welcome_text_$index"
            val file = drawableDir.resolve("$resource.png")
            require(file.isFile) {
                "Base APK is missing res/drawable-nodpi/$resource.png. Install the latest NeLuaJ+ and package again."
            }
            val (width, height) = painter.paint(caption, textSize, textColor, file)
            return TextImage(resource, width, height)
        }
    }

    private fun slotDir(resourcesRoot: File): File {
        val slot = resourcesRoot.walkTopDown().firstOrNull { it.isFile && it.name == "welcome_image.png" }
        return slot?.parentFile ?: resourcesRoot.resolve("package_1/res/drawable-nodpi")
    }

    private fun findIcon(slotDir: File): File? {
        val direct = slotDir.resolve("icon.png")
        if (direct.isFile) return direct
        val res = slotDir.parentFile ?: return null
        val preferred = res.resolve("drawable/icon.png")
        if (preferred.isFile) return preferred
        return res.walkTopDown().firstOrNull { it.isFile && it.name == "icon.png" }
    }

    private fun welcomeFiles(resourcesRoot: File): List<File> {
        if (!resourcesRoot.isDirectory) return emptyList()
        return resourcesRoot.walkTopDown().filter { it.isFile && it.name == "welcome.xml" }.toList()
    }
}
