package com.sustain.step.ui.base.navigation

interface Navigator {

    fun navigate(tag: String)

    fun goBack(result: Any? = null)
}