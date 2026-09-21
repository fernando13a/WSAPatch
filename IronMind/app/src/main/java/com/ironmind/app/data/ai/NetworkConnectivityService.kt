package com.ironmind.app.data.ai

import android.content.Context
import android.net.ConnectivityManager
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
        cm.getNetworkCapabilities(network) ?: return NetworkConnectivity.NONE

        // Metered is the real question for a ~550 MB download, and it is not the same as
        // "cellular": a phone on someone's hotspot, or on metered hotel wifi, bills for every byte.
        // isActiveNetworkMetered is the platform's own answer and resolves the cases reading
        // capabilities by hand gets wrong — notably a VPN, whose own capabilities often omit
        // NOT_METERED even over home wifi, which would otherwise warn on an unmetered connection.
        return if (cm.isActiveNetworkMetered) NetworkConnectivity.MOBILE else NetworkConnectivity.WIFI
    }

    fun isOnMobileData(): Boolean = getConnectivity() == NetworkConnectivity.MOBILE

    fun isConnected(): Boolean = getConnectivity() != NetworkConnectivity.NONE
}
