package com.youtube.webview

object AdBlocker {

    // Network-level blocking: domains / paths that serve ads or ad-tracking requests.
    private val adPatterns = listOf(
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
        "youtube.com/api/stats/ads",
        "youtube.com/pagead/",
        "youtube.com/ptracking",
        "youtube.com/api/stats/qoe",
        "youtube.com/get_midroll_",
        "/pagead/",
        "/ptracking",
        "adnxs.com",
        "amazon-adsystem.com",
        "scorecardresearch.com",
        "moatads.com"
    )

    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return adPatterns.any { lowerUrl.contains(it) }
    }

    // Cosmetic filtering: a lot of what shows up as "ads" on YouTube (in-feed promoted videos,
    // masthead banner, companion cards, mealbar signup nag) is embedded directly in the normal
    // page/API response - it doesn't come from a separate ad domain, so it can't be blocked at
    // the network level like above. Brave and other ad-blockers additionally strip these out by
    // matching known element tags/classes and removing them from the DOM. We replicate that here
    // with a MutationObserver so it also catches ad nodes that get added after initial page load
    // (YouTube's mobile web app is a single-page app that swaps content in via JS).
    val COSMETIC_FILTER_JS = """
        (function() {
            if (window.__ytLiteCosmeticFilterInstalled) return;
            window.__ytLiteCosmeticFilterInstalled = true;

            var selectors = [
                'ytm-promoted-video-renderer',
                'ytm-companion-ad-renderer',
                'ytm-primetime-promo-renderer',
                'ytm-primetime-promo-renderer-background',
                'ytm-banner-promo-renderer',
                'ytm-mealbar-promo-renderer',
                'ytm-in-feed-ad-layout-renderer',
                'ytm-ad-slot-renderer',
                'ytd-promoted-sparkles-web-renderer',
                'ytd-display-ad-renderer',
                'ytd-promoted-video-renderer',
                'ytd-companion-slot-renderer',
                'ytd-action-companion-ad-renderer',
                'ytd-in-feed-ad-layout-renderer',
                'ytd-ad-slot-renderer',
                'ytd-banner-promo-renderer-background',
                '.ytp-ad-module',
                '.ytp-ad-overlay-container',
                '.ytp-ad-text-overlay',
                '#masthead-ad'
            ];
            var cssSelector = selectors.join(',');

            function removeAds(root) {
                try {
                    var nodes = root.querySelectorAll(cssSelector);
                    for (var i = 0; i < nodes.length; i++) {
                        nodes[i].remove();
                    }
                } catch (e) {}
            }

            removeAds(document);

            var observer = new MutationObserver(function(mutations) {
                for (var i = 0; i < mutations.length; i++) {
                    var added = mutations[i].addedNodes;
                    for (var j = 0; j < added.length; j++) {
                        var node = added[j];
                        if (node.nodeType !== 1) continue;
                        try {
                            if (node.matches && node.matches(cssSelector)) {
                                node.remove();
                                continue;
                            }
                        } catch (e) {}
                        removeAds(node);
                    }
                }
            });

            observer.observe(document.documentElement, { childList: true, subtree: true });
        })();
    """.trimIndent()

    // The pre-roll/mid-roll video ad itself plays through the SAME <video> element as the real
    // content (via Google's IMA SDK). We deliberately do NOT block that SDK's network requests
    // - doing so left the player stuck in a broken, unresponsive state (ad overlay on screen,
    // nothing tappable). Instead, we let the ad load normally and auto-skip it from JS by
    // clicking "Skip" the moment it becomes available.
    //
    // IMPORTANT: this only ever *clicks a button that YouTube itself rendered*. It never seeks,
    // mutes, or changes the real <video> element directly - an earlier version tried to
    // fast-forward unskippable ads by force-seeking currentTime, but if the "is this an ad"
    // detection ever misfired it would force-seek the REAL video to its end too, which is what
    // caused the "blank screen / just shows the player icon" bug. Clicking a real skip button is
    // safe because that button simply doesn't exist unless YouTube's own player put it there.
    val AUTO_SKIP_AD_JS = """
        (function() {
            if (window.__ytLiteAdSkipInstalled) return;
            window.__ytLiteAdSkipInstalled = true;

            function tick() {
                try {
                    var skipBtn = document.querySelector(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
                        'button.ytp-ad-skip-button-container, .ytp-ad-overlay-close-button'
                    );
                    if (skipBtn) {
                        skipBtn.click();
                    }
                } catch (e) {}
            }

            setInterval(tick, 400);
        })();
    """.trimIndent()
}
