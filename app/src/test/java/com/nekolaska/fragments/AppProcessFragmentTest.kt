package com.nekolaska.fragments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessFragmentTest {
    @Test
    fun createsFilesystemSafeUtf8ApkName() {
        val name = createApkName("应用".repeat(100), "版本")

        assertTrue(name.endsWith(".apk"))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 184)
        assertEquals(name, createApkName("应用".repeat(100), "版本"))
    }

    @Test
    fun replacesInvalidFilenameCharacters() {
        assertEquals("My_App_1.0.apk", createApkName("My/App", "1.0"))
    }
}
