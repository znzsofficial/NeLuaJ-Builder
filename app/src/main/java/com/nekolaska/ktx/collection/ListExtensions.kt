package com.nekolaska.ktx.collection

import com.nekolaska.ktx.value.toLuaValue
import org.luaj.LuaTable

fun List<*>.toTable() = LuaTable().apply {
    forEach {
        add(it.toLuaValue())
    }
}