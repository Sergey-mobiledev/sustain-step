package com.sustain.step.ui.menu

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.core.view.updateLayoutParams
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.sustain.step.MainActivity
import com.sustain.step.R
import com.sustain.step.data.services.openChromeTabs
import com.sustain.step.data.services.showToast
import com.sustain.step.databinding.DialogMenuBinding
import com.sustain.step.di.App
import com.sustain.step.ui.audio_player.AudioPlayerFragment
import com.sustain.step.ui.base.MotionTokens
import com.sustain.step.ui.base.navigation.mainNavigator
import com.sustain.step.ui.history.HistoryFragment
import com.sustain.step.ui.home.HomeFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MenuFragment : Fragment() {

    private var _binding: DialogMenuBinding? = null
    private val binding: DialogMenuBinding
        get() = _binding ?: error("Binding accessed outside of view lifecycle")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            val baseCloseMargin = dpToPx(20)
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val systemTopInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                buttonClose.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = baseCloseMargin + systemTopInset
                }
                insets
            }
            ViewCompat.requestApplyInsets(root)

            val menuRows = listOf(
                buttonClose,
                buttonHome,
                buttonCompletedTasks,
                buttonAudioPlayer,
                buttonPrivacy,
                buttonTestDate
            )
            menuRows.forEach { menuRow ->
                menuRow.alpha = 0f
                menuRow.translationY = dpToPx(16).toFloat()
            }
            menuRows.forEachIndexed { index, menuRow ->
                menuRow.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * 65L)
                    .setDuration(MotionTokens.DURATION_MEDIUM)
                    .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                    .start()
            }

            buttonClose.setOnClickListener {
                animateQuickTap(buttonClose) {
                    parentFragmentManager.popBackStack()
                }

            }
            buttonHome.setOnClickListener {
                animateQuickTap(iconHome) {
                    navigateTo(HomeFragment.TAG)
                }
            }
            buttonCompletedTasks.setOnClickListener {
                animateQuickTap(getLeadingIcon(buttonCompletedTasks)) {
                    navigateTo(HistoryFragment.TAG)
                }
            }
            buttonAudioPlayer.setOnClickListener {
                animateQuickTap(getLeadingIcon(buttonAudioPlayer)) {
                    navigateTo(AudioPlayerFragment.TAG)
                }
            }
            buttonPrivacy.setOnClickListener {
                animateQuickTap(getLeadingIcon(buttonPrivacy)) {
                    openChromeTabs()
                }
            }
            buttonTestDate.setOnClickListener {
                animateQuickTap(getLeadingIcon(buttonTestDate)) {
                    showTestDatePicker()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun navigateTo(tag: String) {
        val activity = activity as? MainActivity ?: return
        mainNavigator().launchFragment(activity, tag, addToBackStack = false)
    }

    private fun animateQuickTap(view: ImageView?, onEnd: () -> Unit) {
        if (view == null) {
            onEnd()
            return
        }
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(70L)
            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80L)
                    .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                    .withEndAction(onEnd)
                    .start()
            }
            .start()
    }

    private fun getLeadingIcon(container: ViewGroup): ImageView? {
        val child = container[0]
        return child as? ImageView
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showTestDatePicker() {
        val settings = (requireContext().applicationContext as App).settings
        val initial = parseDateOrToday(settings.debugCurrentDateOverride)
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, monthOfYear, dayOfMonth ->
                val selectedDate = "${year}-${monthOfYear + 1}-$dayOfMonth"
                settings.debugCurrentDateOverride = selectedDate
                showToast(getString(R.string.test_date_set, selectedDate))
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setTitle(R.string.test_date_picker_title)
        dialog.setButton(
            DatePickerDialog.BUTTON_NEUTRAL,
            getString(R.string.retry)
        ) { _, _ ->
            settings.debugCurrentDateOverride = null
            showToast(getString(R.string.test_date_reset))
        }
        dialog.show()
    }

    private fun parseDateOrToday(date: String?): Calendar {
        if (date.isNullOrBlank()) return Calendar.getInstance()
        return runCatching {
            val parsed = SimpleDateFormat("yyyy-M-d", Locale.ENGLISH).parse(date)
                ?: return Calendar.getInstance()
            Calendar.getInstance().apply { time = parsed }
        }.getOrElse { Calendar.getInstance() }
    }

    companion object {
        const val TAG = "MenuFragment.TAG"
    }
}
