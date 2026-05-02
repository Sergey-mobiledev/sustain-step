package com.sustain.step.ui.splash

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.sustain.step.databinding.DialogErrorBinding
import com.sustain.step.ui.base.BaseDialogFragment

class ErrorDialogFragment: BaseDialogFragment<DialogErrorBinding>() {

    override val width = WindowManager.LayoutParams.MATCH_PARENT
    override val height = WindowManager.LayoutParams.WRAP_CONTENT

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = DialogErrorBinding.inflate(inflater, container, false)

    override fun onViewCreated() {
        binding.buttonAllow.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setFragmentResult(
            REQUEST_KEY, bundleOf(
                BUNDLE_KEY to false
            )
        )
    }

    companion object {
        const val TAG = "ErrorDialogFragment.TAG"
        const val REQUEST_KEY = "ErrorDialogFragment.REQUEST_KEY"
        const val BUNDLE_KEY = "ErrorDialogFragment.BUNDLE_KEY"
    }
}