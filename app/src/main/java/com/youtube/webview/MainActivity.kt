package com.youtube.webview

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isInPiP = false

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)

        hideSystemBars()
        setupWebView()
        loadYoutube()
        setupBackPressed()
        requestNotificationPermissionIfNeeded()

        startService(Intent(this, BackgroundMusicService::class.java))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    webView.evaluateJavascript(
                        "document.exitFullscreen && document.exitFullscreen();", null
                    )
                } else if (webView.canGoBack() && !isInPiP) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // ----------------------- Picture-in-Picture -----------------------

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        // Only go into PiP if a <video> element actually exists AND is currently playing.
        // Without this check, leaving the app while just browsing the feed (no video open)
        // would still shrink the whole page into a tiny floating window, which looks broken.
        val checkJs = "(function(){var v=document.querySelector('video');" +
            "return !!(v && !v.paused && !v.ended && v.currentTime > 0);})();"
        webView.evaluateJavascript(checkJs, ValueCallback<String> { result ->
            if (result == "true") {
                enterPiP()
            }
        })
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            try {
                enterPictureInPictureMode(params)
            } catch (_: IllegalStateException) {
                // Activity not in a valid state for PiP right now, ignore.
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiP = isInPictureInPictureMode
        if (isInPiP) {
            // Strip away the surrounding YouTube page chrome (feed, header, comments, nav bar)
            // and blow the <video> up to fill the whole floating window, so PiP shows just the
            // video instead of the entire mobile page shrunk down.
            webView.evaluateJavascript(PIP_ENTER_JS, null)
        } else {
            webView.evaluateJavascript(PIP_EXIT_JS, null)
        }
    }

    // ----------------------- WebView setup -----------------------

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return !(url.contains("youtube.com") || url.contains("youtu.be") || url.contains("google.com/accounts"))
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                return if (AdBlocker.isAd(url)) {
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                } else null
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(PAGE_SETUP_JS, null)
                view.evaluateJavascript(AdBlocker.COSMETIC_FILTER_JS, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback

                webView.visibility = View.GONE
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                hideSystemBars()
            }

            override fun onHideCustomView() {
                fullscreenContainer.visibility = View.GONE
                fullscreenContainer.removeAllViews()
                webView.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                hideSystemBars()
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                request.deny()
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    private fun loadYoutube() {
        webView.loadUrl("https://m.youtube.com")
    }

    override fun onDestroy() {
        fullscreenContainer.removeAllViews()
        webView.destroy()
        stopService(Intent(this, BackgroundMusicService::class.java))
        super.onDestroy()
    }

    companion object {
        private const val PAGE_SETUP_JS = "(function(){" +
            "Object.defineProperty(document,'hidden',{get:function(){return false}});" +
            "Object.defineProperty(document,'visibilityState',{get:function(){return'visible'}});" +
            "document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation()},true);" +
            "var v=document.querySelector('video');if(v){v.setAttribute('playsinline','');v.setAttribute('webkit-playsinline','');}" +
            "})();"

        private const val PIP_ENTER_JS = "(function(){" +
            "var id='ytlite-pip-style';var s=document.getElementById(id);" +
            "if(!s){s=document.createElement('style');s.id=id;" +
            "s.innerHTML='body>*:not(video){visibility:hidden !important;}" +
            "video{position:fixed !important;top:0 !important;left:0 !important;" +
            "width:100vw !important;height:100vh !important;max-width:none !important;" +
            "max-height:none !important;object-fit:contain !important;background:#000 !important;" +
            "z-index:2147483647 !important;visibility:visible !important;margin:0 !important;}';" +
            "document.documentElement.appendChild(s);}" +
            "var v=document.querySelector('video');if(v){v.style.visibility='visible';}" +
            "})();"

        private const val PIP_EXIT_JS = "(function(){" +
            "var s=document.getElementById('ytlite-pip-style');if(s){s.remove();}" +
            "})();"
    }
}
