package com.f3401pal

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.checkbox.MaterialCheckBox
import androidx.core.content.withStyledAttributes
import com.nekolaska.Builder.R

class CheckBoxEx : MaterialCheckBox {

    private var isIndeterminate = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        initCustomAttributes(attributeSet)
    }

    constructor(context: Context, attributeSet: AttributeSet, style: Int) : super(
        context,
        attributeSet,
        style
    ) {
        initCustomAttributes(attributeSet)
    }

    fun isIndeterminate(): Boolean {
        return isIndeterminate
    }

    fun setIndeterminate(isIndeterminate: Boolean) {
        this.isIndeterminate = isIndeterminate
        refreshDrawableState()
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isIndeterminate) mergeDrawableStates(drawableState, STATE_INDETERMINATE)
        return drawableState
    }

    private fun initCustomAttributes(attributeSet: AttributeSet) {
        context.withStyledAttributes(attributeSet, R.styleable.CheckBoxEx) {
            isIndeterminate = getBoolean(R.styleable.CheckBoxEx_state_indeterminate, false)
        }
    }

    companion object {

        private val STATE_INDETERMINATE = intArrayOf(R.attr.state_indeterminate)

    }
}