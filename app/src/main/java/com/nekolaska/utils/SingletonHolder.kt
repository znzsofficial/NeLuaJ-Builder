package com.nekolaska.utils

open class SingletonHolder<out T, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator

    @Volatile
    private var _instance: T? = null

    fun init(arg: A): T {
        return _instance ?: synchronized(this) {
            _instance ?: creator!!.invoke(arg).also { _instance = it }
        }
    }

    val instance: T
        get() = _instance ?: throw IllegalStateException("Instance not created yet. You need to call init(arg) first.")
}
