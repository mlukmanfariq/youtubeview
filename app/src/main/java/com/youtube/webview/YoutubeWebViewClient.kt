package com.youtube.webview

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class YoutubeWebViewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url.startsWith("https://m.youtube.com") || url.startsWith("https://www.youtube.com")) {
            return false
        }
        if (url.startsWith("http://m.youtube.com") || url.startsWith("http://www.youtube.com")) {
            return false
        }
        if (url.contains("youtu.be")) {
            view.loadUrl(url)
            return true
        }
        return false
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        injectKeepAlive(view)
    }

    private fun injectKeepAlive(view: WebView) {
        val js = """
            (function() {
                var hidden = false;
                Object.defineProperty(document, 'hidden', { get: function() { return hidden; } });
                Object.defineProperty(document, 'visibilityState', { get: function() { return hidden ? 'hidden' : 'visible'; } });
                window.addEventListener('focus', function() { hidden = false; });
                window.addEventListener('blur', function() { hidden = true; });
                var video = document.querySelector('video');
                if (video) {
                    video.removeAttribute('controls');
                }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}