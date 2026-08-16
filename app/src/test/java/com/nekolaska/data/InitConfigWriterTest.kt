package com.nekolaska.data

import com.nekolaska.ktx.io.LuaConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InitConfigWriterTest {
    @Test
    fun updatesManagedFieldsAndPreservesProjectCode() {
        val original = """
            -- project metadata
            app_name = "Old" -- keep this note
            package_name = "com.example.old"
            custom_setting = make_custom_value()
            user_permission = {
              "INTERNET",
            }
        """.trimIndent() + "\n"
        val config = InitConfig(
            appName = "New \"Name\"",
            packageName = "com.example.newapp",
            versionName = "2.0",
            versionCode = 2,
            debuggable = false,
            targetSDK = 35,
            minSDK = 26,
            theme = "Theme_NeLuaJ_Compat",
            userPermission = listOf("CAMERA", "CAMERA")
        )

        val updated = InitConfigWriter.patch(original, config)

        assertTrue(updated.contains("-- project metadata"))
        assertTrue(updated.contains("-- keep this note"))
        assertTrue(updated.contains("custom_setting = make_custom_value()"))
        assertEquals(1, Regex("(?m)^app_name\\s*=").findAll(updated).count())
        assertFalse(updated.contains("com.example.old"))

        val parsed = parse(updated)
        assertEquals("New \"Name\"", parsed.string("app_name"))
        assertEquals("com.example.newapp", parsed.string("package_name"))
        assertEquals(2, parsed.int("ver_code"))
        assertEquals(listOf("CAMERA"), parsed.stringList("user_permission"))
    }

    @Test
    fun updatesReturnedTableAndAppendsMissingFieldsInsideIt() {
        val original = """
            return {
              app_name = "Old",
              custom_setting = 7,
            }
        """.trimIndent() + "\n"

        val updated = InitConfigWriter.patch(original, config())

        assertTrue(updated.startsWith("return {"))
        assertTrue(updated.contains("custom_setting = 7"))
        assertFalse(Regex("(?m)^package_name\\s*=").containsMatchIn(updated))
        val parsed = parse(updated)
        assertEquals("New", parsed.string("app_name"))
        assertEquals("com.example.newapp", parsed.string("package_name"))
        assertEquals(listOf("CAMERA"), parsed.stringList("user_permission"))
    }

    @Test
    fun appendsToInlineReturnedTableWithoutTrailingComma() {
        val updated = InitConfigWriter.patch("return { app_name = \"Old\" }", config())

        val parsed = parse(updated)
        assertEquals("New", parsed.string("app_name"))
        assertEquals("com.example.newapp", parsed.string("package_name"))
        assertEquals(listOf("CAMERA"), parsed.stringList("user_permission"))
    }

    @Test
    fun appendsAfterComputedCustomReturnedField() {
        val original = "return { custom = make_value(1, 2) }"

        val updated = InitConfigWriter.patch(original, config())

        assertTrue(updated.contains("custom = make_value(1, 2)"))
        assertEquals("New", parse(updated).string("app_name"))
    }

    @Test
    fun updatesValueWhoseTableStartsOnTheNextLine() {
        val original = """
            app_name = "Old"
            package_name = "com.example.old"
            ver_name = "1.0"
            ver_code = 1
            debug_mode = true
            target_sdk = 35
            min_sdk = 26
            NeLuaJ_Theme = "Theme_NeLuaJ_Compat"
            user_permission =
            {
              "INTERNET",
            }
        """.trimIndent() + "\n"

        val updated = InitConfigWriter.patch(original, config())

        assertTrue(updated.contains("user_permission =\n{"))
        assertEquals(listOf("CAMERA"), parse(updated).stringList("user_permission"))
    }

    @Test
    fun rejectsComputedManagedValues() {
        val original = """
            app_name = get_app_name()
            package_name = "com.example.old"
        """.trimIndent()

        try {
            InitConfigWriter.patch(original, config())
            throw AssertionError("Expected computed app_name to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: updating a computed field would require executing Lua.
        }
    }

    @Test
    fun preservesAssignmentsInsideFunctionBodies() {
        val original = """
            app_name = "Old"
            package_name = "com.example.old"
            ver_name = "1.0"
            ver_code = 1
            debug_mode = true
            target_sdk = 35
            min_sdk = 26
            NeLuaJ_Theme = "Theme_NeLuaJ_Compat"
            user_permission = { "INTERNET" }

            function configure()
              app_name = "function-local-lookalike"
            end
        """.trimIndent() + "\n"

        val updated = InitConfigWriter.patch(original, config())

        assertTrue(updated.contains("app_name = \"function-local-lookalike\""))
        assertEquals(2, Regex("(?m)^\\s*app_name\\s*=").findAll(updated).count())
        assertEquals("New", parse(updated).string("app_name"))
    }

    @Test
    fun synchronizesNeLuaJLegacyThemeAndPreservesPlatformTheme() {
        val legacy = "theme = \"Theme_NeLuaJ_Old\"\n"
        val platform = "theme = \"android.R.style.Theme_Material\"\n"

        val updatedLegacy = InitConfigWriter.patch(legacy, config())
        val updatedPlatform = InitConfigWriter.patch(platform, config())

        assertTrue(updatedLegacy.contains("theme = \"Theme_NeLuaJ_Compat\""))
        assertTrue(updatedPlatform.contains("theme = \"android.R.style.Theme_Material\""))
        assertEquals("Theme_NeLuaJ_Compat", parse(updatedPlatform).string("NeLuaJ_Theme"))
    }

    private fun config() = InitConfig(
        appName = "New",
        packageName = "com.example.newapp",
        versionName = "2.0",
        versionCode = 2,
        debuggable = false,
        targetSDK = 35,
        minSDK = 26,
        theme = "Theme_NeLuaJ_Compat",
        userPermission = listOf("CAMERA", "CAMERA")
    )

    private fun parse(source: String) = File.createTempFile("builder-init", ".lua").let { file ->
        try {
            file.writeText(source, Charsets.UTF_8)
            LuaConfigParser.parse(file)
        } finally {
            file.delete()
        }
    }
}
