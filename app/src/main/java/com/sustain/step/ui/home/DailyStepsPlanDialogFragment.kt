package com.sustain.step.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.sustain.step.R
import com.sustain.step.databinding.DialogDailyStepsPlanBinding
import com.sustain.step.ui.base.BaseDialogFragment

class DailyStepsPlanDialogFragment : BaseDialogFragment<DialogDailyStepsPlanBinding>() {

    override val width = WindowManager.LayoutParams.MATCH_PARENT
    override val height = WindowManager.LayoutParams.WRAP_CONTENT
    private var resultGoal: Int? = null

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = DialogDailyStepsPlanBinding.inflate(inflater, container, false)

    override fun onViewCreated() {
        val initialGoal = arguments?.getInt(ARG_INITIAL_GOAL, DEFAULT_STEPS_GOAL) ?: DEFAULT_STEPS_GOAL
        binding.inputDailyStepsGoal.setText(initialGoal.toString())
        binding.buttonExit.setOnClickListener {
            dismiss()
        }
        binding.buttonSave.setOnClickListener {
            val parsedGoal = binding.inputDailyStepsGoal.text?.toString()?.trim()?.toIntOrNull()
            if (parsedGoal == null || parsedGoal <= 0) {
                binding.inputDailyStepsGoal.error = getString(R.string.daily_steps_plan_error_invalid)
                return@setOnClickListener
            }
            resultGoal = parsedGoal
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val goal = resultGoal ?: return
        setFragmentResult(
            REQUEST_KEY, bundleOf(
                BUNDLE_KEY_GOAL to goal
            )
        )
    }

    companion object {
        const val TAG = "DailyStepsPlanDialogFragment.TAG"
        const val REQUEST_KEY = "DailyStepsPlanDialogFragment.REQUEST_KEY"
        const val BUNDLE_KEY_GOAL = "DailyStepsPlanDialogFragment.BUNDLE_KEY_GOAL"
        private const val ARG_INITIAL_GOAL = "ARG_INITIAL_GOAL"
        private const val DEFAULT_STEPS_GOAL = 7000

        fun newInstance(initialGoal: Int): DailyStepsPlanDialogFragment {
            return DailyStepsPlanDialogFragment().apply {
                arguments = bundleOf(ARG_INITIAL_GOAL to initialGoal)
            }
        }
    }
}
