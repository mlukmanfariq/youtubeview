package com.youtube.webview

object AdBlocker {
    private val adPatterns = listOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "2mdn.net",
        "pagead2.googlesyndication.com",
        "securepubads.g.doubleclick.net",
        "googleads.g.doubleclick.net",
        "adservice.google.com",
        "partner.googleadservices.com",
        "tpc.googlesyndication.com",
        "innovid.com",
        "integralads.com",
        "youtube.com/api/stats/ads",
        "youtube.com/pagead/",
        "youtube.com/ptracking"
    )

    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return adPatterns.any { lowerUrl.contains(it) }
    }
}