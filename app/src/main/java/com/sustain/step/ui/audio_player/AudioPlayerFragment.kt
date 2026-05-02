package com.sustain.step.ui.audio_player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.sustain.step.MainActivity
import com.sustain.step.R
import com.sustain.step.databinding.FragmentAudioPlayerBinding
import com.sustain.step.di.factory
import com.sustain.step.ui.base.BaseFragment
import com.sustain.step.ui.base.MotionTokens
import com.sustain.step.ui.base.navigation.mainNavigator
import com.sustain.step.ui.menu.MenuFragment
import com.sustain.step.ui.audio_player.service.AudioPlaybackService
import com.sustain.step.ui.audio_player.service.PlaybackStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayerFragment :
    BaseFragment<FragmentAudioPlayerBinding>(FragmentAudioPlayerBinding::inflate) {

    companion object {
        const val TAG = "AudioPlayerFragment.tag"
    }

    override val viewModel by viewModels<AudioPlayerViewModel> { factory() }
    private val audioAdapter = AudioAdapter(selectSomeTrack = {
        pendingPlayUri = it
        isPlaying = true
        viewModel.playSong(it)
    }, toggleFavorite = {
        viewModel.toggleFavorite(it)
    })
    private var snackBar: Snackbar? = null
    private var isPlaying = false
    private var currentTrackUri: Uri? = null
    private var pendingPlayUri: Uri? = null
    private var seekBarJob: Job? = null
    private var lastRenderedSongs: List<AudioData> = emptyList()
    private var lastSongsSignature: List<String> = emptyList()
    private var hasPlayedPlayerEnterAnimation = false
    private var isCoverReady = false
    private var lastKnownAudioPermissionGranted: Boolean? = null
    private var currentCardState: AudioCardState? = null
    private var isSongListReadyForDisplay = false
    private var selectedAudioFilter = AudioFilter.ALL
    private var allSongs: List<AudioData> = emptyList()
    private var visibleSongs: List<AudioData> = emptyList()
    private var appBarOffsetListener: AppBarLayout.OnOffsetChangedListener? = null
    private var audioCollapsingRangePx = 0
    private var audioLargeTitleMorphDx = 0f
    private var audioLargeTitleMorphDy = 0f
    private var audioLargeTitleMorphScaleX = 1f
    private var audioLargeTitleMorphScaleY = 1f
    private var lastAudioHeaderProgress = 0f

    private enum class AudioCardState {
        PermissionRequired,
        Player,
        Empty
    }

    private enum class AudioFilter {
        ALL,
        FAVORITES,
        DOWNLOADS,
        MUSIC,
        RECORDINGS,
        RECENT
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLocalAudioHeader()
        setupAudioFilters()
        binding.audioMenuButton.setOnClickListener {
            animateQuickTap(binding.audioMenuButton) {
                mainNavigator().navigate(MenuFragment.TAG)
            }
        }
        binding.apply {
            playerContainer.isInvisible = true
            songList.isVisible = false
            songList.apply {
                adapter = audioAdapter
                isMotionEventSplittingEnabled = false
                val itemAnimator = this.itemAnimator
                if (itemAnimator is DefaultItemAnimator) {
                    itemAnimator.supportsChangeAnimations = false
                }
            }
            playerContainer.post {
                updateSongListTopPadding()
            }
            seekBar.isEnabled = false
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                    if (p2) {
                        startPlaybackService(
                            action = AudioPlaybackService.ACTION_SEEK,
                            extrasBuilder = {
                                putExtra(AudioPlaybackService.EXTRA_POSITION_MS, p1)
                            }
                        )
                    }
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                }
            })
            viewModel.liveDataSongs.observe(viewLifecycleOwner) { list ->
                if (!hasAudioPermission()) return@observe
                allSongs = list
                renderAudioForSelectedFilter()
            }
            buttonGrantAudioAccess.setOnClickListener { requestAudioPermissionFlow() }
            buttonRefreshAudioList.setOnClickListener { checkPermission() }
            buttonFavoriteCurrent.setOnClickListener {
                currentTrackUri?.let(viewModel::toggleFavorite)
            }
        }
        observePlaybackState()
        checkPermission()
    }

    private fun setupAudioFilters() {
        listOf(
            AudioFilter.ALL to binding.chipAudioAll,
            AudioFilter.FAVORITES to binding.chipAudioFavorites,
            AudioFilter.DOWNLOADS to binding.chipAudioDownloads,
            AudioFilter.MUSIC to binding.chipAudioMusic,
            AudioFilter.RECORDINGS to binding.chipAudioRecordings,
            AudioFilter.RECENT to binding.chipAudioRecent
        ).forEach { (filter, chip) ->
            chip.setOnClickListener {
                if (selectedAudioFilter == filter) return@setOnClickListener
                selectedAudioFilter = filter
                renderAudioFilters()
                renderAudioForSelectedFilter()
            }
        }
        renderAudioFilters()
    }

    private fun renderAudioFilters() {
        listOf(
            AudioFilter.ALL to binding.chipAudioAll,
            AudioFilter.FAVORITES to binding.chipAudioFavorites,
            AudioFilter.DOWNLOADS to binding.chipAudioDownloads,
            AudioFilter.MUSIC to binding.chipAudioMusic,
            AudioFilter.RECORDINGS to binding.chipAudioRecordings,
            AudioFilter.RECENT to binding.chipAudioRecent
        ).forEach { (filter, chip) ->
            chip.setChipSelected(filter == selectedAudioFilter)
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

    private fun renderAudioForSelectedFilter() {
        val list = allSongs
        if (list.isEmpty()) {
            showEmptyAudioState()
            binding.songList.visibility = View.GONE
            visibleSongs = emptyList()
            isCoverReady = false
            stopPlayback()
            isPlaying = false
            currentTrackUri = null
            binding.buttonPlay.setImageResource(R.drawable.icon_play_big)
            return
        }

        val filteredList = filterSongs(list)
        visibleSongs = filteredList
        if (filteredList.isEmpty()) {
            audioAdapter.submitList(emptyList())
            binding.songList.isVisible = false
            val activeSong = findActiveSong(list)
            if (activeSong == null) {
                showPlayerState()
                return
            }
            bindCurrentSong(activeSong)
            showPlayerState()
            if (currentTrackUri != activeSong.uri) {
                currentTrackUri = activeSong.uri
                loadCover(activeSong.uri)
            }
            binding.buttonNext.setOnClickListener { playAdjacentInVisibleQueue(forward = true) }
            binding.buttonPrevious.setOnClickListener { playAdjacentInVisibleQueue(forward = false) }
            controlSound(activeSong)
            return
        }

        val songsSignature = filteredList.map { "${it.uri}:${it.isFavorite}:${it.isPlaying}" }
        val structureChanged = songsSignature != lastSongsSignature
        val listChanged = filteredList != lastRenderedSongs
        if (listChanged || structureChanged) {
            lastRenderedSongs = filteredList
            lastSongsSignature = songsSignature
        }
        binding.seekBar.isEnabled = true
        val preferredUri = pendingPlayUri ?: PlaybackStateStore.state.value.currentUri
        val pendingSong = pendingPlayUri?.let { pendingUri ->
            filteredList.find { it.uri == pendingUri }
        }
        val activeSong = findActiveSong(list)
        val currentSong = pendingSong ?: activeSong ?: filteredList.find { it.uri == preferredUri }
            ?: filteredList.find { it.isPlaying } ?: filteredList.first()
        bindCurrentSong(currentSong)
        showPlayerState()
        val currentSongInVisibleList = filteredList.any { it.uri == currentSong.uri }
        if (structureChanged && currentSongInVisibleList) {
            syncQueueWithService(filteredList, currentSong.uri)
        }
        if (currentTrackUri != currentSong.uri) {
            loadCover(currentSong.uri)
        }
        if (currentTrackUri != currentSong.uri) {
            currentTrackUri = currentSong.uri
            if (pendingPlayUri == currentSong.uri) {
                playTrack(currentSong)
                pendingPlayUri = null
            }
        }
        binding.buttonNext.setOnClickListener { playAdjacentInVisibleQueue(forward = true) }
        binding.buttonPrevious.setOnClickListener { playAdjacentInVisibleQueue(forward = false) }
        controlSound(currentSong)
        if (filteredList.isNotEmpty()) {
            if (listChanged || audioAdapter.currentList != filteredList) {
                audioAdapter.submitList(filteredList) {
                    syncPlayerAndSongListLayout(showList = true)
                }
            } else {
                syncPlayerAndSongListLayout(showList = true)
            }
        }
    }

    private fun findActiveSong(list: List<AudioData>): AudioData? {
        val activeUri = PlaybackStateStore.state.value.currentUri
        return list.find { it.uri == activeUri } ?: list.find { it.isPlaying }
    }

    private fun playAdjacentInVisibleQueue(forward: Boolean) {
        val queue = visibleSongs
        if (queue.isEmpty()) {
            return
        }
        val currentUri = PlaybackStateStore.state.value.currentUri ?: currentTrackUri
        val currentIndex = queue.indexOfFirst { it.uri == currentUri }
        if (currentIndex == -1) {
            val target = if (forward) queue.first() else queue.last()
            syncQueueWithService(queue, target.uri)
            currentTrackUri = target.uri
            playTrack(target)
            return
        }
        syncQueueWithService(queue, queue[currentIndex].uri)
        startPlaybackService(
            if (forward) AudioPlaybackService.ACTION_NEXT else AudioPlaybackService.ACTION_PREVIOUS
        )
    }

    private fun filterSongs(list: List<AudioData>): List<AudioData> {
        return when (selectedAudioFilter) {
            AudioFilter.ALL -> list
            AudioFilter.FAVORITES -> list.filter { it.isFavorite }
            AudioFilter.DOWNLOADS -> list.filter { it.matchesFolder("download") }
            AudioFilter.MUSIC -> list.filter { it.matchesFolder("music") }
            AudioFilter.RECORDINGS -> list.filter {
                it.matchesFolder("record") || it.matchesFolder("voice")
            }
            AudioFilter.RECENT -> list.sortedByDescending { it.dateAddedSeconds }.take(30)
        }
    }

    private fun AudioData.matchesFolder(query: String): Boolean {
        return folderName.contains(query, ignoreCase = true) ||
            folderPath.contains(query, ignoreCase = true)
    }

    private fun bindCurrentSong(song: AudioData) {
        binding.title.text = song.title
        binding.artistName.text = song.artist
        binding.totalTime.text = formatDuration(song.duration)
        binding.buttonFavoriteCurrent.setImageResource(
            if (song.isFavorite) R.drawable.icon_heart_filled else R.drawable.icon_heart_outline
        )
    }

    override fun onResume() {
        super.onResume()
        val hasPermission = hasAudioPermission()
        lastKnownAudioPermissionGranted = hasPermission
        if (hasPermission) {
            viewModel.getAudio()
        } else {
            showPermissionDeniedState()
        }
    }

    private fun setupLocalAudioHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            binding.audioAppBar.updatePadding(top = statusTop)
            binding.audioAppBar.post { captureAudioLargeTitleMorphTargets() }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.audioAppBar.doOnLayout {
            captureAudioLargeTitleMorphTargets()
            captureAudioCollapsingRange()
        }
        registerAudioHeaderScrollFeedback()
    }

    private fun registerAudioHeaderScrollFeedback() {
        appBarOffsetListener?.let { binding.audioAppBar.removeOnOffsetChangedListener(it) }
        appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (verticalOffset == 0) {
                binding.audioCollapsing.post {
                    captureAudioCollapsingRange()
                    captureAudioLargeTitleMorphTargets()
                }
            }
            val range = audioCollapsingRangePx.takeIf { it > 0 } ?: appBarLayout.totalScrollRange
            val p = if (range <= 0) 0f else (-verticalOffset / range.toFloat()).coerceIn(0f, 1f)
            applyAudioHeaderVisuals(p)
        }
        binding.audioAppBar.addOnOffsetChangedListener(appBarOffsetListener!!)
    }

    private fun captureAudioCollapsingRange() {
        audioCollapsingRangePx =
            (binding.audioCollapsing.height - binding.audioCollapsing.minimumHeight).coerceAtLeast(1)
    }

    private fun applyAudioHeaderVisuals(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        lastAudioHeaderProgress = p
        val morph = emphasizedProgress(p, 0.12f, 0.92f)
        val compactAlpha = emphasizedProgress(p, 0.48f, 0.96f)
        val largeAlpha = 1f - emphasizedProgress(p, 0.08f, 0.84f)
        val playerHideProgress = emphasizedProgress(p, 0.08f, 0.88f)
        val miniPlayerProgress =
            if (currentCardState == AudioCardState.Player) emphasizedProgress(p, 0.72f, 0.96f) else 0f
        binding.audioHeaderScrim.alpha = p
        binding.audioAppBarDivider.alpha = emphasizedProgress(p, 0.08f, 0.48f)
        binding.audioCompactTitle.alpha = compactAlpha
        binding.audioLargeTitle.alpha = largeAlpha
        binding.audioLargeTitle.pivotX = 0f
        binding.audioLargeTitle.pivotY = 0f
        binding.audioLargeTitle.translationX = audioLargeTitleMorphDx * morph
        binding.audioLargeTitle.translationY = audioLargeTitleMorphDy * morph
        binding.audioLargeTitle.scaleX = 1f + (audioLargeTitleMorphScaleX - 1f) * morph
        binding.audioLargeTitle.scaleY = 1f + (audioLargeTitleMorphScaleY - 1f) * morph
        binding.playerContainer.alpha = 1f - playerHideProgress
        val playerScale = 1f - 0.04f * playerHideProgress
        binding.playerContainer.scaleX = playerScale
        binding.playerContainer.scaleY = playerScale
        val shouldShowMiniPlayer = miniPlayerProgress > 0.01f
        (activity as? MainActivity)?.setAudioScreenMiniPlayerVisible(shouldShowMiniPlayer)
        updateSongListTopPadding(miniPlayerVisible = shouldShowMiniPlayer)
    }


    private fun captureAudioLargeTitleMorphTargets() {
        val large = binding.audioLargeTitle
        val compact = binding.audioCompactTitle
        if (large.width == 0 || large.height == 0 || compact.width == 0 || compact.height == 0) return
        val largeLocation = IntArray(2)
        val compactLocation = IntArray(2)
        large.getLocationInWindow(largeLocation)
        compact.getLocationInWindow(compactLocation)
        audioLargeTitleMorphDx = (compactLocation[0] - largeLocation[0]).toFloat()
        audioLargeTitleMorphDy = (compactLocation[1] - largeLocation[1]).toFloat()
        audioLargeTitleMorphScaleX =
            (compact.width.toFloat() / large.width.toFloat()).coerceIn(0.6f, 1f)
        audioLargeTitleMorphScaleY =
            (compact.textSize / large.textSize).coerceIn(0.6f, 1f)
    }

    private fun emphasizedProgress(progress: Float, start: Float, end: Float): Float {
        if (progress <= start) return 0f
        if (progress >= end) return 1f
        val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun animateQuickTap(view: View?, onEnd: () -> Unit) {
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

    private fun loadCover(uri: Uri) {
        isCoverReady = false
        binding.songCover.isInvisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val imageBytes = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(requireContext(), uri)
                        retriever.embeddedPicture
                    } finally {
                        retriever.release()
                    }
                }.getOrNull()
            }
            if (!isAdded) return@launch
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    binding.songCover.setImageBitmap(bitmap)
                    isCoverReady = true
                    binding.songCover.isInvisible = false
                    return@launch
                }
            }
            binding.songCover.setImageResource(R.drawable.img_track)
            isCoverReady = true
            binding.songCover.isInvisible = false
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun controlSound(track: AudioData) {
        val togglePlayback = View.OnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (currentTrackUri != track.uri) {
                    currentTrackUri = track.uri
                    playTrack(track)
                    isPlaying = true
                    return@launch
                }
                if (isPlaying) {
                    isPlaying = false
                    startPlaybackService(AudioPlaybackService.ACTION_PAUSE)
                    binding.buttonPlay.setImageResource(R.drawable.icon_play_big)
                } else {
                    isPlaying = true
                    playTrack(track)
                    binding.buttonPlay.setImageResource(R.drawable.icon_pause)
                }
            }
        }
        binding.buttonPlay.setOnClickListener(togglePlayback)
    }

    private fun initSeekBar() {
        seekBarJob?.cancel()
        seekBarJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val state = PlaybackStateStore.state.value
                binding.seekBar.max = state.durationMs.coerceAtLeast(0)
                binding.seekBar.progress =
                    state.positionMs.coerceIn(0, state.durationMs.coerceAtLeast(0))
                binding.currentTime.text = formatDuration(state.positionMs.toLong())
                binding.totalTime.text = formatDuration(state.durationMs.toLong())
                delay(250)
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private fun showAccessDialog() {
        val fm = activity?.supportFragmentManager ?: return
        if (fm.findFragmentByTag(AccessMusicDialogFragment.TAG) != null) return
        AccessMusicDialogFragment().show(
            fm,
            AccessMusicDialogFragment.TAG
        )
        fm.setFragmentResultListener(
            AccessMusicDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val result = bundle.getBoolean(AccessMusicDialogFragment.BUNDLE_KEY)
            if (result) {
                requestAudioPermissionFlow()
            } else {
                showPermissionDeniedState()
            }
        }
    }

    private fun checkPermission() {
        if (hasAudioPermission()) {
            viewModel.getAudio()
            return
        }
        showPermissionDeniedState()
        if (!viewModel.isAudioPermissionPrePromptShown()) {
            viewModel.markAudioPermissionPrePromptShown()
            showAccessDialog()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            viewModel.markAudioPermissionSystemRequested()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (permissions[Manifest.permission.READ_MEDIA_AUDIO] == true) {
                    viewModel.getAudio()
                } else {
                    showPermissionDeniedState()
                }
            } else {
                if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
                    viewModel.getAudio()
                } else {
                    showPermissionDeniedState()
                }
            }
        }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            val hasAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            if (hasAudioPermission) {
                viewModel.getAudio()
            } else {
                showPermissionDeniedState()
            }
        }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAudioPermissionFlow() {
        if (hasAudioPermission()) {
            viewModel.getAudio()
            return
        }
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val blockedBySystem = viewModel.wasAudioPermissionSystemRequested() &&
            !shouldShowRequestPermissionRationale(audioPermission)
        if (blockedBySystem) {
            openAppSettings()
            return
        }
        val permissionsToRequest = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }

            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
        requestPermissionLauncher.launch(permissionsToRequest)
    }

    private fun openAppSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + requireContext().packageName)
        )
        resultLauncher.launch(appSettingsIntent)
    }

    private fun showPermissionDeniedState() {
        renderAudioCardState(
            state = AudioCardState.PermissionRequired,
            title = getString(R.string.audio_access_not_granted_title),
            subtitle = getString(R.string.audio_permission_required),
            showGrantButton = true,
            showRefreshButton = false
        )
    }

    private fun showEmptyAudioState() {
        renderAudioCardState(
            state = AudioCardState.Empty,
            title = getString(R.string.no_audio_files_found),
            subtitle = getString(R.string.audio_empty_subtitle),
            showGrantButton = false,
            showRefreshButton = true
        )
    }

    private fun showPlayerState() {
        renderAudioCardState(
            state = AudioCardState.Player,
            title = binding.title.text,
            subtitle = binding.artistName.text,
            showGrantButton = false,
            showRefreshButton = false
        )
    }

    private fun renderAudioCardState(
        state: AudioCardState,
        title: CharSequence,
        subtitle: CharSequence,
        showGrantButton: Boolean,
        showRefreshButton: Boolean
    ) {
        val previousState = currentCardState
        currentCardState = state
        renderAudioStateChrome(state)
        binding.title.text = title
        binding.artistName.text = subtitle
        if (state == AudioCardState.Player && !hasPlayedPlayerEnterAnimation && lastAudioHeaderProgress <= 0.05f) {
            preparePlayerContentForEnterAnimation()
        }
        if (previousState == state) return
        binding.songList.isVisible = false
        isSongListReadyForDisplay = false

        cancelAudioCardAnimations()
        val interactiveViews = listOf(
            binding.seekBar,
            binding.playerControls,
            binding.buttonGrantAudioAccess,
            binding.buttonRefreshAudioList
        )
        if (previousState == null) {
            interactiveViews.forEach { view ->
                view.isVisible = false
                view.alpha = 1f
            }
        } else {
            interactiveViews.filter { it.isVisible }.forEach { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(MotionTokens.DURATION_MEDIUM / 2)
                    .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                    .withEndAction {
                        view.isVisible = false
                        view.alpha = 1f
                    }
                    .start()
            }
        }

        if (state != AudioCardState.Player) {
            isCoverReady = true
            binding.songCover.setImageResource(R.drawable.img_track)
            binding.songCover.isVisible = true
            (activity as? MainActivity)?.setAudioScreenMiniPlayerVisible(false)
        }
        binding.playerContainer.isInvisible = false
        updateSongListTopPadding()
        binding.audioAppBar.post { applyAudioHeaderVisuals(lastAudioHeaderProgress) }
        if (state == AudioCardState.Player) {
            syncPlayerAndSongListLayout(showList = false)
        }
        if (previousState == null && state != AudioCardState.Player) {
            binding.songCover.alpha = 1f
            binding.songCover.isVisible = true
            listOf(binding.title, binding.artistName).forEach { view ->
                fadeInView(view)
            }
        } else if (state != AudioCardState.Player) {
            fadeInAudioCardText()
        }

        binding.playerContainer.postDelayed({
            when (state) {
                AudioCardState.PermissionRequired -> fadeInView(binding.buttonGrantAudioAccess)
                AudioCardState.Empty -> fadeInView(binding.buttonRefreshAudioList)
                AudioCardState.Player -> {
                    playPlayerContentEnterAnimationIfNeeded()
                    syncPlayerAndSongListLayout(showList = true)
                }
            }
        }, if (previousState == null) 0L else MotionTokens.DURATION_MEDIUM / 2)
    }

    private fun renderAudioStateChrome(state: AudioCardState) {
        val isPlayer = state == AudioCardState.Player
        binding.audioFilterScroll.isVisible = isPlayer
        binding.buttonFavoriteCurrent.isVisible = isPlayer
        binding.seekBar.isVisible = isPlayer
        binding.audioTimeRow.isVisible = isPlayer
        binding.currentTime.isVisible = isPlayer
        binding.totalTime.isVisible = isPlayer
        binding.playerControls.isVisible = isPlayer
        binding.playerStatusLabel.text = getString(
            when (state) {
                AudioCardState.PermissionRequired -> R.string.access_required
                AudioCardState.Empty -> R.string.no_audio_files_found
                AudioCardState.Player -> R.string.audio_now_playing
            }
        )
        if (!isPlayer) {
            binding.currentTime.text = "0:00"
            binding.totalTime.text = "0:00"
        }
    }

    private fun syncPlayerAndSongListLayout(showList: Boolean) {
        binding.playerContainer.post {
            updateSongListTopPadding()
            if (showList) {
                if (!isSongListReadyForDisplay) {
                    isSongListReadyForDisplay = true
                }
                binding.songList.isVisible = true
            }
        }
    }

    private fun updateSongListTopPadding() {
        updateSongListTopPadding(
            miniPlayerVisible = lastAudioHeaderProgress >= 0.72f &&
                currentCardState == AudioCardState.Player
        )
    }

    private fun updateSongListTopPadding(miniPlayerVisible: Boolean) {
        binding.songList.updatePadding(
            top = dpToPx(8),
            bottom = dpToPx(if (miniPlayerVisible) 126 else 16)
        )
    }

    private fun fadeInAudioCardText() {
        listOf(binding.songCover, binding.title, binding.artistName).forEach { view ->
            fadeInView(view)
        }
    }

    private fun fadeInView(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.isVisible = true
        view.animate()
            .alpha(1f)
            .setDuration(MotionTokens.DURATION_MEDIUM)
            .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
            .start()
    }

    private fun cancelAudioCardAnimations() {
        listOf(
            binding.playerStatusLabel,
            binding.buttonFavoriteCurrent,
            binding.songCover,
            binding.title,
            binding.artistName,
            binding.seekBar,
            binding.audioTimeRow,
            binding.playerControls,
            binding.buttonGrantAudioAccess,
            binding.buttonRefreshAudioList
        ).forEach { view ->
            view.animate().cancel()
        }
    }

    private fun preparePlayerContentForEnterAnimation() {
        listOf(
            binding.playerStatusLabel,
            binding.buttonFavoriteCurrent,
            binding.songCover,
            binding.title,
            binding.artistName,
            binding.seekBar,
            binding.audioTimeRow,
            binding.playerControls
        ).forEach { view ->
            view.animate().cancel()
            view.alpha = 0f
            view.scaleX = 0.96f
            view.scaleY = 0.96f
        }
    }

    private fun playPlayerContentEnterAnimationIfNeeded() {
        if (hasPlayedPlayerEnterAnimation) return
        hasPlayedPlayerEnterAnimation = true
        binding.playerContainer.animate().cancel()
        binding.playerContainer.translationY = 0f
        applyAudioHeaderVisuals(lastAudioHeaderProgress)
        if (lastAudioHeaderProgress > 0.05f) return

        val revealItems = listOf(
            binding.playerStatusLabel to 0L,
            binding.buttonFavoriteCurrent to 20L,
            binding.songCover to 45L,
            binding.title to 90L,
            binding.artistName to 110L,
            binding.seekBar to 140L,
            binding.audioTimeRow to 155L,
            binding.playerControls to 175L
        )
        revealItems.forEach { (view, delayMs) ->
            view.animate().cancel()
            view.alpha = 0f
            view.scaleX = 0.96f
            view.scaleY = 0.96f
            view.isVisible = true
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delayMs)
                .setDuration(MotionTokens.DURATION_MEDIUM)
                .setInterpolator(MotionTokens.STANDARD_INTERPOLATOR)
                .start()
        }
    }

    private fun stopPlayback() {
        startPlaybackService(AudioPlaybackService.ACTION_STOP)
        currentTrackUri = null
    }

    private fun playTrack(track: AudioData) {
        val currentList = visibleSongs.ifEmpty { viewModel.liveDataSongs.value.orEmpty() }
        val index = currentList.indexOfFirst { it.uri == track.uri }
        startPlaybackService(
            action = AudioPlaybackService.ACTION_PLAY,
            extrasBuilder = {
                putExtra(AudioPlaybackService.EXTRA_URI, track.uri.toString())
                putExtra(AudioPlaybackService.EXTRA_TITLE, track.title)
                putExtra(AudioPlaybackService.EXTRA_ARTIST, track.artist)
                putExtra(AudioPlaybackService.EXTRA_INDEX, index)
            }
        )
    }

    private fun syncQueueWithService(list: List<AudioData>, selectedUri: Uri) {
        if (list.isEmpty()) return
        val selectedIndex = list.indexOfFirst { it.uri == selectedUri }.coerceAtLeast(0)
        startPlaybackService(
            action = AudioPlaybackService.ACTION_SET_QUEUE,
            extrasBuilder = {
                putStringArrayListExtra(
                    AudioPlaybackService.EXTRA_QUEUE_URIS,
                    ArrayList(list.map { it.uri.toString() })
                )
                putStringArrayListExtra(
                    AudioPlaybackService.EXTRA_QUEUE_TITLES,
                    ArrayList(list.map { it.title })
                )
                putStringArrayListExtra(
                    AudioPlaybackService.EXTRA_QUEUE_ARTISTS,
                    ArrayList(list.map { it.artist })
                )
                putExtra(AudioPlaybackService.EXTRA_INDEX, selectedIndex)
            }
        )
    }

    private fun startPlaybackService(
        action: String,
        extrasBuilder: Intent.() -> Unit = {}
    ) {
        val intent = Intent(requireContext(), AudioPlaybackService::class.java)
            .setAction(action)
            .apply(extrasBuilder)
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun observePlaybackState() {
        initSeekBar()
        viewLifecycleOwner.lifecycleScope.launch {
            PlaybackStateStore.state.collectLatest { state ->
                isPlaying = state.isPlaying
                if (state.currentUri != null && currentTrackUri != state.currentUri) {
                    currentTrackUri = state.currentUri
                    viewModel.playSong(state.currentUri)
                    loadCover(state.currentUri)
                }
                if (state.title.isNotBlank()) {
                    binding.title.text = state.title
                }
                if (state.artist.isNotBlank()) {
                    binding.artistName.text = state.artist
                }
                val playIcon = if (state.isPlaying) R.drawable.icon_pause else R.drawable.icon_play_big
                binding.buttonPlay.setImageResource(playIcon)
            }
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setAudioScreenMiniPlayerVisible(false)
        appBarOffsetListener?.let { binding.audioAppBar.removeOnOffsetChangedListener(it) }
        appBarOffsetListener = null
        seekBarJob?.cancel()
        seekBarJob = null
        currentCardState = null
        super.onDestroyView()
    }


}