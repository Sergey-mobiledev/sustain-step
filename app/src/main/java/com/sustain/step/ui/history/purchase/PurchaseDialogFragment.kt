package com.sustain.step.ui.history.purchase

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.sustain.step.databinding.DialogPurchaseBinding
import com.sustain.step.ui.base.BaseDialogFragment

class PurchaseDialogFragment: BaseDialogFragment<DialogPurchaseBinding>() {

    override val width = WindowManager.LayoutParams.MATCH_PARENT
    override val height = WindowManager.LayoutParams.WRAP_CONTENT
    private var fragmentResult = false

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = DialogPurchaseBinding.inflate(inflater, container, false)

    override fun onViewCreated() {
        binding.apply {
            buttonBuyNow.setOnClickListener {
                fragmentResult = true
                dismiss()
            }
            buttonNoThanks.setOnClickListener {
                dismiss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        setFragmentResult(
            REQUEST_KEY, bundleOf(
                BUNDLE_KEY to fragmentResult
            )
        )
    }

    companion object {
        const val TAG = "PurchaseDialogFragment.tag"
        const val REQUEST_KEY = "PurchaseDialogFragment.request_key"
        const val BUNDLE_KEY = "PurchaseDialogFragment.bundle_key"
    }
}