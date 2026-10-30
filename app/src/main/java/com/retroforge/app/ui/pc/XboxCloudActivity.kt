package com.retroforge.app.ui.pc

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Xbox Cloud Gaming: Microsoft's own xbox.com/play page, full screen, with Better xCloud (redphx, MIT, bundled in
 * assets/xcloud) added as it is in a browser with Tampermonkey. The user signs in on Microsoft's page with their own
 * account; RetroForge never sees the password. Controllers reach the page through the WebView's Gamepad API, touch
 * goes to the page as it would in a browser.
 */
class XboxCloudActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false // the stream starts after the "Play" press, not on a gesture the WebView sees
            settings.setSupportMultipleWindows(false)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true) // Microsoft sign-in hands the session back to xbox.com through cookies
        }
        val script = betterXcloudScript()
        val atDocumentStart = if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, script, setOf(XBOX_ORIGIN))
            true
        } else false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "https" && isSignInOrXbox(url)) return false
                // Anything else (help pages, store links, app links) opens outside, so this screen stays xCloud.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                return true
            }

            // Old WebViews without document-start scripts: add it as early as the page allows. Better xCloud
            // patches xCloud's code as it loads, so some of its features may not take on these.
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!atDocumentStart && url?.startsWith(XBOX_ORIGIN) == true) view.evaluateJavascript(script, null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            // Microphone (party chat) and camera aren't offered; the stream itself needs no permission.
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        setContentView(web)
        web.requestFocus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askBeforeLeaving()
        })
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(START_URL)
    }

    private fun askBeforeLeaving() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Leave Xbox Cloud Gaming?")
            .setMessage("A game you're streaming stops when you leave. You stay signed in.")
            .setPositiveButton("Leave") { _, _ -> finish() }
            .setNegativeButton("Stay", null)
        if (web.canGoBack()) dialog.setNeutralButton("Previous page") { _, _ -> web.goBack() }
        dialog.show()
    }

    /** Better xCloud, run only where its userscript header says it runs (@match / @exclude), wrapped in a function as Tampermonkey does. */
    private fun betterXcloudScript(): String {
        val userscript = assets.open("xcloud/better-xcloud.user.js").bufferedReader().use { it.readText() }
        return """
            (function () {
              var p = location.pathname;
              var play = /^\/[^\/]+\/play/.test(p) && p.indexOf('/xbox-game-pass/play-day-one') < 0;
              var signedIn = /^\/[^\/]+\/auth\/msa/.test(p) && location.search.indexOf('loggedIn') >= 0;
              if (!play && !signedIn) return;
              window.BX_FLAGS = { DeviceInfo: { deviceType: 'android' } };
              (function () {
            $userscript
              })();
            })();
        """.trimIndent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val XBOX_ORIGIN = "https://www.xbox.com"
        const val START_URL = "https://www.xbox.com/play"

        /** Microsoft's own sign-in and xbox.com pages stay in the WebView. */
        val SIGN_IN_HOSTS = listOf("xbox.com", "live.com", "microsoftonline.com", "microsoft.com", "xboxlive.com", "msauth.net", "msftauth.net")

        fun isSignInOrXbox(url: Uri): Boolean {
            val host = url.host ?: return false
            return SIGN_IN_HOSTS.any { host == it || host.endsWith(".$it") }
        }
    }
}
