package com.sustain.step.ui.splash

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.sustain.step.data.services.checkInternet
import com.sustain.step.databinding.FragmentSplashBinding
import com.sustain.step.di.factory
import com.sustain.step.ui.base.BaseFragment
import com.sustain.step.ui.base.navigation.mainNavigator
import com.sustain.step.ui.home.HomeFragment

class SplashFragment : BaseFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {

    override val viewModel by viewModels<SplashViewModel> { factory() }
    private var isLoadingCompleted = false
    private var isNotificationFlowCompleted = true

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        isNotificationFlowCompleted = true
        viewModel.resumeLoading()
        proceedWhenReady()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isNotificationFlowCompleted = !shouldAskNotificationPermission()
        viewModel.liveDataLoadingAppState.observe(viewLifecycleOwner) {
            binding.apply {
                if (it <= 100) {
                    progressBar.progress = it
                    progressValue.text = it.toString()
                }
            }
            viewModel.maybeTriggerNotificationsPrompt(
                progress = it,
                shouldAskPermission = !isNotificationFlowCompleted
            )
            if (it >= 100) {
                isLoadingCompleted = true
                proceedWhenReady()
            }
        }
        viewModel.showNotificationsPermissionPrompt.observe(viewLifecycleOwner) { show ->
            if (!show || isNotificationFlowCompleted) return@observe
            viewModel.consumeNotificationsPrompt()
            viewModel.pauseLoading()
            showNotificationAccessDialog()
        }
    }

    private fun proceedWhenReady() {
        if (!isLoadingCompleted || !isNotificationFlowCompleted) return
        startNextFragment()
    }

    private fun startNextFragment() {
        if (!checkInternet()) {
            showErrorDialogFragment()
            return
        }
        navigateToHome()
    }

    private fun shouldAskNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return !granted && !viewModel.notificationsAsked
    }

    private fun showNotificationAccessDialog() {
        val fm = activity?.supportFragmentManager ?: return
        if (fm.findFragmentByTag(AccessNotificationsDialogFragment.TAG) != null) return
        AccessNotificationsDialogFragment().show(fm, AccessNotificationsDialogFragment.TAG)
        fm.setFragmentResultListener(
            AccessNotificationsDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            viewModel.markNotificationsAsked()
            val allowed = bundle.getBoolean(AccessNotificationsDialogFragment.BUNDLE_KEY)
            if (allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                isNotificationFlowCompleted = true
                viewModel.resumeLoading()
                proceedWhenReady()
            }
        }
    }

    private fun navigateToHome() {
        mainNavigator().navigate(HomeFragment.TAG)
    }

    private fun showErrorDialogFragment() {
        val fragmentManager = activity?.supportFragmentManager ?: return
        fragmentManager.findFragmentByTag(ErrorDialogFragment.TAG).let { fragment ->
            fragment ?: let {
                ErrorDialogFragment().show(
                    fragmentManager,
                    ErrorDialogFragment.TAG
                )
            }
            activity?.supportFragmentManager?.setFragmentResultListener(
                ErrorDialogFragment.REQUEST_KEY,
                this
            ) { _, _ ->
                viewModel.loadApp()
            }
        }
    }

    companion object {
        const val TAG = "SplashFragment.tag"
    }
}
