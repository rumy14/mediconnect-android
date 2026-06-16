package com.mediconnect.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.InputStream

/**
 * VAPI Voice Assistant Configuration
 */
object VapiConfig {
    const val PUBLIC_KEY = "903ffbab-e2a4-43de-9db6-772c9d2933f5"
    const val ASSISTANT_ID = "24b96fc8-1e80-4401-8e1f-480caec6b033"
}

/**
 * Fullscreen voice call dialog powered by VAPI.ai via WebView.
 * Shows when [show] is true; calls [onDismiss] when user closes.
 */
@Composable
fun VapiVoiceCallDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    // —— Permission state ——
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    // Request permission on first show if not granted
    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // —— UI ——
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            if (!hasMicPermission) {
                // Permission not granted — show prompt
                PermissionPrompt(
                    onRetry = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onDismiss = onDismiss
                )
            } else {
                // Voice call WebView
                VapiVoiceCallWebView(
                    onDismiss = onDismiss
                )
            }
        }
    }
}

/**
 * Shown when microphone permission is missing.
 */
@Composable
private fun PermissionPrompt(
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎤",
            fontSize = 56.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Microphone Access Needed",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "The AI voice assistant needs microphone access to listen and respond to you.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text("Grant Permission")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = Color.White.copy(alpha = 0.5f))
        }
    }
}

/**
 * The actual WebView that loads the VAPI voice call HTML.
 */
@Composable
private fun VapiVoiceCallWebView(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Auto-dismiss error after 20s timeout if nothing connected
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(20_000)
        if (isLoading && errorMessage == null) {
            errorMessage = "Connection timed out. Please try again."
            isLoading = false
        }
    }

    // Load the HTML content with key/assistant injected
    val htmlContent = remember {
        try {
            val inputStream: InputStream = context.assets.open("vapi_voice.html")
            val text = inputStream.bufferedReader().use { it.readText() }
            text
                .replace("__PUBLIC_KEY__", VapiConfig.PUBLIC_KEY)
                .replace("__ASSISTANT_ID__", VapiConfig.ASSISTANT_ID)
        } catch (e: Exception) {
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // —— WebView ——
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        allowContentAccess = true
                        allowFileAccess = false
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            errorMessage = null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            // Only show error for MAIN page failure, not sub-resources (fonts, icons, etc.)
                            if (request?.isForMainFrame == true) {
                                val desc = if (Build.VERSION.SDK_INT >= 23) {
                                    error?.description?.toString() ?: "Unknown"
                                } else {
                                    "Error"
                                }
                                errorMessage = "Failed to load voice interface ($desc)"
                                isLoading = false
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            // Automatically grant microphone permission in WebView
                            // (we already have the Android-level permission)
                            if (request.origin != null) {
                                request.grant(request.resources)
                            }
                        }
                    }

                    // Load the HTML
                    htmlContent?.let { data ->
                        loadDataWithBaseURL("https://mediconnect.local", data, "text/html", "UTF-8", null)
                    } ?: run {
                        loadUrl("about:blank")
                    }

                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { /* no-op after init */ }
        )

        // —— Loading overlay ——
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Starting voice assistant...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // —— Error state ——
        errorMessage?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        error,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Close and try again",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // —— Close button (top-right) ——
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        // End the VAPI call before closing
                        webView?.evaluateJavascript(
                            "window.VapiBridge?.end();",
                            null
                        )
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
