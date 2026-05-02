package com.sustain.step.ui.base.navigation

import android.app.Application
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.sustain.step.MainActivity
import com.sustain.step.R
import com.sustain.step.ui.audio_player.AudioPlayerFragment
import com.sustain.step.ui.history.HistoryFragment
import com.sustain.step.ui.home.HomeFragment
import com.sustain.step.ui.menu.MenuFragment
import com.sustain.step.ui.splash.SplashFragment

class MainNavigator(
    application: Application
) : AndroidViewModel(application), Navigator {

    val whenActivityActive = MainActivityActions()

    override fun navigate(tag: String) = whenActivityActive {
        launchFragment(it, tag)
    }

    override fun goBack(result: Any?) = whenActivityActive {
        it.onBackPressedDispatcher.onBackPressed()
    }

    override fun onCleared() {
        super.onCleared()
        whenActivityActive.clear()
    }

    fun launchFragment(
        activity: MainActivity,
        tag: String,
        addToBackStack: Boolean = true
    ) {
        val fragment = activity.supportFragmentManager.findFragmentByTag(tag) ?: when(tag) {
            HomeFragment.TAG -> HomeFragment()
            HistoryFragment.TAG -> HistoryFragment()
            SplashFragment.TAG -> SplashFragment()
            AudioPlayerFragment.TAG -> AudioPlayerFragment()
            MenuFragment.TAG -> MenuFragment()
            else -> return
        }
        val transaction = activity.supportFragmentManager.beginTransaction()
        if (addToBackStack) transaction.addToBackStack(null)
        transaction.replace(R.id.container, fragment)
            .commit()
    }
}

fun Fragment.mainNavigator(): MainNavigator {
    val navigatorProvider =
        ViewModelProvider(requireActivity(), ViewModelProvider.AndroidViewModelFactory(requireActivity().application))
    return navigatorProvider[MainNavigator::class.java]
}