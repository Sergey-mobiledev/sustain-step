package com.sustain.step

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import com.sustain.step.databinding.ActivityMainBinding
import com.sustain.step.ui.audio_player.AudioPlayerFragment
import com.sustain.step.ui.audio_player.service.AudioPlaybackService
import com.sustain.step.ui.audio_player.service.PlaybackState
import com.sustain.step.ui.audio_player.service.PlaybackStateStore
import com.sustain.step.ui.base.navigation.MainNavigator
import com.sustain.step.ui.history.HistoryFragment
import com.sustain.step.ui.home.HomeFragment
import com.sustain.step.ui.menu.MenuFragment
import com.sustain.step.ui.splash.SplashFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val navigator by viewModels<MainNavigator> {
        ViewModelProvider.AndroidViewModelFactory(
            application
        )
    }
    private var doubleClick = false
    private var f: Fragment? = null
    private var statusBarInsetTop: Int = 0
    private var bottomInset: Int = 0
    private var latestPlaybackState = PlaybackState()
    private var forceMiniPlayerOnAudioScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarInsetTop = systemBars.top
            bottomInset = systemBars.bottom
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            val currentFragment = supportFragmentManager.findFragmentById(R.id.container)
            val isSplash = currentFragment is SplashFragment
            val isMenu = currentFragment is MenuFragment
            val isHistory = currentFragment is HistoryFragment
            val isHome = currentFragment is HomeFragment
            val isAudio = currentFragment is AudioPlayerFragment
            binding.container.updatePadding(
                top = if (isSplash || isMenu || isHistory || isHome || isAudio) 0 else statusBarInsetTop
            )
            binding.miniPlayer.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = dpToPx(16) + bottomInset
            }
            insets
        }
        if (savedInstanceState == null) {
            navigator.launchFragment(this, SplashFragment.TAG, addToBackStack = false)
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentCallbacks, false)
        observeMiniPlayerState()
        setupMiniPlayerClicks()
    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentCallbacks)
    }

    override fun onResume() {
        super.onResume()
        navigator.whenActivityActive.mainActivity = this
    }

    override fun onPause() {
        super.onPause()
        navigator.whenActivityActive.mainActivity = null
    }

    private val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: android.view.View,
            savedInstanceState: Bundle?
        ) {
            super.onFragmentViewCreated(fm, f, v, savedInstanceState)
            if (f is DialogFragment) {
                return
            }
            this@MainActivity.f = f
            binding.apply {
                when (this@MainActivity.f) {
                    is HomeFragment -> {
                        binding.container.updatePadding(top = 0)
                        window.setBackgroundDrawableResource(R.drawable.home)
                    }

                    is HistoryFragment -> {
                        binding.container.updatePadding(top = 0)
                    }

                    is AudioPlayerFragment -> {
                        binding.container.updatePadding(top = 0)
                    }

                    is SplashFragment -> {
                        binding.container.updatePadding(top = 0)
                        binding.container.translationY = 0f
                    }

                    is MenuFragment -> {
                        binding.container.updatePadding(top = 0)
                        binding.container.translationY = 0f
                    }
                }
                renderMiniPlayer(latestPlaybackState)
            }
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val count = supportFragmentManager.backStackEntryCount
            if (count == 0) {
                finish()
                return
            }
            if (f is MenuFragment) {
                supportFragmentManager.popBackStack()
                return
            }
            if (count == 1 || f is HomeFragment) {
                if (doubleClick) {
                    finish()
                    return
                }
                doubleClick = true
                Toast.makeText(
                    this@MainActivity,
                    "Click BACK again to exit",
                    Toast.LENGTH_SHORT
                ).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    doubleClick = false
                }, 2000)
            } else {
                if (f is AudioPlayerFragment || f is HistoryFragment) {
                    navigator.navigate(HomeFragment.TAG)
                } else {
                    supportFragmentManager.popBackStack()
                }
            }
        }
    }

    fun setHeaderScrollProgress(progress: Float) {
        // Legacy API for fragments that used global header progress.
        // Intentionally no-op: each fragment now owns its local collapsing header.
    }

    private fun showMenuFragment() {
        if (f is MenuFragment) return
        binding.container.updatePadding(top = 0)
        navigator.navigate(MenuFragment.TAG)
    }

    private fun setupMiniPlayerClicks() {
        binding.miniPlayer.setOnClickListener {
            if (f !is AudioPlayerFragment) {
                navigator.navigate(AudioPlayerFragment.TAG)
            }
        }
        binding.miniPlayerPrevious.setOnClickListener {
            startPlaybackService(AudioPlaybackService.ACTION_PREVIOUS)
        }
        binding.miniPlayerPlayPause.setOnClickListener {
            startPlaybackService(AudioPlaybackService.ACTION_TOGGLE)
        }
        binding.miniPlayerNext.setOnClickListener {
            startPlaybackService(AudioPlaybackService.ACTION_NEXT)
        }
        binding.miniPlayerProgress.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                startPlaybackService(
                    AudioPlaybackService.ACTION_SEEK,
                    android.content.Intent().apply {
                        putExtra(AudioPlaybackService.EXTRA_POSITION_MS, progress)
                    }
                )
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun observeMiniPlayerState() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                PlaybackStateStore.state.collect { state ->
                    latestPlaybackState = state
                    renderMiniPlayer(state)
                }
            }
        }
    }

    private fun renderMiniPlayer(state: PlaybackState) {
        val shouldShow = state.currentUri != null &&
                (f !is AudioPlayerFragment || forceMiniPlayerOnAudioScreen) &&
                f !is SplashFragment &&
                f !is MenuFragment
        binding.miniPlayer.isVisible = shouldShow
        binding.miniPlayer.alpha = if (state.isPlaying) 1f else 0.58f
        binding.miniPlayerTitle.text = state.title.ifBlank { getString(R.string.audio_player) }
        binding.miniPlayerArtist.text =
            state.artist.ifBlank { if (state.isPlaying) "Playing" else "Paused" }
        binding.miniPlayerPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.icon_pause_mini else R.drawable.icon_play_mini
        )
        binding.miniPlayerProgress.max = state.durationMs.coerceAtLeast(0)
        binding.miniPlayerProgress.progress =
            state.positionMs.coerceIn(0, state.durationMs.coerceAtLeast(0))
    }

    fun setAudioScreenMiniPlayerVisible(visible: Boolean) {
        if (forceMiniPlayerOnAudioScreen == visible) return
        forceMiniPlayerOnAudioScreen = visible
        renderMiniPlayer(latestPlaybackState)
    }

    private fun startPlaybackService(action: String, extraIntent: android.content.Intent? = null) {
        val intent =
            android.content.Intent(this, AudioPlaybackService::class.java).setAction(action)
        extraIntent?.extras?.let { intent.putExtras(it) }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

}