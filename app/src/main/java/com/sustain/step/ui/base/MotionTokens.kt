package com.sustain.step.ui.base

import android.view.animation.Interpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

object MotionTokens {
    const val DURATION_SHORT = 160L
    const val DURATION_MEDIUM = 220L
    val STANDARD_INTERPOLATOR: Interpolator = FastOutSlowInInterpolator()
}
