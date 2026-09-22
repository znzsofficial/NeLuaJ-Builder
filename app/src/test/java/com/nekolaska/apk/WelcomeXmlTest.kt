package com.nekolaska.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WelcomeXmlTest {
    @Test
    fun defaultSplashUsesTemplateIcon() {
        val xml = render("return {}")

        assertTrue(xml.contains("android:color=\"?colorSurface\""))
        assertTrue(xml.contains("android:src=\"@drawable/icon\""))
        assertTrue(xml.contains("android:width=\"160dp\""))
    }

    @Test
    fun missingDefaultIconUsesPackagedDrawable() {
        val xml = render("""return { icon = "icon.png", background = "#112233" }""")

        assertTrue(xml.contains("@drawable/icon"))
        assertTrue(xml.contains("#112233"))
    }

    @Test
    fun backgroundWithoutIcon() {
        val xml = render(
            """
            return {
              background = "#112233",
              icon = false,
            }
            """.trimIndent()
        )

        assertTrue(xml.contains("android:color=\"#112233\""))
        assertFalse(xml.contains("@drawable/icon"))
    }

    @Test
    fun fullImageReplacesIcon() {
        val project = tempDir()
        val drawable = resDrawable()
        project.resolve("welcome.png").writeBytes(byteArrayOf(1, 2, 3))
        val xml = WelcomeXml.render(
            """
            return {
              background = "#FF000000",
              image = "welcome.png",
              icon = "ignored.png",
            }
            """.trimIndent(),
            project,
            drawable
        )

        assertTrue(drawable.resolve("welcome_image.png").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(drawable.resolve("ignored.png").exists())
        assertTrue(xml.contains("android:gravity=\"fill\""))
        assertTrue(xml.contains("@drawable/welcome_image"))
        assertFalse(xml.contains("@drawable/ignored"))
    }

    @Test
    fun applyOverwritesDecodedWelcomeXml() {
        val project = tempDir()
        val resources = tempDir()
        val welcome = resources.resolve("package_1/res/drawable-v23/welcome.xml")
        welcome.parentFile!!.mkdirs()
        welcome.writeText("old")
        project.resolve("welcome.lua").writeText("return { icon = false, background = \"#ABCDEF\" }\n")

        WelcomeXml.apply(project, resources)

        val written = welcome.readText()
        assertTrue(written.contains("#ABCDEF"))
        assertFalse(written.contains("@drawable/icon"))
    }

    @Test
    fun computedColorAndLayers() {
        val project = tempDir()
        project.resolve("icon.png").writeBytes(byteArrayOf(1))
        val drawable = tempDir()
        drawable.resolve("icon.png").writeBytes(byteArrayOf(0))
        val xml = WelcomeXml.render(
            """
            local color = string.format("#%06X", 0x112233)
            return {
              layers = {
                { color = color },
                { shape = "oval", gradient_start = "#00112233", gradient_end = "#FF112233", gradient_angle = "90", size = "240dp" },
                { icon = "icon.png", size = "72dp", gravity = "center|bottom", inset = "24dp" },
              }
            }
            """.trimIndent(),
            project,
            drawable
        )

        assertTrue(xml.contains("#112233"))
        assertTrue(xml.contains("android:shape=\"oval\""))
        assertTrue(xml.contains("android:angle=\"90\""))
        assertTrue(xml.contains("android:left=\"24dp\""))
        assertTrue(project.resolve("icon.png").isFile)
    }

    @Test
    fun textBecomesBitmapLayer() {
        val drawable = resDrawable()
        val xml = WelcomeXml.render(
            """
            return {
              background = "#111111",
              icon = false,
              text = "Hi",
              text_size = "20sp",
              text_color = "#FFFFFFFF",
            }
            """.trimIndent(),
            tempDir(),
            drawable
        ) { _, size, color, file ->
            assertEquals("20sp", size)
            assertEquals("#FFFFFFFF", color)
            file.writeBytes(byteArrayOf(1))
            "40dp" to "16dp"
        }

        assertTrue(xml.contains("@drawable/welcome_text"))
        assertTrue(xml.contains("android:width=\"40dp\""))
        assertTrue(xml.contains("android:height=\"16dp\""))
        assertTrue(drawable.resolve("welcome_text.png").readBytes().contentEquals(byteArrayOf(1)))
    }

    @Test(timeout = 3000)
    fun stopsInfiniteLoop() {
        assertRejected("while true do end\nreturn {}")
    }

    @Test
    fun customImageOverwritesNodpiSlot() {
        val project = tempDir()
        val resources = tempDir()
        val nodpi = resources.resolve("package_1/res/drawable-nodpi")
        val drawable = resources.resolve("package_1/res/drawable")
        nodpi.mkdirs()
        drawable.mkdirs()
        nodpi.resolve("welcome_image.png").writeBytes(byteArrayOf(0))
        drawable.resolve("icon.png").writeBytes(byteArrayOf(9))
        project.resolve("welcome.png").writeBytes(byteArrayOf(4, 5))
        project.resolve("welcome.lua").writeText("return { image = \"welcome.png\" }\n")

        WelcomeXml.apply(project, resources)

        assertTrue(nodpi.resolve("welcome_image.png").readBytes().contentEquals(byteArrayOf(4, 5)))
        assertFalse(drawable.resolve("welcome_image.png").exists())
        assertTrue(drawable.resolve("icon.png").readBytes().contentEquals(byteArrayOf(9)))
    }

    @Test
    fun keepsPublicXmlUntouched() {
        val project = tempDir()
        val drawable = resDrawable()
        val publicXml = drawable.parentFile!!.resolve("values/public.xml")
        val before = publicXml.readText()
        project.resolve("welcome.png").writeBytes(byteArrayOf(7))
        WelcomeXml.render(
            """return { image = "welcome.png", background = "#112233" }""",
            project,
            drawable
        )
        assertEquals(before, publicXml.readText())
    }

    @Test
    fun sandboxRejectsFilesystemAccess() {
        assertRejected("os.execute(\"never\")\nreturn {}")
        assertRejected("loadfile(\"welcome.lua\")\nreturn {}")
    }

    @Test
    fun rejectsUnsafeImageName() {
        assertRejected("return { image = \"../icon.png\" }")
        assertRejected("return { background = \"#fff\" }")
        assertRejected("return { icon_size = \"160px\" }")
        assertRejected("return { title = \"Hello\" }")
    }

    private fun render(source: String): String {
        return WelcomeXml.render(source, tempDir(), tempDir())
    }

    private fun assertRejected(source: String) {
        try {
            render(source)
            throw AssertionError("Expected rejection for $source")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun resDrawable(): File {
        val drawable = tempDir().resolve("drawable")
        drawable.mkdirs()
        val publicXml = drawable.parentFile!!.resolve("values/public.xml")
        publicXml.parentFile!!.mkdirs()
        publicXml.writeText("""<resources><public type="drawable" name="icon" id="0x7f080010" /></resources>""")
        drawable.resolve("welcome_image.png").writeBytes(byteArrayOf(0))
        drawable.resolve("welcome_text.png").writeBytes(byteArrayOf(0))
        return drawable
    }

    private fun tempDir(): File = File.createTempFile("welcome", "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }
}
