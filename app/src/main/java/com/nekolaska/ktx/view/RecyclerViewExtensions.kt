package com.nekolaska.ktx.view

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.fastscroll.FastScrollerBuilder

fun RecyclerView.fastScroller() = FastScrollerBuilder(this)
    .useMd2Style()

fun RecyclerView.linearLayoutManager() {
   layoutManager = LinearLayoutManager(context)
}