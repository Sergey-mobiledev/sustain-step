package com.sustain.step.ui.menu

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.os.bundleOf
import androidx.core.view.get
import androidx.fragment.app.setFragmentResult
import com.sustain.step.R
import com.sustain.step.data.services.openChromeTabs
import com.sustain.step.databinding.DialogMenuBinding
import com.sustain.step.ui.base.BaseDialogFragment
import com.sustain.step.ui.base.MotionTokens

class MenuDialogFragment : BaseDialogFragment<DialogMenuBinding>() {

    override val width = WindowManager.LayoutParams.MATCH_PARENT
    override val height = WindowManager.LayoutParams.MATCH_PARENT
    override val edgeToEdge: Boolean = true
    private var result = 0

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = DialogMenuBinding.inflate(inflater, container, false)

    override fun onViewCreated() {
        binding.apply {
            val menuRows = listOf(
                buttonClose,
                buttonHome,
                buttonCompletedTasks,
                buttonAudioPlayer,
                buttonPrivacy
            )
            menuRows.forEach { view ->
                view.alpha = 0f
                view.translationY = dpToPx(16).toFloat()
            }
            menuRows.forEachIndexed { index, view ->
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * 65L)
                    .setDuration(MotionTokens.DURATION_MEDIUM)
                    .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                    .start()
            }

            buttonClose.setOnClickListener {
                dismiss()
            }
            buttonHome.setOnClickListener {
                result = 1
                setFragmentResult(
                    REQUEST_KEY, bundleOf(
                        BUNDLE_KEY to result
                    )
                )
                dismiss()
            }
            buttonCompletedTasks.setOnClickListener {
                result = 2
                setFragmentResult(
                    REQUEST_KEY, bundleOf(
                        BUNDLE_KEY to result
                    )
                )
                dismiss()
            }
            buttonAudioPlayer.setOnClickListener {
                result = 3
                setFragmentResult(
                    REQUEST_KEY, bundleOf(
                        BUNDLE_KEY to result
                    )
                )
                dismiss()
            }
            buttonPrivacy.setOnClickListener {
                animateWaterDropTap(getLeadingIcon(buttonPrivacy)) {
                    openChromeTabs()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.orange)))
            window.setDimAmount(0f)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    companion object {
        const val TAG = "MenuDialogFragment.TAG"
        const val REQUEST_KEY = "MenuDialogFragment.REQUEST_KEY"
        const val BUNDLE_KEY = "MenuDialogFragment.BUNDLE_KEY"
    }

    private fun animateWaterDropTap(view: ImageView?, onEnd: () -> Unit) {
        if (view == null) {
            onEnd()
            return
        }
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(MotionTokens.DURATION_SHORT)
            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
            .withEndAction {
                view.animate()
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(MotionTokens.DURATION_SHORT)
                    .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                    .withEndAction {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(MotionTokens.DURATION_SHORT)
                            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                            .withEndAction(onEnd)
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun getLeadingIcon(container: ViewGroup): ImageView? {
        val child = container[0]
        return child as? ImageView
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}