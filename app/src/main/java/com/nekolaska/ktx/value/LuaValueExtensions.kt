package com.nekolaska.ktx.value

import kotlinx.coroutines.suspendCancellableCoroutine
import org.luaj.Globals
import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.jse.CoerceJavaToLua
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun Globals.require(value: LuaValue): LuaValue = p.y.call(value)

@Suppress("NOTHING_TO_INLINE")
inline fun Varargs.firstArg(): LuaValue = arg1()

@Suppress("NOTHING_TO_INLINE")
inline fun Varargs.firstIsNil(): Boolean = arg1().isnil()

@Suppress("NOTHING_TO_INLINE")
inline fun Varargs.secondArg(): LuaValue = arg(2)

@Suppress("NOTHING_TO_INLINE")
inline fun Varargs.argAt(index: Int): LuaValue = arg(index)

/**
 * @param default 返回默认值
 * @return 如果为空，返回默认值，否则返回字符串类型的本身
 */
infix fun LuaValue.toStringOr(default: String): String = if (!isnil()) tojstring() else default

/**
 * @param default 返回默认值
 * @return 如果为空，返回默认值，否则返回整数类型的本身
 */
infix fun LuaValue.toIntOr(default: Int): Int = if (!isnil()) toint() else default

// nil 的 toBoolean 方法返回 false , 所以不需要单独判断
@Suppress("NOTHING_TO_INLINE")
inline fun LuaValue.toBool() = toboolean()

fun LuaValue.isNotNil(): Boolean = !isnil()

fun LuaValue.ifNotNil(): LuaValue? = takeIf { it.isNotNil() }

/**
 * @return 如果不是表，返回 null，否则返回 LuaTable 类型的本身
 */
fun LuaValue.ifIsTable(): LuaTable? = takeIf { it.istable() } as? LuaTable

fun LuaValue.ifIsFunction(): LuaValue? = takeIf { it.isfunction() }

suspend fun LuaValue.suspendInvoke(varargs: Varargs): Varargs = suspendCancellableCoroutine {
    try {
        it.resume(this.invoke(varargs))
    } catch (e: Exception) {
        it.resumeWithException(e)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> T.toLuaValue(): LuaValue = CoerceJavaToLua.coerce(this)