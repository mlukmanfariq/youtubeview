package com.youtube.webview

object AdBlocker {

    // Domains / paths involved in serving ads or ad-tracking on the YouTube web/mobile-web player.
    // Note: this blocks ad *requests*, not "skip button" UI elements that already loaded.
    private val adPatterns = listOf(
        // Google/DoubleClick ad networks
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "google-analytics.com",
        "2mdn.net",
        "adservice.google.com",
        "partner.googleadservices.com",
        "static.doubleclick.net",

        // Google IMA SDK - serves the actual pre-roll/mid-roll video ads in the web player
        "imasdk.googleapis.com",

        // YouTube-specific ad & tracking endpoints
        "youtube.com/api/stats/ads",
        "youtube.com/pagead/",
        "youtube.com/ptracking",
        "youtube.com/api/stats/qoe",
        "youtube.com/get_midroll_",
        "/pagead/",
        "/ptracking",

        // Generic ad/analytics beacons often loaded alongside the page
        "adnxs.com",
        "amazon-adsystem.com",
        "scorecardresearch.com",
        "moatads.com"
    )

    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return adPatterns.any { lowerUrl.contains(it) }
    }
}
