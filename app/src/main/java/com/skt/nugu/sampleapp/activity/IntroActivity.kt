package com.skt.nugu.sampleapp.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RelativeLayout

/**
 * Demonstrate using nugu with webview.
 */
class IntroActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_KEY_POCID = "key_poc_id"
        const val EXTRA_KEY_DEVICE_UNIQUEID = "key_device_unique_id"
        private const val BASE_USING_URL = "https://webview.sktnugu.com/v2/3pp/confirm.html?poc_id=%s&device_unique_id=%s"

        fun invokeActivity(context: Context, pocId: String, deviceUniqueId: String) {
            context.startActivity(
                Intent(context, IntroActivity::class.java)
                    .putExtra(EXTRA_KEY_POCID, pocId)
                    .putExtra(EXTRA_KEY_DEVICE_UNIQUEID, deviceUniqueId)
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = RelativeLayout(this)
        layout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(layout)

        val webView = WebView(this)
        webView.settings?.run {
            domStorageEnabled = true
            javaScriptEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.apply {
                    // Otherwise, the link is not for a page on my site, so launch
                    // another Activity that handles URLs
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }
                return false
            }
        }
        intent.extras?.apply {
            webView.loadUrl(String.format(BASE_USING_URL, getString(EXTRA_KEY_POCID), getString(EXTRA_KEY_DEVICE_UNIQUEID)))
        }

        layout.addView( webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}