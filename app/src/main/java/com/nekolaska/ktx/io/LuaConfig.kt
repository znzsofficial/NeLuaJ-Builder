package com.nekolaska.ktx.io

internal data class LuaConfig(
    private val values: Map<String, LuaLiteral>,
    val assignments: List<LuaAssignment> = emptyList(),
    val unsupportedKeys: Set<String> = emptySet(),
    val returnedTable: LuaTableRange? = null,
    val appendPosition: Int? = null
) {
    fun string(vararg keys: String): String? = first(*keys)?.asString()

    fun int(vararg keys: String): Int? = first(*keys)?.asInt()

    fun boolean(vararg keys: String): Boolean? =
        (first(*keys) as? LuaLiteral.BooleanValue)?.value

    fun stringList(key: String): List<String>? =
        (values[key] as? LuaLiteral.TableValue)?.let { table ->
            if (table.fields.isNotEmpty()) return@let null
            table.items.map { (it as? LuaLiteral.StringValue)?.value ?: return@let null }
        }

    internal fun strictStringList(key: String): List<String>? {
        val literal = values[key] ?: return null
        if (literal is LuaLiteral.Nil) return null
        val table = literal as? LuaLiteral.TableValue
            ?: throw IllegalArgumentException("$key must be a table of strings")
        if (table.fields.isNotEmpty() || table.items.any { it !is LuaLiteral.StringValue }) {
            throw IllegalArgumentException("$key must be an array of strings")
        }
        return table.items.map { (it as LuaLiteral.StringValue).value }
    }

    operator fun contains(key: String) = values[key]?.let { it !is LuaLiteral.Nil } == true

    internal fun value(key: String): LuaLiteral? = values[key]

    internal fun hasValue(vararg keys: String): Boolean =
        keys.any { values[it]?.let { literal -> literal !is LuaLiteral.Nil } == true }

    private fun first(vararg keys: String): LuaLiteral? =
        keys.asSequence().mapNotNull(values::get).firstOrNull { it !is LuaLiteral.Nil }
}

internal data class LuaAssignment(
    val key: String,
    val valueStart: Int,
    val valueEnd: Int,
    val returnedField: Boolean,
    val containerCloseStart: Int? = null
)

internal data class LuaTableRange(
    val openStart: Int,
    val closeStart: Int,
    val closeEnd: Int,
    val hasEntries: Boolean,
    val lastEntryEnd: Int?,
    val hasTrailingSeparator: Boolean
)

internal sealed interface LuaLiteral {
    data class StringValue(val value: String) : LuaLiteral
    data class NumberValue(val raw: String) : LuaLiteral
    data class BooleanValue(val value: Boolean) : LuaLiteral
    data class TableValue(
        val items: List<LuaLiteral>,
        val fields: Map<String, LuaLiteral>
    ) : LuaLiteral

    data object Nil : LuaLiteral

    fun asString(): String? = when (this) {
        is StringValue -> value
        is NumberValue -> raw
        else -> null
    }

    fun asInt(): Int? = when (this) {
        is StringValue -> value.toIntOrNull()
        is NumberValue -> if (raw.startsWith("0x", ignoreCase = true)) {
            raw.substring(2).toIntOrNull(16)
        } else {
            raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
        }
        else -> null
    }
}
