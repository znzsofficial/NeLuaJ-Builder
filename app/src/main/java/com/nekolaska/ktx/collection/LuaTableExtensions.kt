package com.nekolaska.ktx.collection

import org.luaj.LuaTable

fun LuaTable.stringList(): List<String> {
    return values().map { it.toString() }
}
