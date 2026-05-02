package com.sustain.step.ui.history

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.sustain.step.R
import com.sustain.step.data.database.entity.HistoryEntity
import com.sustain.step.databinding.FragmentHistoryBinding
import com.sustain.step.di.factory
import com.sustain.step.ui.audio_player.service.PlaybackStateStore
import com.sustain.step.ui.base.BaseFragment
import com.sustain.step.ui.base.MotionTokens
import com.sustain.step.ui.base.navigation.mainNavigator
import com.sustain.step.ui.menu.MenuFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryFragment : BaseFragment<FragmentHistoryBinding>(FragmentHistoryBinding::inflate) {

    override val viewModel by viewModels<HistoryViewModel> { factory() }
    private val historyAdapter = HistoryAdapter()
    private var emptyStateJob: Job? = null
    private var deleteSnackBar: Snackbar? = null
    private var selectedPeriod = HistoryPeriod.DAY
    private var allHistoryItems: List<HistoryEntity> = emptyList()
    private var statusBarTop = 0
    private var listBottomPadding = 0
    private var hasSubmittedHistory = false
    private var hasPlayedEnterAnimation = false
    private var hasPlayedListCascade = false
    private var appBarOffsetListener: AppBarLayout.OnOffsetChangedListener? = null
    /** Scroll distance only for [binding.historyCollapsing] (excludes chip row), captured when expanded. */
    private var collapsingToolbarScrollRangePx = 0
    private var lastAppBarVerticalOffset = 0
    private var largeTitleMorphDx = 0f
    private var largeTitleMorphDy = 0f
    private var largeTitleMorphScaleX = 1f
    private var largeTitleMorphScaleY = 1f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLocalHeader()
        binding.apply {
            rvHistory.isVisible = false
            tvEmptyMessage.isVisible = false
            historyMenuButton.setOnClickListener {
                animateQuickTap(historyMenuButton) {
                    mainNavigator().navigate(MenuFragment.TAG)
                }
            }
            setupPeriodFilters()
            rvHistory.apply {
                adapter = historyAdapter
                isMotionEventSplittingEnabled = false
                val itemAnimator = itemAnimator
                if (itemAnimator is DefaultItemAnimator) {
                    itemAnimator.supportsChangeAnimations = false
                    itemAnimator.addDuration = MotionTokens.DURATION_MEDIUM
                    itemAnimator.removeDuration = MotionTokens.DURATION_MEDIUM
                    itemAnimator.moveDuration = MotionTokens.DURATION_MEDIUM
                }
                setupSwipeDelete(this)
            }
            viewModel.history.observe(viewLifecycleOwner) {
                allHistoryItems = it
                renderHistory()
            }
        }
        observeMiniPlayerVisibility()
    }

    private fun observeMiniPlayerVisibility() {
        viewLifecycleOwner.lifecycleScope.launch {
            PlaybackStateStore.state.collectLatest { state ->
                val shouldReserveBottom = state.currentUri != null
                listBottomPadding = if (shouldReserveBottom) dpToPx(136) else 0
                updateHistoryListPadding()
            }
        }
    }

    private fun setupLocalHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            statusBarTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            binding.historyAppBar.updatePadding(top = statusBarTop)
            binding.historyAppBar.post {
                captureLargeTitleMorphTargets()
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.historyAppBar.doOnLayout {
            captureLargeTitleMorphTargets()
        }
        registerAppBarScrollFeedback()
        updateHistoryListPadding()
    }

    private fun updateHistoryListPadding() {
        val bottom = listBottomPadding + dpToPx(LIST_SCROLL_END_PADDING_DP)
        binding.rvHistory.updatePadding(top = 0, bottom = bottom)
    }

    private fun registerAppBarScrollFeedback() {
        appBarOffsetListener?.let { binding.historyAppBar.removeOnOffsetChangedListener(it) }
        appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            lastAppBarVerticalOffset = verticalOffset
            if (verticalOffset == 0) {
                binding.historyCollapsing.post {
                    captureCollapsingToolbarScrollRange()
                    captureLargeTitleMorphTargets()
                }
            }
            val rangePx = collapsingToolbarScrollRangePx
            val p = if (rangePx <= 0) {
                val fallbackTotal = appBarLayout.totalScrollRange
                if (fallbackTotal <= 0) {
                    0f
                } else {
                    (-verticalOffset / fallbackTotal.toFloat()).coerceIn(0f, 1f)
                }
            } else {
                val consumedPx = (-verticalOffset).coerceAtLeast(0)
                (consumedPx / rangePx.toFloat()).coerceIn(0f, 1f)
            }
            applyAppBarCollapseVisuals(p)
        }
        binding.historyAppBar.addOnOffsetChangedListener(appBarOffsetListener!!)
    }

    private fun captureCollapsingToolbarScrollRange() {
        val ctl = binding.historyCollapsing
        collapsingToolbarScrollRangePx =
            (ctl.height - ctl.minimumHeight).coerceAtLeast(1)
    }

    private fun applyAppBarCollapseVisuals(p: Float) {
        val progress = p.coerceIn(0f, 1f)
        val morphProgress = emphasizedProgress(
            progress = progress,
            start = 0.12f,
            end = 0.92f
        )
        val compactAlphaProgress = emphasizedProgress(
            progress = progress,
            start = 0.48f,
            end = 0.96f
        )
        val largeAlphaProgress = emphasizedProgress(
            progress = progress,
            start = 0.08f,
            end = 0.84f
        )
        binding.historyHeaderScrim.alpha = progress
        binding.historyAppBarDivider.alpha = emphasizedProgress(progress, 0.08f, 0.48f)
        binding.historyChipScroll.elevation = 0f
        binding.historyCompactTitle.alpha = compactAlphaProgress
        binding.historyLargeTitle.alpha = 1f - largeAlphaProgress
        binding.historyLargeTitle.pivotX = 0f
        binding.historyLargeTitle.pivotY = 0f
        binding.historyLargeTitle.translationX = largeTitleMorphDx * morphProgress
        binding.historyLargeTitle.translationY = largeTitleMorphDy * morphProgress
        binding.historyLargeTitle.scaleX = 1f + (largeTitleMorphScaleX - 1f) * morphProgress
        binding.historyLargeTitle.scaleY = 1f + (largeTitleMorphScaleY - 1f) * morphProgress
        val summaryAlphaProgress = emphasizedProgress(
            progress = progress,
            start = 0.08f,
            end = 0.88f
        )
        binding.historySummaryCard.alpha = 1f - summaryAlphaProgress
        val summaryScale = 1f - 0.04f * summaryAlphaProgress
        binding.historySummaryCard.scaleX = summaryScale
        binding.historySummaryCard.scaleY = summaryScale
    }

    private fun emphasizedProgress(progress: Float, start: Float, end: Float): Float {
        if (progress <= start) return 0f
        if (progress >= end) return 1f
        val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun captureLargeTitleMorphTargets() {
        val large = binding.historyLargeTitle
        val compact = binding.historyCompactTitle
        if (large.width == 0 || large.height == 0 || compact.width == 0 || compact.height == 0) return

        val largeLocation = IntArray(2)
        val compactLocation = IntArray(2)
        large.getLocationInWindow(largeLocation)
        compact.getLocationInWindow(compactLocation)

        largeTitleMorphDx = (compactLocation[0] - largeLocation[0]).toFloat()
        largeTitleMorphDy = (compactLocation[1] - largeLocation[1]).toFloat()
        largeTitleMorphScaleX = (compact.width.toFloat() / large.width.toFloat()).coerceIn(0.6f, 1f)
        largeTitleMorphScaleY =
            (compact.textSize / large.textSize).coerceIn(0.6f, 1f)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

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

    private fun setupPeriodFilters() {
        binding.chipDay.setOnClickListener { selectPeriod(HistoryPeriod.DAY) }
        binding.chipWeek.setOnClickListener { selectPeriod(HistoryPeriod.WEEK) }
        binding.chipMonth.setOnClickListener { selectPeriod(HistoryPeriod.MONTH) }
        binding.chipAll.setOnClickListener { selectPeriod(HistoryPeriod.ALL) }
        renderPeriodChips()
    }

    private fun selectPeriod(period: HistoryPeriod) {
        if (selectedPeriod == period) return
        selectedPeriod = period
        renderPeriodChips()
        renderHistory(animateChange = true)
    }

    private fun renderPeriodChips() {
        listOf(
            HistoryPeriod.DAY to binding.chipDay,
            HistoryPeriod.WEEK to binding.chipWeek,
            HistoryPeriod.MONTH to binding.chipMonth,
            HistoryPeriod.ALL to binding.chipAll
        ).forEach { (period, chip) ->
            val selected = period == selectedPeriod
            chip.setChipSelected(selected)
        }
    }

    private fun AppCompatTextView.setChipSelected(selected: Boolean) {
        setBackgroundResource(if (selected) R.drawable.back_orange_r_24 else R.drawable.back_white_r_24)
        setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.white_f8 else R.color.black_text_color
            )
        )
    }

    private fun renderHistory(animateChange: Boolean = false) {
        val filteredItems = filterItemsForSelectedPeriod(allHistoryItems)
        renderSummary(filteredItems)
        if (filteredItems.isEmpty()) {
            emptyStateJob?.cancel()
            hasSubmittedHistory = true
            historyAdapter.submitList(emptyList())
            emptyStateJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(160)
                if (historyAdapter.currentList.isEmpty()) {
                    binding.tvEmptyMessage.isVisible = true
                    binding.rvHistory.isVisible = false
                    playEmptyEnterAnimationIfNeeded()
                }
            }
        } else {
            emptyStateJob?.cancel()
            binding.tvEmptyMessage.isVisible = false
            submitHistoryList(
                items = filteredItems,
                animateChange = animateChange && hasSubmittedHistory,
                forceScrollToTop = animateChange
            )
        }
    }

    private fun submitHistoryList(
        items: List<HistoryEntity>,
        animateChange: Boolean,
        forceScrollToTop: Boolean
    ) {
        val recyclerView = binding.rvHistory
        val shouldKeepCollapsed = lastAppBarVerticalOffset != 0
        val submitAndShow = {
            val shouldCascadeItems = !hasPlayedListCascade
            if (shouldCascadeItems) {
                historyAdapter.enableCascadeForCurrentData()
            }
            historyAdapter.submitList(items) {
                hasSubmittedHistory = true
                binding.rvHistory.isVisible = true
                if (forceScrollToTop) {
                    binding.rvHistory.scrollToPosition(0)
                    binding.historyAppBar.setExpanded(!shouldKeepCollapsed, false)
                }
                if (shouldCascadeItems) {
                    hasPlayedListCascade = true
                }
                if (animateChange) {
                    playListSwapInAnimation()
                } else {
                    playListEnterAnimationIfNeeded()
                }
            }
        }
        if (!animateChange || !recyclerView.isVisible) {
            submitAndShow()
            return
        }
        recyclerView.animate()
            .alpha(0f)
            .translationY(dpToPx(8).toFloat())
            .setDuration(MotionTokens.DURATION_SHORT)
            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
            .withEndAction {
                recyclerView.translationY = -dpToPx(6).toFloat()
                submitAndShow()
            }
            .start()
    }

    private fun playListSwapInAnimation() {
        binding.rvHistory.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(MotionTokens.DURATION_MEDIUM)
            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
            .start()
    }

    private fun filterItemsForSelectedPeriod(items: List<HistoryEntity>): List<HistoryEntity> {
        val today = Calendar.getInstance()
        val filteredItems = if (selectedPeriod == HistoryPeriod.ALL) {
            items
        } else {
            items.filter { item ->
            val itemCalendar = parseHistoryDate(item.date) ?: return@filter false
            when (selectedPeriod) {
                HistoryPeriod.DAY -> isSameDay(itemCalendar, today)
                HistoryPeriod.WEEK -> isSameWeek(itemCalendar, today)
                HistoryPeriod.MONTH -> isSameMonth(itemCalendar, today)
                HistoryPeriod.ALL -> true
            }
            }
        }
        return filteredItems.sortedByDescending { item ->
            parseHistoryDate(item.date)?.timeInMillis ?: Long.MIN_VALUE
        }
    }

    private fun renderSummary(items: List<HistoryEntity>) {
        val totalSteps = items.sumOf { it.steps }
        val daysCount = items.map { it.date }.distinct().size.coerceAtLeast(1)
        val averageSteps = totalSteps / daysCount
        val distanceKm = totalSteps * KM_PER_STEP
        val calories = (totalSteps * KCAL_PER_STEP).toInt()

        binding.historySummaryTitle.text = getString(selectedPeriod.summaryTitleRes)
        binding.historySummarySteps.text = getString(
            R.string.history_summary_steps,
            formatWithCommas(totalSteps)
        )
        binding.historySummaryDistance.text = getString(
            R.string.history_summary_distance,
            String.format(Locale.getDefault(), "%.1f", distanceKm)
        )
        binding.historySummaryCalories.text = getString(
            R.string.history_summary_calories,
            calories
        )
        binding.historySummaryAverage.text = getString(
            R.string.history_summary_average,
            formatWithCommas(averageSteps)
        )
        if (lastAppBarVerticalOffset == 0) {
            binding.historyCollapsing.post {
                captureCollapsingToolbarScrollRange()
                captureLargeTitleMorphTargets()
            }
        }
    }

    private fun parseHistoryDate(date: String): Calendar? {
        return runCatching {
            val parsed = SimpleDateFormat("yyyy-M-d", Locale.ENGLISH).parse(date) ?: return null
            Calendar.getInstance().apply { time = parsed }
        }.getOrNull()
    }

    private fun formatWithCommas(value: Int): String = String.format("%,d", value)

    private fun isSameDay(left: Calendar, right: Calendar): Boolean {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
            left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameWeek(left: Calendar, right: Calendar): Boolean {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
            left.get(Calendar.WEEK_OF_YEAR) == right.get(Calendar.WEEK_OF_YEAR)
    }

    private fun isSameMonth(left: Calendar, right: Calendar): Boolean {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
            left.get(Calendar.MONTH) == right.get(Calendar.MONTH)
    }

    private fun setupSwipeDelete(recyclerView: RecyclerView) {
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val item = historyAdapter.currentList.getOrNull(position)
                    if (item == null) {
                        if (position != RecyclerView.NO_POSITION) {
                            historyAdapter.notifyItemChanged(position)
                        }
                        return
                    }
                    deleteHistoryItem(item)
                }
            }
        ).attachToRecyclerView(recyclerView)
    }

    private fun deleteHistoryItem(item: HistoryEntity) {
        deleteSnackBar?.dismiss()
        viewModel.deleteHistoryItem(item)
        deleteSnackBar = Snackbar.make(
            binding.root,
            getString(R.string.history_deleted),
            Snackbar.LENGTH_LONG
        )
            .setAction(getString(R.string.undo)) {
                viewModel.restoreHistoryItem(item)
            }
            .setTextColor(Color.BLACK)
            .setBackgroundTint(Color.WHITE)
            .setActionTextColor(Color.BLACK)
        deleteSnackBar?.show()
    }

    private fun playListEnterAnimationIfNeeded() {
        if (hasPlayedEnterAnimation) return
        hasPlayedEnterAnimation = true
        val shift = dpToPx(12).toFloat()
        binding.rvHistory.alpha = 0f
        binding.rvHistory.translationY = shift
        binding.rvHistory.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(MotionTokens.DURATION_MEDIUM)
            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
            .start()
    }

    private fun playEmptyEnterAnimationIfNeeded() {
        if (hasPlayedEnterAnimation) return
        hasPlayedEnterAnimation = true
        binding.tvEmptyMessage.alpha = 0f
        binding.tvEmptyMessage.animate()
            .alpha(1f)
            .setDuration(MotionTokens.DURATION_SHORT)
            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
            .start()
    }

    override fun onDestroyView() {
        appBarOffsetListener?.let { listener ->
            binding.historyAppBar.removeOnOffsetChangedListener(listener)
        }
        appBarOffsetListener = null
        emptyStateJob?.cancel()
        emptyStateJob = null
        deleteSnackBar?.dismiss()
        deleteSnackBar = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "HistoryFragment.tag"
        private const val LIST_SCROLL_END_PADDING_DP = 40
        private const val KM_PER_STEP = 0.00075
        private const val KCAL_PER_STEP = 0.04
    }

    private enum class HistoryPeriod(val summaryTitleRes: Int) {
        DAY(R.string.history_summary_title_today),
        WEEK(R.string.history_summary_title_week),
        MONTH(R.string.history_summary_title_month),
        ALL(R.string.history_summary_title_all)
    }
}