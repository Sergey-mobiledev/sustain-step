package com.sustain.step.data.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import com.sustain.step.data.services.Ext.Companion.POLICY_URL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class Ext {
    companion object {
        const val POLICY_URL = "https://mkelectronicsbd.com/privacy/index.html"
    }
}

fun Fragment.showToast(message: String){
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

fun Fragment.checkInternet(): Boolean {
    val connectivityManager =
        requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    return true
                }

                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    return true
                }

                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    return true
                }
            }
        }
    } else {
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
            return true
        }
    }
    return false
}

suspend fun Fragment.checkInternetWithTimeout(): Boolean {
    return try {
        withTimeout(5000) {
            val deferred = CompletableDeferred<Boolean>()
            var internetConnection = false
            while (!internetConnection) {
                val connectivityManager =
                    requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val capabilities =
                        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    if (capabilities != null) {
                        when {
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                                internetConnection = true
                            }

                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                                internetConnection = true
                            }

                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                                internetConnection = true
                            }
                        }
                    }
                } else {
                    val activeNetworkInfo = connectivityManager.activeNetworkInfo
                    if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                        internetConnection = true
                    }
                }
                if (internetConnection) {
                    deferred.complete(true)
                }
                delay(1000)
            }
            deferred.await()
        }
    } catch (e: Exception) {
        false
    }
}

fun Fragment.openChromeTabs() {
    val builder = CustomTabsIntent.Builder()
    val customTabsIntent = builder.build()
    customTabsIntent.launchUrl(requireContext(), Uri.parse(POLICY_URL))
}