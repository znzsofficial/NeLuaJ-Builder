package com.nekolaska.ktx.collection

fun Map<String, String>.contentToString() = entries.joinToString(prefix = "{", postfix = "}") {
    "${it.key}=${it.value}"
}