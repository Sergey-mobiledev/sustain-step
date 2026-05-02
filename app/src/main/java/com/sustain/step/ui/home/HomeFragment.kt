package com.sustain.step.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.sustain.step.R
import com.sustain.step.data.services.showToast
import com.sustain.step.databinding.FragmentHomeBinding
import com.sustain.step.di.factory
import com.sustain.step.ui.audio_player.service.PlaybackStateStore
import com.sustain.step.ui.base.BaseFragment
import com.sustain.step.ui.base.MotionTokens
import com.sustain.step.ui.base.navigation.mainNavigator
import com.sustain.step.ui.history.HistoryFragment
import com.sustain.step.ui.home.permission.AccessDialogFragment
import com.sustain.step.ui.home.service.StepTrackingService
import com.sustain.step.ui.menu.MenuFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    override val viewModel by viewModels<HomeViewModel> { factory() }
    private var snackBar: Snackbar? = null
    private var hasPlayedEnterAnimation = false
    private var lastKnownActivityPermissionGranted: Boolean? = null
    private var lastRenderedDeniedState: Boolean? = null
    private var isEcoTaskTransitionRunning = false
    private var pendingEcoTaskText: String? = null
    private var birdWiggleJob: Job? = null
    private var shredderJob: Job? = null
    private var appBarOffsetListener: AppBarLayout.OnOffsetChangedListener? = null
    private var collapsingToolbarScrollRangePx = 0
    private var lastHomeAppBarVerticalOffset = 0
    private var largeTitleMorphDx = 0f
    private var largeTitleMorphDy = 0f
    private var largeTitleMorphScaleX = 1f
    private var largeTitleMorphScaleY = 1f
    private val recognitionPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(
                Manifest.permission.ACTIVITY_RECOGNITION,
                false
            ) -> {
                startStepTrackingService()
                viewModel.startCounting()
                renderStepsPermissionGrantedState()
                showToast("Permission is granted")
            }

            else -> {
                stopStepTracking()
                viewModel.loadActualData()
                renderStepsPermissionDeniedState()
                showToast("Step tracking is disabled. You can enable permission later.")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.homeContent.isInvisible = false
        setupLocalHomeHeader()
        binding.homeMenuButton.setOnClickListener {
            animateMenuQuickTap(binding.homeMenuButton) {
                mainNavigator().navigate(MenuFragment.TAG)
            }
        }
        restoreScrollIfNeeded()
        playEnterAnimationIfNeeded()
        binding.buttonGrantPermission.setOnClickListener {
            onGrantPermissionClicked()
        }
        binding.buttonChangeGoal.setOnClickListener {
            if (!hasActivityRecognitionPermission()) return@setOnClickListener
            animateCardTap(binding.homeSummaryCard) { showDailyStepsPlanDialog() }
        }
        binding.buttonHomeHistory.setOnClickListener {
            animateCardTap(binding.homeSummaryCard) {
                mainNavigator().navigate(HistoryFragment.TAG)
            }
        }
        setDailyStepsPlanResultListener()
        binding.apply {
            setupEcoTaskActions()
            setupEcoCardScrollSafeTap()
            viewModel.ecoTaskData.observe(viewLifecycleOwner) { ecoTaskData ->
                if (isEcoTaskTransitionRunning) {
                    pendingEcoTaskText = ecoTaskData.ecoTask
                } else {
                    currentEcoTask.text = ecoTaskData.ecoTask
                    renderEcoTaskDoneButtonDefault()
                }
            }
            viewModel.stepsData.observe(viewLifecycleOwner) { stepsData ->
                if (stepsData == null) return@observe
                if (!hasActivityRecognitionPermission()) {
                    renderStepsPermissionDeniedState()
                    return@observe
                }
                renderStepsProgress(stepsData.stepsCount)
            }
            showAccessDialog()
        }
        observeMiniPlayerVisibility()
    }

    override fun onResume() {
        super.onResume()
        val granted = hasActivityRecognitionPermission()
        lastKnownActivityPermissionGranted = granted
        if (!granted) {
            stopStepTracking()
            viewModel.loadActualData()
            renderStepsPermissionDeniedState()
        } else {
            startStepTrackingService()
            renderStepsPermissionGrantedState()
            // Always refresh on resume to reflect permission changes from Settings immediately.
            viewModel.startCounting()
        }
    }

    private fun setupEcoCardScrollSafeTap() {
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val slopSq = slop * slop
        val swipeThreshold = dpToPx(48).toFloat()
        var downRawX = 0f
        var downRawY = 0f
        var movedPastSlop = false
        val card = binding.cardEcoTask
        card.isClickable = false
        card.isFocusable = false
        val touchListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    movedPastSlop = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (dx * dx + dy * dy > slopSq) {
                        movedPastSlop = true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = kotlin.math.abs(event.rawY - downRawY)
                    val horizontalSkip =
                        kotlin.math.abs(deltaX) >= swipeThreshold &&
                            deltaY < slop * 2
                    when {
                        horizontalSkip -> {
                            viewModel.skipCurrentTask()
                            showSkippedTaskSnack()
                        }
                        !movedPastSlop && !isEcoTaskTransitionRunning -> {
                            animateCardTap(card) { completeEcoTaskWithAnimation() }
                        }
                    }
                }
            }
            false
        }
        attachTouchListenerRecursively(
            root = card,
            listener = touchListener,
            excludedViewIds = setOf(R.id.button_done, R.id.button_skip)
        )
    }

    private fun setupEcoTaskActions() {
        binding.buttonSkip.setOnClickListener {
            if (isEcoTaskTransitionRunning) return@setOnClickListener
            animateCardTap(binding.buttonSkip) {
                viewModel.skipCurrentTask()
                showSkippedTaskSnack()
            }
        }
        binding.buttonDone.setOnClickListener {
            if (isEcoTaskTransitionRunning) return@setOnClickListener
            animateCardTap(binding.cardEcoTask) {
                completeEcoTaskWithAnimation()
            }
        }
    }

    private fun completeEcoTaskWithAnimation() {
        if (isEcoTaskTransitionRunning) return
        isEcoTaskTransitionRunning = true
        pendingEcoTaskText = null
        viewModel.setDoneToCurrentTask()
        playDoneToLoadingAnimation()
    }

    private fun renderEcoTaskDoneButtonDefault() {
        birdWiggleJob?.cancel()
        birdWiggleJob = null
        binding.doneBird.apply {
            animate().cancel()
            rotation = 0f
            scaleX = 1f
            scaleY = 1f
            visibility = View.GONE
        }
        binding.doneLabel.visibility = View.VISIBLE
        binding.doneLabel.text = getString(R.string.done)
        binding.buttonDone.alpha = 1f
    }

    private fun playDoneToLoadingAnimation() {
        // Icon replaces text — do NOT call renderEcoTaskDoneButtonDefault() here (it hides the icon).
        binding.doneLabel.visibility = View.GONE
        binding.doneBird.visibility = View.VISIBLE
        binding.doneBird.bringToFront()
        binding.doneBird.alpha = 1f
        startDoneBirdDance()
        startEcoTaskShimmerPlaceholder()
    }

    private fun startDoneBirdDance() {
        birdWiggleJob?.cancel()
        birdWiggleJob = viewLifecycleOwner.lifecycleScope.launch {
            val bird = binding.doneBird
            val start = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - start) < 2000L) {
                val t = (System.currentTimeMillis() - start) / 1000.0
                val wobble = (kotlin.math.sin(t * 12.0) * 6f).toFloat()
                bird.rotation = wobble
                bird.scaleX = 1f + 0.06f * kotlin.math.sin(t * 10.0).toFloat()
                bird.scaleY = 1f + 0.06f * kotlin.math.cos(t * 9.0).toFloat()
                delay(16L)
            }
            bird.animate().cancel()
            bird.rotation = 0f
            bird.scaleX = 1f
            bird.scaleY = 1f
        }
    }

    private suspend fun awaitPendingEcoTaskText(timeoutMs: Long = 900L): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (isAdded && SystemClock.elapsedRealtime() < deadline) {
            pendingEcoTaskText?.takeIf { it.isNotBlank() }?.let { return it }
            viewModel.ecoTaskData.value?.ecoTask?.takeIf { it.isNotBlank() }?.let { return it }
            delay(24L)
        }
        return pendingEcoTaskText?.takeIf { it.isNotBlank() }
            ?: viewModel.ecoTaskData.value?.ecoTask?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun measureEcoTaskPlaceholderHeightPx(taskText: CharSequence): Int {
        val tv = binding.currentEcoTask
        val hostWidth = binding.ecoTaskTextHost.width.takeIf { it > 0 }
            ?: return dpToPx(22)
        val contentWidth =
            (hostWidth - tv.paddingStart - tv.paddingEnd).coerceAtLeast(1)

        val sample = if (taskText.isBlank()) "\u00A0" else taskText
        val paint = tv.paint

        val builder = StaticLayout.Builder.obtain(sample, 0, sample.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(tv.lineSpacingExtra, tv.lineSpacingMultiplier)
            .setIncludePad(tv.includeFontPadding)
            .setBreakStrategy(tv.breakStrategy)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setHyphenationFrequency(tv.hyphenationFrequency)
        }
        val layout = builder.build()
        return (layout.height + tv.paddingTop + tv.paddingBottom).coerceAtLeast(dpToPx(22))
    }

    private fun startEcoTaskShimmerPlaceholder() {
        shredderJob?.cancel()
        binding.currentEcoTask.visibility = View.INVISIBLE
        binding.ecoTaskPlaceholder.visibility = View.INVISIBLE
        binding.ecoTaskShimmer.translationX = 0f

        binding.ecoTaskTextHost.post {
            if (!isAdded) return@post
            shredderJob = viewLifecycleOwner.lifecycleScope.launch {
                val taskPreview = awaitPendingEcoTaskText()
                val placeholderHeightPx = measureEcoTaskPlaceholderHeightPx(taskPreview)
                if (!isActive) return@launch

                binding.ecoTaskPlaceholder.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = placeholderHeightPx
                }
                binding.ecoTaskPlaceholder.requestLayout()
                binding.ecoTaskPlaceholder.visibility = View.VISIBLE

                val hostW = binding.ecoTaskTextHost.width.coerceAtLeast(dpToPx(160))
                val shimmerW = binding.ecoTaskShimmer.width.takeIf { it > 0 } ?: dpToPx(120)
                val loadingMs = 2000L
                val startedAt = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - startedAt < loadingMs) {
                    val loop = 1100L
                    val t =
                        ((System.currentTimeMillis() - startedAt) % loop).toFloat() / loop.toFloat()
                    binding.ecoTaskShimmer.translationX =
                        -shimmerW + t * (hostW + shimmerW)
                    delay(16L)
                }

                binding.ecoTaskPlaceholder.visibility = View.GONE

                val newTask = pendingEcoTaskText
                    ?: viewModel.ecoTaskData.value?.ecoTask
                    ?: ""

                binding.currentEcoTask.text = newTask
                binding.currentEcoTask.visibility = View.VISIBLE
                binding.currentEcoTask.alpha = 0f
                binding.currentEcoTask.animate()
                    .alpha(1f)
                    .setDuration(MotionTokens.DURATION_MEDIUM)
                    .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                    .start()

                binding.buttonDone.alpha = 0f
                renderEcoTaskDoneButtonDefault()
                binding.buttonDone.animate()
                    .alpha(1f)
                    .setDuration(MotionTokens.DURATION_MEDIUM)
                    .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                    .start()

                isEcoTaskTransitionRunning = false
                pendingEcoTaskText = null
            }
        }
    }

    private fun observeMiniPlayerVisibility() {
        viewLifecycleOwner.lifecycleScope.launch {
            PlaybackStateStore.state.collectLatest { state ->
                val shouldReserveBottom = state.currentUri != null
                val miniPad = if (shouldReserveBottom) dpToPx(136) else 0
                val bottom = miniPad + dpToPx(LIST_SCROLL_END_PADDING_DP)
                binding.homeScroll.updatePadding(bottom = bottom)
            }
        }
    }

    private fun setupLocalHomeHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            binding.homeAppBar.updatePadding(top = top)
            binding.homeAppBar.post { captureHomeLargeTitleMorphTargets() }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.homeAppBar.doOnLayout { captureHomeLargeTitleMorphTargets() }
        registerHomeAppBarScrollFeedback()
        val bottom = dpToPx(LIST_SCROLL_END_PADDING_DP)
        binding.homeScroll.updatePadding(bottom = bottom)
    }

    private fun registerHomeAppBarScrollFeedback() {
        appBarOffsetListener?.let { binding.homeAppBar.removeOnOffsetChangedListener(it) }
        appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            lastHomeAppBarVerticalOffset = verticalOffset
            if (verticalOffset == 0) {
                binding.homeCollapsing.post {
                    captureHomeCollapsingToolbarScrollRange()
                    captureHomeLargeTitleMorphTargets()
                }
            }
            val rangePx = collapsingToolbarScrollRangePx
            val p = if (rangePx <= 0) {
                val total = appBarLayout.totalScrollRange
                if (total <= 0) 0f
                else (-verticalOffset / total.toFloat()).coerceIn(0f, 1f)
            } else {
                val consumedPx = (-verticalOffset).coerceAtLeast(0)
                (consumedPx / rangePx.toFloat()).coerceIn(0f, 1f)
            }
            applyHomeAppBarCollapseVisuals(p)
        }
        binding.homeAppBar.addOnOffsetChangedListener(appBarOffsetListener!!)
    }

    private fun captureHomeCollapsingToolbarScrollRange() {
        val ctl = binding.homeCollapsing
        collapsingToolbarScrollRangePx =
            (ctl.height - ctl.minimumHeight).coerceAtLeast(1)
    }

    private fun applyHomeAppBarCollapseVisuals(p: Float) {
        val progress = p.coerceIn(0f, 1f)
        val morphProgress = emphasizedHomeProgress(progress, 0.12f, 0.92f)
        val compactAlphaProgress = emphasizedHomeProgress(progress, 0.48f, 0.96f)
        val largeAlphaProgress = emphasizedHomeProgress(progress, 0.08f, 0.84f)
        binding.homeHeaderScrim.alpha = progress
        binding.homeAppBarDivider.alpha = emphasizedHomeProgress(progress, 0.08f, 0.48f)
        binding.homeCompactTitle.alpha = compactAlphaProgress
        binding.homeLargeTitle.alpha = 1f - largeAlphaProgress
        binding.homeLargeTitle.pivotX = 0f
        binding.homeLargeTitle.pivotY = 0f
        binding.homeLargeTitle.translationX = largeTitleMorphDx * morphProgress
        binding.homeLargeTitle.translationY = largeTitleMorphDy * morphProgress
        binding.homeLargeTitle.scaleX = 1f + (largeTitleMorphScaleX - 1f) * morphProgress
        binding.homeLargeTitle.scaleY = 1f + (largeTitleMorphScaleY - 1f) * morphProgress
        binding.homeSummaryCard.alpha = 1f
        binding.homeSummaryCard.scaleX = 1f
        binding.homeSummaryCard.scaleY = 1f
    }

    private fun emphasizedHomeProgress(progress: Float, start: Float, end: Float): Float {
        if (progress <= start) return 0f
        if (progress >= end) return 1f
        val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun captureHomeLargeTitleMorphTargets() {
        val large = binding.homeLargeTitle
        val compact = binding.homeCompactTitle
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

    private fun animateMenuQuickTap(view: ImageView?, onEnd: () -> Unit) {
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

    private fun refreshHomeHeaderLayoutAfterOverviewTextChanged() {
        if (lastHomeAppBarVerticalOffset == 0) {
            binding.homeCollapsing.post {
                captureHomeCollapsingToolbarScrollRange()
                captureHomeLargeTitleMorphTargets()
            }
        }
    }

    private fun formatWithCommas(value: Int): String {
        return String.format("%,d", value)
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun renderStepsPermissionDeniedState() {
        binding.homeOverviewTitle.text = getString(R.string.home_permission_summary_title)
        binding.homeOverviewSteps.text = getString(R.string.step_tracking_permission_required_value)
        binding.homeOverviewProgress.progress = 0
        binding.homeOverviewDistance.text = getString(R.string.history_summary_distance, "0.0")
        binding.homeOverviewCalories.text = getString(R.string.history_summary_calories, 0)
        binding.homeOverviewHint.text = getString(R.string.home_permission_summary_body)
        binding.homeInsightPace.text = getString(R.string.home_insight_permission)
        binding.homeInsightActive.text = getString(R.string.home_insight_active_minutes, 0)
        binding.homeInsightEco.text = getString(R.string.home_insight_eco, "0.0")
        binding.buttonGrantPermission.isVisible = true
        binding.buttonChangeGoal.isVisible = false
        animateStepCardStateIfNeeded(isDenied = true)
        refreshHomeHeaderLayoutAfterOverviewTextChanged()
    }

    private fun renderStepsPermissionGrantedState() {
        val goal = viewModel.getDailyStepsGoal()
        val latestSteps = viewModel.stepsData.value?.stepsCount
        binding.buttonGrantPermission.isVisible = false
        binding.buttonChangeGoal.isVisible = true
        if (latestSteps != null) {
            renderStepsProgress(latestSteps)
        } else {
            renderHomeOverview(stepsCount = 0, goal = goal)
        }
        animateStepCardStateIfNeeded(isDenied = false)
        refreshHomeHeaderLayoutAfterOverviewTextChanged()
    }

    private fun renderStepsProgress(stepsCount: Int) {
        val goal = viewModel.getDailyStepsGoal().coerceAtLeast(1)
        renderHomeOverview(stepsCount = stepsCount, goal = goal)
        refreshHomeHeaderLayoutAfterOverviewTextChanged()
    }

    private fun renderHomeOverview(stepsCount: Int, goal: Int) {
        val safeGoal = goal.coerceAtLeast(1)
        val progress = ((stepsCount.toDouble() / safeGoal) * 100).toInt().coerceIn(0, 100)
        val distanceKm = stepsCount * KM_PER_STEP
        val calories = (stepsCount * KCAL_PER_STEP).toInt()
        val left = (safeGoal - stepsCount).coerceAtLeast(0)

        binding.homeOverviewTitle.text = getString(R.string.home_today_overview)
        binding.homeOverviewSteps.text = getString(
            R.string.home_steps_overview,
            formatWithCommas(stepsCount),
            formatWithCommas(safeGoal)
        )
        binding.homeOverviewProgress.progress = progress
        binding.homeOverviewDistance.text = getString(
            R.string.history_summary_distance,
            String.format(Locale.getDefault(), "%.1f", distanceKm)
        )
        binding.homeOverviewCalories.text = getString(
            R.string.history_summary_calories,
            calories
        )
        binding.homeOverviewHint.text = if (left == 0) {
            getString(R.string.home_goal_complete)
        } else {
            getString(R.string.home_steps_left, formatWithCommas(left))
        }
        renderHomeInsights(stepsCount = stepsCount, stepsLeft = left, distanceKm = distanceKm)
    }

    private fun renderHomeInsights(stepsCount: Int, stepsLeft: Int, distanceKm: Double) {
        val walkMinutesToGoal = ((stepsLeft + STEPS_PER_ACTIVE_MINUTE - 1) / STEPS_PER_ACTIVE_MINUTE)
            .coerceAtLeast(1)
        val activeMinutes = (stepsCount / STEPS_PER_ACTIVE_MINUTE).coerceAtLeast(0)
        val avoidedCo2Kg = distanceKm * CO2_KG_PER_WALKED_KM

        binding.homeInsightPace.text = if (stepsLeft == 0) {
            getString(R.string.home_insight_goal_done)
        } else {
            getString(R.string.home_insight_quick_walk, walkMinutesToGoal)
        }
        binding.homeInsightActive.text = getString(
            R.string.home_insight_active_minutes,
            activeMinutes
        )
        binding.homeInsightEco.text = getString(
            R.string.home_insight_eco,
            String.format(Locale.getDefault(), "%.1f", avoidedCo2Kg)
        )
    }

    private fun animateStepCardStateIfNeeded(isDenied: Boolean) {
        if (lastRenderedDeniedState == isDenied) return
        lastRenderedDeniedState = isDenied
        val shift = dpToPx(8).toFloat()
        val targets = listOf(
            binding.buttonGrantPermission,
            binding.buttonChangeGoal,
            binding.buttonHomeHistory,
            binding.homeOverviewTitle,
            binding.homeOverviewSteps,
            binding.homeOverviewProgress,
            binding.homeOverviewDistance,
            binding.homeOverviewCalories,
            binding.homeOverviewHint,
            binding.homeInsightPace,
            binding.homeInsightActive,
            binding.homeInsightEco
        )
        targets.forEach { view ->
            view.alpha = 0f
            view.translationY = shift
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(MotionTokens.DURATION_MEDIUM)
                .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                .start()
        }
    }

    private fun onGrantPermissionClicked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (hasActivityRecognitionPermission()) {
            viewModel.startCounting()
            renderStepsPermissionGrantedState()
            return
        }
        val blockedBySystem = viewModel.wasActivityPermissionSystemRequested() &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)
        if (blockedBySystem) {
            openAppSettings()
            return
        }
        requestRecognitionPermission()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun animateCardTap(view: View, onEnd: () -> Unit) {
        view.animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
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

    private fun attachTouchListenerRecursively(
        root: View,
        listener: View.OnTouchListener,
        excludedViewIds: Set<Int>
    ) {
        if (root.id !in excludedViewIds) {
            root.setOnTouchListener(listener)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                attachTouchListenerRecursively(
                    root = root.getChildAt(i),
                    listener = listener,
                    excludedViewIds = excludedViewIds
                )
            }
        }
    }

    private fun showDailyStepsPlanDialog() {
        val fm = activity?.supportFragmentManager ?: return
        if (fm.findFragmentByTag(DailyStepsPlanDialogFragment.TAG) != null) return
        DailyStepsPlanDialogFragment.newInstance(viewModel.getDailyStepsGoal())
            .show(fm, DailyStepsPlanDialogFragment.TAG)
    }

    private fun setDailyStepsPlanResultListener() {
        parentFragmentManager.setFragmentResultListener(
            DailyStepsPlanDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val goal = bundle.getInt(DailyStepsPlanDialogFragment.BUNDLE_KEY_GOAL, -1)
            if (goal <= 0) return@setFragmentResultListener
            viewModel.saveDailyStepsGoal(goal)
            if (hasActivityRecognitionPermission()) {
                val currentSteps = viewModel.stepsData.value?.stepsCount ?: 0
                renderStepsProgress(currentSteps)
                showToast(getString(R.string.daily_steps_plan_saved, formatWithCommas(goal)))
            }
        }
    }


    private fun showAccessDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            viewModel.startCounting()
            return
        }
        if (hasActivityRecognitionPermission()) {
            startStepTrackingService()
            viewModel.startCounting()
            return
        }
        viewModel.loadActualData()
        if (viewModel.isActivityPermissionPrePromptShown()) return
        viewModel.markActivityPermissionPrePromptShown()
        val fm = activity?.supportFragmentManager ?: return
        if (fm.findFragmentByTag(AccessDialogFragment.TAG) != null) return
        AccessDialogFragment().show(fm, AccessDialogFragment.TAG)
        fm.setFragmentResultListener(
            AccessDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val result = bundle.getBoolean(AccessDialogFragment.BUNDLE_KEY)
            if (result) {
                requestRecognitionPermission()
            } else {
                stopStepTracking()
                viewModel.loadActualData()
                renderStepsPermissionDeniedState()
                showToast("Step tracking is disabled. You can enable permission later.")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRecognitionPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startStepTrackingService()
            viewModel.startCounting()
            renderStepsPermissionGrantedState()
            showToast("Permission is granted")
        } else {
            viewModel.markActivityPermissionSystemRequested()
            recognitionPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACTIVITY_RECOGNITION
                )
            )
        }
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (hasActivityRecognitionPermission()) {
                startStepTrackingService()
                viewModel.startCounting()
                renderStepsPermissionGrantedState()
                showToast("Permission is granted")
            } else {
                stopStepTracking()
                renderStepsPermissionDeniedState()
            }
        }

    private fun startStepTrackingService() {
        StepTrackingService.start(requireContext())
    }

    private fun stopStepTracking() {
        StepTrackingService.stop(requireContext())
        viewModel.stopCounting()
    }

    private fun openAppSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + requireActivity().packageName)
        )
        resultLauncher.launch(appSettingsIntent)
    }


    private fun playEnterAnimationIfNeeded() {
        if (hasPlayedEnterAnimation) return
        hasPlayedEnterAnimation = true
        val views = listOf(binding.homeSummaryCard, binding.cardEcoTask)
        val shift = dpToPx(12).toFloat()
        views.forEachIndexed { index, target ->
            target.alpha = 0f
            target.translationY = shift
            target.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(MotionTokens.DURATION_MEDIUM)
                .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                .start()
        }
    }

    private fun showSkippedTaskSnack() {
        snackBar?.dismiss()
        snackBar = Snackbar.make(
            binding.root,
            getString(R.string.task_skipped_message),
            Snackbar.LENGTH_SHORT
        )
            .setTextColor(Color.BLACK)
            .setBackgroundTint(Color.WHITE)
        snackBar?.show()
    }

    private fun restoreScrollIfNeeded() {
        val offset = savedHomeScrollY
        if (offset <= 0) return
        binding.homeScroll.post {
            binding.homeScroll.scrollTo(0, offset)
        }
    }

    override fun onDestroyView() {
        appBarOffsetListener?.let { binding.homeAppBar.removeOnOffsetChangedListener(it) }
        appBarOffsetListener = null
        savedHomeScrollY = binding.homeScroll.scrollY
        birdWiggleJob?.cancel()
        birdWiggleJob = null
        shredderJob?.cancel()
        shredderJob = null
        super.onDestroyView()
        if (snackBar != null) {
            snackBar?.dismiss()
            snackBar = null
        }
    }

    companion object {
        const val TAG = "HomeFragment.tag"
        private const val LIST_SCROLL_END_PADDING_DP = 40
        private const val KM_PER_STEP = 0.00075
        private const val KCAL_PER_STEP = 0.04
        private const val STEPS_PER_ACTIVE_MINUTE = 100
        private const val CO2_KG_PER_WALKED_KM = 0.12
        private var savedHomeScrollY: Int = 0
    }
}