package com.nekolaska.ktx.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LuaConfigParserTest {
    @Test
    fun parsesAssignmentsWithoutExecutingCode() {
        val config = parse(
            """
            --[[ package_name = "ignored.comment" ]]
            app_name = "Demo\nApp"
            package_name = 'com.example.demo'
            ver_code = "7"
            debug_mode = true
            user_permission = {
              "INTERNET",
              'CAMERA',
            }
            dangerous = os.execute("never run")
            """.trimIndent()
        )

        assertEquals("Demo\nApp", config.string("app_name"))
        assertEquals("com.example.demo", config.string("package_name"))
        assertEquals(7, config.int("ver_code"))
        assertEquals(true, config.boolean("debug_mode"))
        assertEquals(listOf("INTERNET", "CAMERA"), config.stringList("user_permission"))
        assertTrue("dangerous" !in config)
    }

    @Test
    fun parsesReturnedTable() {
        val config = parse(
            """
            return {
              app_name = "Returned",
              target_sdk = 35,
              user_permission = { "INTERNET" },
            }
            """.trimIndent()
        )

        assertEquals("Returned", config.string("app_name"))
        assertEquals(35, config.int("target_sdk"))
        assertEquals(listOf("INTERNET"), config.stringList("user_permission"))
    }

    @Test
    fun rejectsReturnedExpression() {
        try {
            LuaConfigParser.parse("return make_config()")
            throw AssertionError("Expected a returned expression to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: the builder only accepts a literal returned table.
        }
    }

    @Test
    fun marksLocalDeclarationsAsUnsupported() {
        val config = LuaConfigParser.parse("local app_name = \"not-a-global-config\"\n")

        assertTrue("app_name" in config.unsupportedKeys)
        assertEquals(null, config.string("app_name"))
    }

    @Test
    fun parsesCommaDelimitedGlobalAssignments() {
        val config = LuaConfigParser.parse(
            "app_name = \"Demo\", package_name = \"com.example.demo\", ver_code = 9,"
        )

        assertEquals("Demo", config.string("app_name"))
        assertEquals("com.example.demo", config.string("package_name"))
        assertEquals(9, config.int("ver_code"))
    }

    @Test
    fun preservesComputedCustomReturnedFields() {
        val config = LuaConfigParser.parse(
            "return { app_name = \"Demo\", custom = make_value(1, 2), target_sdk = 35 }"
        )

        assertEquals("Demo", config.string("app_name"))
        assertEquals(35, config.int("target_sdk"))
        assertTrue("custom" in config.unsupportedKeys)
    }

    @Test
    fun rejectsMalformedPermissionArrays() {
        val config = LuaConfigParser.parse("user_permission = { \"INTERNET\", false }")

        try {
            config.strictStringList("user_permission")
            throw AssertionError("Expected malformed permissions to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: every permission must be a string literal.
        }
    }

    @Test
    fun normalizesLuaLongStringNewlines() {
        val config = LuaConfigParser.parse("app_name = [[\r\nDemo\rName\r\n]]")

        assertEquals("Demo\nName\n", config.string("app_name"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnterminatedTables() {
        parse("user_permission = { \"INTERNET\"")
    }

    private fun parse(source: String) = File.createTempFile("builder-init", ".lua").let { file ->
        try {
            file.writeText(source, Charsets.UTF_8)
            LuaConfigParser.parse(file)
        } finally {
            file.delete()
        }
    }
}
