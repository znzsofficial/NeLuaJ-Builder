package com.nekolaska.ktx.io

import java.io.File
import java.io.IOException

/** Reads the literal subset used by project init.lua files without executing Lua. */
internal object LuaConfigParser {
    private const val MAX_CONFIG_BYTES = 1024 * 1024L
    private const val MAX_TOKENS = 100_000
    private const val MAX_TABLE_DEPTH = 16

    fun parse(file: File): LuaConfig {
        if (!file.isFile) {
            throw IOException("Configuration file does not exist: ${file.absolutePath}")
        }
        if (file.length() > MAX_CONFIG_BYTES) {
            throw IOException("Configuration file is too large: ${file.absolutePath}")
        }
        return parse(file.readText(Charsets.UTF_8))
    }

    fun parse(source: String): LuaConfig {
        if (source.toByteArray(Charsets.UTF_8).size > MAX_CONFIG_BYTES) {
            throw IllegalArgumentException("Configuration source is too large")
        }
        return Parser(tokenize(source), source.length).parse()
    }

    private enum class TokenType {
        IDENTIFIER,
        STRING,
        NUMBER,
        EQUALS,
        LEFT_BRACE,
        RIGHT_BRACE,
        LEFT_PAREN,
        RIGHT_PAREN,
        LEFT_BRACKET,
        RIGHT_BRACKET,
        COMMA,
        SEMICOLON,
        NEWLINE,
        OTHER
    }

    private data class Token(
        val type: TokenType,
        val value: String,
        val start: Int,
        val end: Int
    )

    private data class ParsedValue(
        val literal: LuaLiteral,
        val start: Int,
        val end: Int
    )

    private data class ParsedTable(
        val literal: LuaLiteral.TableValue,
        val openStart: Int,
        val closeStart: Int,
        val closeEnd: Int,
        val assignments: List<LuaAssignment>,
        val hasEntries: Boolean,
        val lastEntryEnd: Int?,
        val hasTrailingSeparator: Boolean
    )

    private class UnsupportedLiteralException(message: String) : IllegalArgumentException(message)

    private fun tokenize(source: String): List<Token> {
        val tokens = ArrayList<Token>()
        var index = 0
        fun add(type: TokenType, value: String, start: Int, end: Int) {
            if (tokens.size >= MAX_TOKENS) throw IllegalArgumentException("Too many config tokens")
            tokens += Token(type, value, start, end)
        }

        while (index < source.length) {
            val current = source[index]
            when {
                current == '\uFEFF' -> index++
                current == '\r' || current == '\n' -> {
                    val start = index++
                    if (current == '\r' && source.getOrNull(index) == '\n') index++
                    add(TokenType.NEWLINE, "\n", start, index)
                }
                current.isWhitespace() -> index++
                current == '-' && source.getOrNull(index + 1) == '-' -> {
                    index = skipComment(source, index + 2, ::add)
                }
                current == '\'' || current == '"' -> {
                    val start = index
                    val parsed = readQuotedString(source, index)
                    add(TokenType.STRING, parsed.first, start, parsed.second)
                    index = parsed.second
                }
                current == '[' && longBracketLength(source, index) > 0 -> {
                    val start = index
                    val parsed = readLongString(source, index)
                    add(TokenType.STRING, parsed.first, start, parsed.second)
                    index = parsed.second
                }
                current.isLetter() || current == '_' -> {
                    val start = index++
                    while (index < source.length &&
                        (source[index].isLetterOrDigit() || source[index] == '_')
                    ) index++
                    add(TokenType.IDENTIFIER, source.substring(start, index), start, index)
                }
                current.isDigit() ||
                    (current == '-' && source.getOrNull(index + 1)?.isDigit() == true) -> {
                    val start = index
                    index = readNumber(source, index)
                    add(TokenType.NUMBER, source.substring(start, index), start, index)
                }
                else -> {
                    val type = when (current) {
                        '=' -> TokenType.EQUALS
                        '{' -> TokenType.LEFT_BRACE
                        '}' -> TokenType.RIGHT_BRACE
                        '(' -> TokenType.LEFT_PAREN
                        ')' -> TokenType.RIGHT_PAREN
                        '[' -> TokenType.LEFT_BRACKET
                        ']' -> TokenType.RIGHT_BRACKET
                        ',' -> TokenType.COMMA
                        ';' -> TokenType.SEMICOLON
                        else -> TokenType.OTHER
                    }
                    add(type, current.toString(), index, index + 1)
                    index++
                }
            }
        }
        return tokens
    }

    private fun skipComment(
        source: String,
        start: Int,
        add: (TokenType, String, Int, Int) -> Unit
    ): Int {
        if (source.getOrNull(start) == '[' && longBracketLength(source, start) > 0) {
            val openingLength = longBracketLength(source, start)
            val close = "]${"=".repeat(openingLength - 2)}]"
            val closeIndex = source.indexOf(close, start + openingLength)
            if (closeIndex < 0) throw IllegalArgumentException("Unterminated long comment")
            val end = closeIndex + close.length
            var cursor = start
            while (cursor < end) {
                if (source[cursor] == '\r' || source[cursor] == '\n') {
                    val newlineStart = cursor++
                    if (source[newlineStart] == '\r' && source.getOrNull(cursor) == '\n') cursor++
                    add(TokenType.NEWLINE, "\n", newlineStart, cursor)
                } else cursor++
            }
            return end
        }
        val newline = source.indexOfAny(charArrayOf('\r', '\n'), start)
        return if (newline >= 0) newline else source.length
    }

    private fun longBracketLength(source: String, start: Int): Int {
        if (source.getOrNull(start) != '[') return 0
        var index = start + 1
        while (source.getOrNull(index) == '=') index++
        return if (source.getOrNull(index) == '[') index - start + 1 else 0
    }

    private fun readLongString(source: String, start: Int): Pair<String, Int> {
        val openingLength = longBracketLength(source, start)
        val close = "]${"=".repeat(openingLength - 2)}]"
        val contentStart = start + openingLength
        val closeIndex = source.indexOf(close, contentStart)
        if (closeIndex < 0) throw IllegalArgumentException("Unterminated long string")
        var value = source.substring(contentStart, closeIndex)
        value = when {
            value.startsWith("\r\n") -> value.substring(2)
            value.startsWith('\n') || value.startsWith('\r') -> value.substring(1)
            else -> value
        }.replace("\r\n", "\n").replace('\r', '\n')
        return value to closeIndex + close.length
    }

    private fun readQuotedString(source: String, start: Int): Pair<String, Int> {
        val quote = source[start]
        val result = StringBuilder()
        var index = start + 1
        while (index < source.length) {
            val current = source[index++]
            if (current == quote) return result.toString() to index
            if (current == '\r' || current == '\n') {
                throw IllegalArgumentException("Unterminated string")
            }
            if (current != '\\' || index >= source.length) {
                result.append(current)
                continue
            }
            val escaped = source[index++]
            when (escaped) {
                'a' -> result.append('\u0007')
                'b' -> result.append('\b')
                'f' -> result.append('\u000c')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'v' -> result.append('\u000b')
                '\\', '\'', '"' -> result.append(escaped)
                'z' -> while (source.getOrNull(index)?.isWhitespace() == true) index++
                '\r', '\n' -> {
                    if (escaped == '\r' && source.getOrNull(index) == '\n') index++
                    result.append('\n')
                }
                in '0'..'9' -> {
                    val digits = StringBuilder().append(escaped)
                    repeat(2) {
                        if (source.getOrNull(index) in '0'..'9') digits.append(source[index++])
                    }
                    result.append(digits.toString().toInt().toChar())
                }
                else -> result.append(escaped)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun readNumber(source: String, start: Int): Int {
        var index = start
        if (source[index] == '-') index++
        if (source.startsWith("0x", index, ignoreCase = true)) {
            index += 2
            while (source.getOrNull(index)?.let { it.isDigit() || it.lowercaseChar() in 'a'..'f' } == true) index++
            return index
        }
        while (source.getOrNull(index)?.isDigit() == true) index++
        if (source.getOrNull(index) == '.') {
            index++
            while (source.getOrNull(index)?.isDigit() == true) index++
        }
        if (source.getOrNull(index)?.lowercaseChar() == 'e') {
            index++
            if (source.getOrNull(index) == '+' || source.getOrNull(index) == '-') index++
            while (source.getOrNull(index)?.isDigit() == true) index++
        }
        return index
    }

    private class Parser(
        private val tokens: List<Token>,
        private val sourceLength: Int
    ) {
        private val values = linkedMapOf<String, LuaLiteral>()
        private val assignments = mutableListOf<LuaAssignment>()
        private val unsupportedKeys = mutableSetOf<String>()
        private var index = 0

        fun parse(): LuaConfig {
            skipSeparators()
            if (tokens.getOrNull(index)?.value == "return") return parseReturnedTable()

            var appendSafe = true
            while (index < tokens.size) {
                skipSeparators()
                if (index >= tokens.size) break
                val token = tokens[index]
                if (token.value == "local") {
                    markLocalKeys(index + 1)
                    if (!skipUnsupportedStatement(index + 1)) {
                        appendSafe = false
                        break
                    }
                    continue
                }
                if (token.type == TokenType.IDENTIFIER &&
                    tokens.getOrNull(index + 1)?.type == TokenType.EQUALS
                ) {
                    parseGlobalAssignment(token.value)
                    continue
                }
                if (!skipUnsupportedStatement(index)) {
                    appendSafe = false
                    break
                }
            }
            return LuaConfig(
                values = values,
                assignments = assignments,
                unsupportedKeys = unsupportedKeys,
                appendPosition = sourceLength.takeIf { appendSafe }
            )
        }

        private fun parseReturnedTable(): LuaConfig {
            index++
            skipNewlines()
            if (tokens.getOrNull(index)?.type != TokenType.LEFT_BRACE) {
                throw IllegalArgumentException("return must be followed by a table")
            }
            val table = parseTable(
                depth = 0,
                collectAssignments = true,
                allowUnsupportedFields = true
            )
            skipSeparators()
            if (index != tokens.size) throw IllegalArgumentException("Unexpected content after return table")
            return LuaConfig(
                values = table.literal.fields,
                assignments = table.assignments,
                unsupportedKeys = unsupportedKeys,
                returnedTable = LuaTableRange(
                    table.openStart,
                    table.closeStart,
                    table.closeEnd,
                    table.hasEntries,
                    table.lastEntryEnd,
                    table.hasTrailingSeparator
                ),
                appendPosition = table.closeStart
            )
        }

        private fun parseGlobalAssignment(key: String) {
            index += 2
            skipNewlines()
            val valueIndex = index
            val firstToken = tokens.getOrNull(valueIndex)
            val parsed = if (firstToken?.isLiteralStart() == true) parseValue(0) else null
            if (parsed != null && isGlobalTerminator()) {
                values[key] = parsed.literal
                assignments += LuaAssignment(key, parsed.start, parsed.end, false)
                if (tokens.getOrNull(index)?.type == TokenType.COMMA) index++
                return
            }
            unsupportedKeys += key
            index = valueIndex
            if (!skipUnsupportedStatement(index, allowComma = true)) index = tokens.size
        }

        private fun Token.isLiteralStart() = when (type) {
            TokenType.STRING, TokenType.NUMBER, TokenType.LEFT_BRACE -> true
            TokenType.IDENTIFIER -> value in setOf("true", "false", "nil")
            else -> false
        }

        private fun parseValue(
            depth: Int,
            allowUnsupportedFields: Boolean = false
        ): ParsedValue {
            if (depth > MAX_TABLE_DEPTH) throw IllegalArgumentException("Config nesting is too deep")
            val token = tokens.getOrNull(index) ?: throw IllegalArgumentException("Missing value")
            return when (token.type) {
                TokenType.STRING -> {
                    index++
                    ParsedValue(LuaLiteral.StringValue(token.value), token.start, token.end)
                }
                TokenType.NUMBER -> {
                    index++
                    ParsedValue(LuaLiteral.NumberValue(token.value), token.start, token.end)
                }
                TokenType.IDENTIFIER -> when (token.value) {
                    "true", "false" -> {
                        index++
                        ParsedValue(LuaLiteral.BooleanValue(token.value == "true"), token.start, token.end)
                    }
                    "nil" -> {
                        index++
                        ParsedValue(LuaLiteral.Nil, token.start, token.end)
                    }
                    else -> throw UnsupportedLiteralException("Unsupported expression")
                }
                TokenType.LEFT_BRACE -> parseTable(
                    depth,
                    collectAssignments = false,
                    allowUnsupportedFields = allowUnsupportedFields
                ).let {
                    ParsedValue(it.literal, it.openStart, it.closeEnd)
                }
                else -> throw UnsupportedLiteralException("Unsupported expression")
            }
        }

        private fun parseTable(
            depth: Int,
            collectAssignments: Boolean,
            allowUnsupportedFields: Boolean = false
        ): ParsedTable {
            if (depth >= MAX_TABLE_DEPTH) throw IllegalArgumentException("Config nesting is too deep")
            val open = tokens[index++]
            val items = mutableListOf<LuaLiteral>()
            val fields = linkedMapOf<String, LuaLiteral>()
            val fieldAssignments = mutableListOf<LuaAssignment>()
            var hasEntries = false
            var lastEntryEnd: Int? = null
            var hasTrailingSeparator = false
            skipNewlines()
            while (true) {
                val token = tokens.getOrNull(index) ?: throw IllegalArgumentException("Unterminated table")
                if (token.type == TokenType.RIGHT_BRACE) {
                    index++
                    val assignmentsWithContainer = fieldAssignments.map {
                        it.copy(containerCloseStart = token.start)
                    }
                    return ParsedTable(
                        LuaLiteral.TableValue(items, fields),
                        open.start,
                        token.start,
                        token.end,
                        assignmentsWithContainer,
                        hasEntries,
                        lastEntryEnd,
                        hasTrailingSeparator
                    )
                }

                hasTrailingSeparator = false
                if (token.type == TokenType.IDENTIFIER &&
                    tokens.getOrNull(index + 1)?.type == TokenType.EQUALS
                ) {
                    val key = token.value
                    index += 2
                    skipNewlines()
                    val canSkipUnsupported = allowUnsupportedFields && key != "user_permission"
                    try {
                        val parsed = parseValue(depth + 1, canSkipUnsupported)
                        skipNewlines()
                        if (!isTableEntryTerminator()) {
                            if (!canSkipUnsupported) {
                                throw IllegalArgumentException("Table fields require a separator")
                            }
                            if (collectAssignments) unsupportedKeys += key
                            lastEntryEnd = skipUnsupportedTableValue()
                        } else {
                            fields[key] = parsed.literal
                            lastEntryEnd = parsed.end
                            if (collectAssignments) {
                                fieldAssignments += LuaAssignment(key, parsed.start, parsed.end, true)
                            }
                        }
                    } catch (_: UnsupportedLiteralException) {
                        if (!canSkipUnsupported) throw IllegalArgumentException("Unsupported expression")
                        if (collectAssignments) unsupportedKeys += key
                        lastEntryEnd = skipUnsupportedTableValue()
                    }
                } else {
                    val parsed = parseValue(depth + 1)
                    items += parsed.literal
                    lastEntryEnd = parsed.end
                }
                hasEntries = true

                skipNewlines()
                when (tokens.getOrNull(index)?.type) {
                    TokenType.COMMA, TokenType.SEMICOLON -> {
                        hasTrailingSeparator = true
                        index++
                        skipNewlines()
                    }
                    TokenType.RIGHT_BRACE -> Unit
                    else -> throw IllegalArgumentException("Table fields require a separator")
                }
            }
        }

        private fun isGlobalTerminator(): Boolean = when (tokens.getOrNull(index)?.type) {
            null, TokenType.NEWLINE, TokenType.SEMICOLON, TokenType.COMMA -> true
            else -> false
        }

        private fun isTableEntryTerminator(): Boolean = when (tokens.getOrNull(index)?.type) {
            TokenType.COMMA, TokenType.SEMICOLON, TokenType.RIGHT_BRACE -> true
            else -> false
        }

        private fun skipUnsupportedTableValue(): Int {
            var parens = 0
            var braces = 0
            var brackets = 0
            var lastEnd: Int? = null
            while (index < tokens.size) {
                val token = tokens[index]
                when (token.type) {
                    TokenType.LEFT_PAREN -> parens++
                    TokenType.RIGHT_PAREN -> if (parens > 0) parens-- else {
                        throw IllegalArgumentException("Unbalanced table expression")
                    }
                    TokenType.LEFT_BRACE -> braces++
                    TokenType.RIGHT_BRACE -> {
                        if (braces == 0 && parens == 0 && brackets == 0) {
                            return lastEnd ?: throw IllegalArgumentException("Missing table expression")
                        }
                        if (braces > 0) braces-- else throw IllegalArgumentException("Unbalanced table expression")
                    }
                    TokenType.LEFT_BRACKET -> brackets++
                    TokenType.RIGHT_BRACKET -> if (brackets > 0) brackets-- else {
                        throw IllegalArgumentException("Unbalanced table expression")
                    }
                    TokenType.COMMA, TokenType.SEMICOLON -> if (
                        parens == 0 && braces == 0 && brackets == 0
                    ) return lastEnd ?: throw IllegalArgumentException("Missing table expression")
                    else -> Unit
                }
                if (token.type != TokenType.NEWLINE) lastEnd = token.end
                index++
            }
            throw IllegalArgumentException("Unterminated table expression")
        }

        private fun markLocalKeys(start: Int) {
            if (tokens.getOrNull(start)?.value == "function") {
                tokens.getOrNull(start + 1)
                    ?.takeIf { it.type == TokenType.IDENTIFIER }
                    ?.let { unsupportedKeys += it.value }
                return
            }
            var cursor = start
            var expectsKey = true
            while (cursor < tokens.size) {
                val token = tokens[cursor]
                if (token.type in setOf(TokenType.EQUALS, TokenType.NEWLINE, TokenType.SEMICOLON)) break
                if (expectsKey && token.type == TokenType.IDENTIFIER) {
                    unsupportedKeys += token.value
                    expectsKey = false
                } else if (token.type == TokenType.COMMA) {
                    expectsKey = true
                }
                cursor++
            }
        }

        private fun skipUnsupportedStatement(start: Int, allowComma: Boolean = false): Boolean {
            index = start
            var parens = 0
            var braces = 0
            var brackets = 0
            while (index < tokens.size) {
                val token = tokens[index]
                if (token.type == TokenType.IDENTIFIER && token.value in BLOCK_KEYWORDS) {
                    return false
                }
                when (token.type) {
                    TokenType.LEFT_PAREN -> parens++
                    TokenType.RIGHT_PAREN -> if (--parens < 0) return false
                    TokenType.LEFT_BRACE -> braces++
                    TokenType.RIGHT_BRACE -> if (--braces < 0) return false
                    TokenType.LEFT_BRACKET -> brackets++
                    TokenType.RIGHT_BRACKET -> if (--brackets < 0) return false
                    TokenType.NEWLINE, TokenType.SEMICOLON -> if (
                        parens == 0 && braces == 0 && brackets == 0
                    ) {
                        index++
                        return true
                    }
                    TokenType.COMMA -> if (
                        allowComma && parens == 0 && braces == 0 && brackets == 0
                    ) {
                        index++
                        return true
                    }
                    else -> Unit
                }
                index++
            }
            return parens == 0 && braces == 0 && brackets == 0
        }

        private fun skipSeparators() {
            while (tokens.getOrNull(index)?.type in setOf(TokenType.NEWLINE, TokenType.SEMICOLON)) index++
        }

        private fun skipNewlines() {
            while (tokens.getOrNull(index)?.type == TokenType.NEWLINE) index++
        }

        private companion object {
            val BLOCK_KEYWORDS = setOf(
                "function", "if", "for", "while", "repeat", "do", "return", "break", "goto"
            )
        }
    }
}
