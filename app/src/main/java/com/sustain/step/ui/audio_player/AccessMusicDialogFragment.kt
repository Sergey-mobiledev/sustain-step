package com.sustain.step.ui.audio_player

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.sustain.step.databinding.DialogAccessMusicBinding
import com.sustain.step.ui.base.BaseDialogFragment

class AccessMusicDialogFragment: BaseDialogFragment<DialogAccessMusicBinding>() {

    override val width = WindowManager.LayoutParams.MATCH_PARENT
    override val height = WindowManager.LayoutParams.WRAP_CONTENT
    private var fragmentResult = false

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = DialogAccessMusicBinding.inflate(inflater, container, false)


    override fun onViewCreated() {
        binding.apply {
            buttonExit.setOnClickListener {
                dismiss()
            }
            buttonAllow.setOnClickListener {
                fragmentResult = true
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setFragmentResult(
            REQUEST_KEY, bundleOf(
                BUNDLE_KEY to fragmentResult
            )
        )
    }

    companion object {
        const val TAG = "AccessMusicDialogFragment.TAG"
        const val REQUEST_KEY = "AccessMusicDialogFragment.REQUEST_KEY"
        const val BUNDLE_KEY = "AccessMusicDialogFragment.BUNDLE_KEY"
    }
}