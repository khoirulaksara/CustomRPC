package com.example.customrpc.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.customrpc.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.webkit.CookieManager
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.customrpc.ui.theme.DiscordDarkPrimary
import com.example.customrpc.ui.theme.DiscordDarkTextMuted

@Composable
fun LoginScreen(sharedPref: android.content.SharedPreferences, onLoginSuccess: () -> Unit) {
    var token by remember { mutableStateOf(sharedPref.getString("token", "") ?: "") }
    var showWebLogin by remember { mutableStateOf(false) }
    var showTokenGuide by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.title_login), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = DiscordDarkPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_subtitle), color = DiscordDarkTextMuted)
        
        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.hint_token)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                 focusedBorderColor = DiscordDarkPrimary,
                 unfocusedBorderColor = Color.DarkGray
            )
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (token.isNotBlank()) {
                    sharedPref.edit().putString("token", token.trim()).apply()
                    onLoginSuccess()
                } else {
                    Toast.makeText(context, context.getString(R.string.msg_enter_token), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DiscordDarkPrimary)
        ) {
            Text(stringResource(R.string.btn_login), fontSize = 16.sp)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showWebLogin = true },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_login_discord))
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { showTokenGuide = true }) {
            Text(stringResource(R.string.btn_token_guide), color = DiscordDarkTextMuted)
        }
    }

    if (showTokenGuide) {
        AlertDialog(
            onDismissRequest = { showTokenGuide = false },
            title = { Text(stringResource(R.string.dialog_guide_title), color = Color.White) },
            text = { Text(stringResource(R.string.dialog_guide_msg), color = Color.White) },
            containerColor = com.example.customrpc.ui.theme.DiscordDarkSecondary,
            confirmButton = {
                TextButton(onClick = { 
                    showTokenGuide = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/login")))
                }) {
                    Text(stringResource(R.string.btn_open_login), color = com.example.customrpc.ui.theme.DiscordDarkPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenGuide = false }) {
                    Text(stringResource(R.string.btn_close), color = Color.Gray)
                }
            }
        )
    }

    if (showWebLogin) {
        Dialog(
            onDismissRequest = { showWebLogin = false },
            properties = DialogProperties(usePlatformDefaultWidth = false) // Fullscreen
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(false)
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        
                        // Enable cookies for persistent login state
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // Desktop UA for reliable web storage patterns
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        
                        val snifferScript = """
                            (function() {
                                function tryExtract() {
                                    try {
                                        // 1. Check LocalStorage
                                        var t = window.localStorage.getItem('token') || window.localStorage.getItem('__AUTH_TOKEN__') || window.localStorage.token;
                                        if (t) {
                                            var clean = t.replace(/"/g, '').trim();
                                            if (clean.length > 40) {
                                                Android.onTokenReceived(clean, "LocalStorage");
                                                return true;
                                            }
                                        }
                                        
                                        // 2. Webpack Method (Highly Reliable for Discord)
                                        try {
                                            if (window.webpackChunkdiscord_app) {
                                                window.webpackChunkdiscord_app.push([
                                                    ['__sniff__'], 
                                                    {}, 
                                                    e => {
                                                        for (const c in e.c) {
                                                            const m = e.c[c].exports;
                                                            if (m && m.default && m.default.getToken !== undefined) {
                                                                const tok = m.default.getToken();
                                                                if (tok && tok.length > 40) {
                                                                    Android.onTokenReceived(tok, "Webpack");
                                                                }
                                                            }
                                                        }
                                                    }
                                                ]);
                                            }
                                        } catch (e) { console.error("Webpack Sniff Err:", e); }
                                    } catch(e) { console.error("Sniffer Err:", e); }
                                    return false;
                                }

                                // 3. Intercept XHR
                                if (!window.xhrHooked) {
                                    window.xhrHooked = true;
                                    var originalOpen = XMLHttpRequest.prototype.open;
                                    XMLHttpRequest.prototype.open = function() {
                                        this.addEventListener('load', function() {
                                            tryExtract();
                                        });
                                        return originalOpen.apply(this, arguments);
                                    };

                                    var originalSetHeader = XMLHttpRequest.prototype.setRequestHeader;
                                    XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
                                        if (key && key.toLowerCase() === 'authorization' && value.length > 40) {
                                            Android.onTokenReceived(value, "XHR Header");
                                        }
                                        originalSetHeader.apply(this, arguments);
                                    };
                                }

                                // 4. Intercept Fetch
                                if (!window.fetchHooked) {
                                    window.fetchHooked = true;
                                    var originalFetch = window.fetch;
                                    window.fetch = function(input, init) {
                                        if (init && init.headers) {
                                            var auth = "";
                                            if (init.headers instanceof Headers) {
                                                auth = init.headers.get('Authorization') || init.headers.get('authorization');
                                            } else {
                                                auth = init.headers['Authorization'] || init.headers['authorization'];
                                            }
                                            if (auth && auth.length > 40) {
                                                Android.onTokenReceived(auth, "Fetch Header");
                                            }
                                        }
                                        return originalFetch.apply(this, arguments).then(function(res) {
                                            tryExtract();
                                            return res;
                                        });
                                    };
                                }

                                // 5. Continuous Polling
                                if (!window.snifferActive) {
                                    window.snifferActive = true;
                                    setInterval(tryExtract, 2000);
                                }
                                tryExtract();
                            })();
                        """.trimIndent()

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onTokenReceived(extToken: String, source: String = "Unknown") {
                                val cleanToken = extToken.replace("Bearer", "", ignoreCase = true).replace("\"", "").trim()
                                if (cleanToken.length < 40) return
                                
                                Handler(Looper.getMainLooper()).post {
                                    if (showWebLogin) {
                                        android.util.Log.d("DiscordSniffer", "Captured via $source: " + cleanToken.take(10) + "...")
                                        token = cleanToken
                                        sharedPref.edit().putString("token", cleanToken).apply()
                                        showWebLogin = false
                                        Toast.makeText(ctx, "Token Sniffed Automatically!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    }
                                }
                            }
                        }, "Android")
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                                android.util.Log.d("DiscordSniffer", "[JS] " + (msg?.message() ?: ""))
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                view?.evaluateJavascript(snifferScript, null)
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript(snifferScript, null)
                                // Extra attempt after finish
                                Handler(Looper.getMainLooper()).postDelayed({
                                    view?.evaluateJavascript(snifferScript, null)
                                }, 2000)
                            }
                        }
                        
                        loadUrl("https://discord.com/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
