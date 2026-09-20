package com.ironmind.app.data.ai

/**
 * Network connectivity state for model download decisions.
 * Determines whether to warn the user about data usage before downloading over mobile.
 */
enum class NetworkConnectivity {
    /** WiFi or equivalent unlimited connection (no data warning needed). */
    WIFI,

    /** Mobile data (warn user; allow if confirmed). */
    MOBILE,

    /** No connection (download blocked). */
    NONE,
}
