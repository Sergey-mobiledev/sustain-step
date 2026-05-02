package com.sustain.step.ui.splash

import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.sustain.step.databinding.DialogAccessNotificationsBinding
import com.sustain.step.ui.base.BaseDialogFragment

class AccessNotificationsDialogFragment : BaseDialogFragment<DialogAccessNotificationsBinding>() {

    override val width = WindowManager.LayoutParams.MATCH_PARENT
    override val height = WindowManager.LayoutParams.WRAP_CONTENT
    private var fragmentResult = false

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = DialogAccessNotificationsBinding.inflate(inflater, container, false)

    override fun onViewCreated() {
        binding.apply {
            buttonSkip.setOnClickListener { dismiss() }
            buttonAllow.setOnClickListener {
                fragmentResult = true
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY to fragmentResult))
    }

    companion object {
        const val TAG = "AccessNotificationsDialogFragment.TAG"
        const val REQUEST_KEY = "AccessNotificationsDialogFragment.REQUEST_KEY"
        const val BUNDLE_KEY = "AccessNotificationsDialogFragment.BUNDLE_KEY"
    }
}
