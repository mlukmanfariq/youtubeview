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
                '#masthead-ad',
                '.ad-container',
                '[class*="ad-badge"]'
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
    // anymore - doing so left the player stuck in a broken, unresponsive state (ad overlay stays
    // on screen with no working Skip button, nothing tappable). Instead, we let the ad load
    // normally so the player keeps functioning, and auto-skip it from JS: click "Skip" the
    // moment it's available, and if the ad is unskippable, mute + fast-forward through it.
    val AUTO_SKIP_AD_JS = """
        (function() {
            if (window.__ytLiteAdSkipInstalled) return;
            window.__ytLiteAdSkipInstalled = true;

            var hacking = false;
            var prevRate = 1;
            var prevMuted = false;

            function tick() {
                try {
                    var player = document.querySelector('.html5-video-player');
                    var video = document.querySelector('video');
                    var isAd = player && (
                        player.classList.contains('ad-showing') ||
                        player.classList.contains('ad-interrupting')
                    );

                    if (isAd) {
                        var skipBtn = document.querySelector(
                            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button'
                        );
                        if (skipBtn) {
                            skipBtn.click();
                            return;
                        }
                        var closeBtn = document.querySelector('.ytp-ad-overlay-close-button');
                        if (closeBtn) closeBtn.click();

                        if (video) {
                            if (!hacking) {
                                hacking = true;
                                prevRate = video.playbackRate;
                                prevMuted = video.muted;
                            }
                            video.muted = true;
                            video.playbackRate = 16;
                            if (video.duration && isFinite(video.duration) &&
                                (video.duration - video.currentTime) > 0.5) {
                                video.currentTime = video.duration;
                            }
                        }
                    } else if (hacking) {
                        hacking = false;
                        if (video) {
                            video.playbackRate = prevRate || 1;
                            video.muted = prevMuted;
                        }
                    }
                } catch (e) {}
            }

            setInterval(tick, 250);
        })();
    """.trimIndent()
}
