package com.nekolaska.apk

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class MySignerTest {
    @Test
    fun exportsTheCompleteSigningKey() {
        val key = File.createTempFile("signing-key", ".jks")
        try {
            val expected = ByteArray(4096) { (it % 251).toByte() }
            key.writeBytes(expected)
            val output = ByteArrayOutputStream()

            copySigningKey(key, output)

            assertArrayEquals(expected, output.toByteArray())
        } finally {
            key.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnEmptySigningKey() {
        val key = File.createTempFile("empty-signing-key", ".jks")
        try {
            copySigningKey(key, ByteArrayOutputStream())
        } finally {
            key.delete()
        }
    }
}
