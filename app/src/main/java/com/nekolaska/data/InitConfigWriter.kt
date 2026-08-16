package com.nekolaska.data

import com.nekolaska.ktx.io.LuaAssignment
import com.nekolaska.ktx.io.LuaConfigParser

/** Rewrites only parsed literal ranges and preserves all surrounding Lua source. */
internal object InitConfigWriter {
    private data class Field(
        val canonicalKey: String,
        val aliases: Set<String>,
        val value: (InitConfig, String) -> String
    )

    private data class Edit(val start: Int, val end: Int, val replacement: String)

    private val fields = listOf(
        Field("app_name", setOf("app_name", "appname")) { config, _ -> quote(config.appName) },
        Field("package_name", setOf("package_name")) { config, _ -> quote(config.packageName) },
        Field("ver_name", setOf("ver_name", "version_name")) { config, _ -> quote(config.versionName) },
        Field("ver_code", setOf("ver_code", "version_code")) { config, _ -> "\"${config.versionCode}\"" },
        Field("min_sdk", setOf("min_sdk")) { config, _ -> "\"${config.minSDK}\"" },
        Field("target_sdk", setOf("target_sdk")) { config, _ -> "\"${config.targetSDK}\"" },
        Field("debug_mode", setOf("debug_mode", "debugmode")) { config, _ -> config.debuggable.toString() },
        Field("NeLuaJ_Theme", setOf("NeLuaJ_Theme")) { config, _ -> quote(config.theme) },
        Field("user_permission", setOf("user_permission")) { config, newline ->
            formatPermissions(config.userPermission, newline)
        }
    )
    private val managedKeys = fields.flatMapTo(mutableSetOf()) { it.aliases }

    fun patch(raw: String, config: InitConfig): String {
        if (raw.isBlank()) return build(config)

        val document = LuaConfigParser.parse(raw)
        val unsafeKeys = document.unsupportedKeys.intersect(managedKeys)
        require(unsafeKeys.isEmpty()) {
            "Cannot safely update computed config fields: ${unsafeKeys.joinToString()}"
        }

        val edits = mutableListOf<Edit>()
        val missing = mutableListOf<Field>()
        val newline = newlineOf(raw)
        fields.forEach { field ->
            val matches = document.assignments.filter { it.key in field.aliases }
            if (matches.isEmpty()) {
                missing += field
            } else {
                matches.forEach { assignment ->
                    edits += Edit(
                        assignment.valueStart,
                        assignment.valueEnd,
                        render(field, config, raw, assignment, newline)
                    )
                }
            }
        }
        if (document.string("theme")?.startsWith("Theme_NeLuaJ_") == true) {
            document.assignments.filter { it.key == "theme" }.forEach { assignment ->
                edits += Edit(assignment.valueStart, assignment.valueEnd, quote(config.theme))
            }
        }

        if (missing.isNotEmpty()) {
            val appendPosition = document.appendPosition
                ?: throw IllegalArgumentException("init.lua has no safe location for missing fields")
            val table = document.returnedTable
            if (table == null) {
                edits += Edit(
                    appendPosition,
                    appendPosition,
                    formatGlobalAppend(raw, missing, config, newline)
                )
            } else {
                var insertion = formatTableAppend(
                    raw,
                    document.assignments,
                    missing,
                    config,
                    newline,
                    table
                )
                if (table.hasEntries && !table.hasTrailingSeparator) {
                    val lastEnd = table.lastEntryEnd
                        ?: throw IllegalArgumentException("Cannot locate the last table field")
                    if (lastEnd == insertion.first) {
                        insertion = insertion.first to ",${insertion.second}"
                    } else {
                        edits += Edit(lastEnd, lastEnd, ",")
                    }
                }
                edits += Edit(insertion.first, insertion.first, insertion.second)
            }
        }

        val updated = edits
            .sortedWith(compareByDescending<Edit> { it.start }.thenByDescending { it.end })
            .fold(raw) { text, edit -> text.replaceRange(edit.start, edit.end, edit.replacement) }
        verify(updated, config)
        return updated
    }

    private fun formatGlobalAppend(
        raw: String,
        missing: List<Field>,
        config: InitConfig,
        newline: String
    ): String {
        val prefix = if (raw.endsWith("\n") || raw.endsWith("\r")) "" else newline
        return prefix + missing.joinToString(newline, postfix = newline) {
            "${it.canonicalKey} = ${value(it, config, newline)}"
        }
    }

    private fun formatTableAppend(
        raw: String,
        assignments: List<LuaAssignment>,
        missing: List<Field>,
        config: InitConfig,
        newline: String,
        table: com.nekolaska.ktx.io.LuaTableRange
    ): Pair<Int, String> {
        val closeLineStart = raw.lastIndexOfAny(charArrayOf('\r', '\n'), table.closeStart - 1) + 1
        val multiline = raw.substring(table.openStart, table.closeStart)
            .any { it == '\r' || it == '\n' }

        if (multiline) {
            val closePrefix = raw.substring(closeLineStart, table.closeStart)
            if (!closePrefix.isBlank()) {
                val text = missing.joinToString(" ", prefix = " ", postfix = " ") {
                    "${it.canonicalKey} = ${value(it, config, newline)},"
                }
                return table.closeStart to text
            }
            val closeIndent = closePrefix.takeWhile { it == ' ' || it == '\t' }
            val indent = assignments.firstOrNull()?.let { leadingIndent(raw, it.valueStart) }
                ?.takeIf { it.isNotEmpty() }
                ?: closeIndent + "  "
            val text = missing.joinToString(newline, postfix = newline) {
                "$indent${it.canonicalKey} = ${value(it, config, newline, indent)},"
            }
            return closeLineStart to text
        }

        val text = missing.joinToString(" ", prefix = " ", postfix = " ") {
            "${it.canonicalKey} = ${value(it, config, newline)},"
        }
        return table.closeStart to text
    }

    private fun render(
        field: Field,
        config: InitConfig,
        raw: String,
        assignment: LuaAssignment,
        newline: String
    ): String {
        val indent = leadingIndent(raw, assignment.valueStart)
        return value(field, config, newline, indent)
    }

    private fun value(
        field: Field,
        config: InitConfig,
        newline: String,
        indent: String = ""
    ): String = if (field.canonicalKey == "user_permission") {
        formatPermissions(config.userPermission, newline, indent)
    } else {
        field.value(config, newline)
    }

    private fun leadingIndent(raw: String, position: Int): String {
        val lineStart = raw.lastIndexOfAny(charArrayOf('\r', '\n'), position - 1) + 1
        return raw.substring(lineStart, position).takeWhile { it == ' ' || it == '\t' }
    }

    private fun verify(updated: String, config: InitConfig) {
        val parsed = LuaConfigParser.parse(updated)
        require(parsed.string("app_name", "appname") == config.appName)
        require(parsed.string("package_name") == config.packageName)
        require(parsed.string("ver_name", "version_name") == config.versionName)
        require(parsed.int("ver_code", "version_code") == config.versionCode)
        require(parsed.int("min_sdk") == config.minSDK)
        require(parsed.int("target_sdk") == config.targetSDK)
        require(parsed.boolean("debug_mode", "debugmode") == config.debuggable)
        require(parsed.string("NeLuaJ_Theme") == config.theme)
        require(parsed.stringList("user_permission") == config.userPermission.distinct())
    }

    private fun build(config: InitConfig): String = fields.joinToString("\n", postfix = "\n") {
        "${it.canonicalKey} = ${value(it, config, "\n")}"
    }

    private fun newlineOf(raw: String) = if (raw.contains("\r\n")) "\r\n" else "\n"

    private fun formatPermissions(
        permissions: List<String>,
        newline: String,
        indent: String = ""
    ): String = buildString {
        append('{')
        if (permissions.isNotEmpty()) {
            append(newline)
            permissions.distinct().forEach {
                append(indent).append("  ").append(quote(it)).append(',').append(newline)
            }
            append(indent)
        }
        append('}')
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
