package com.sustain.step.ui.home.permission

import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.sustain.step.databinding.DialogAccessBinding
import com.sustain.step.ui.base.BaseDialogFragment

class AccessDialogFragment: BaseDialogFragment<DialogAccessBinding>() {

    override val width = WindowManager.LayoutParams.MATCH_PARENT
    override val height = WindowManager.LayoutParams.WRAP_CONTENT
    private var result = false

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = DialogAccessBinding.inflate(inflater, container, false)

    override fun onViewCreated() {
        binding.apply {
            buttonExit.setOnClickListener {
                dismiss()
            }
            buttonAllow.setOnClickListener {
                result = true
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setFragmentResult(
            REQUEST_KEY, bundleOf(
                BUNDLE_KEY to result
            )
        )
    }

    companion object {
        const val TAG = "AccessDialogFragment.TAG"
        const val REQUEST_KEY = "AccessDialogFragment.REQUEST_KEY"
        const val BUNDLE_KEY = "AccessDialogFragment.BUNDLE_KEY"
    }
}