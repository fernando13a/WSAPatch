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

        // Metered is the question that matters for a ~550 MB download, and it isn't the same as
        // "cellular": a phone on someone's hotspot, or on metered hotel wifi, has TRANSPORT_WIFI
        // and still bills for every byte. Treating any wifi as free skipped the warning exactly
        // where it was needed most.
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            NetworkConnectivity.WIFI
        } else {
            NetworkConnectivity.MOBILE
        }
    }

    fun isOnMobileData(): Boolean = getConnectivity() == NetworkConnectivity.MOBILE

    fun isConnected(): Boolean = getConnectivity() != NetworkConnectivity.NONE
}
