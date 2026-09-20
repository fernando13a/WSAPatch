package com.ironmind.app.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for determining the current network connectivity state.
 * Used to warn the user before downloading large models over mobile data.
 */
@Singleton
class NetworkConnectivityService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getConnectivity(): NetworkConnectivity {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkConnectivity.NONE

        val network = cm.activeNetwork ?: return NetworkConnectivity.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkConnectivity.NONE

        return when {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkConnectivity.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkConnectivity.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnectivity.MOBILE
            else -> NetworkConnectivity.MOBILE
        }
    }

    fun isOnMobileData(): Boolean = getConnectivity() == NetworkConnectivity.MOBILE

    fun isConnected(): Boolean = getConnectivity() != NetworkConnectivity.NONE
}
