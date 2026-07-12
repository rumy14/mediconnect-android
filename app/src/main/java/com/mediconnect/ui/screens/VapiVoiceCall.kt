package com.mediconnect.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.model.SaveVoiceCallRequest
import com.mediconnect.data.model.VoiceCallTranscriptEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
 *
 * Captures transcript in real-time via JavascriptInterface and POSTs
 * the completed call data to the MediConnect API on call end.
 */
@Composable
fun VapiVoiceCallDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // —— Audio manager (used for volume + loudspeaker) ——
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }

    // Volume button handler for the entire dialog surface.
    val handleVolumeKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = volKey@ { ke ->
        if (ke.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@volKey false
        when (ke.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                true
            }
            else -> false
        }
    }

    // —— UI ——
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(durationMillis = 250)),
        exit = fadeOut(animationSpec = tween(durationMillis = 400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050608))
                .onPreviewKeyEvent { handleVolumeKey(it) }
        ) {
            if (!hasMicPermission) {
                // Permission not granted — show prompt
                PermissionPrompt(
                    onRetry = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onDismiss = onDismiss
                )
            } else {
                // Voice call WebView with smooth end transition
                VapiVoiceCallSurface(
                    onDismiss = onDismiss,
                    scope = scope
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
            text = "Microphone Access Needed",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
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
 * Voice call surface — owns the WebView and the closing transition.
 *
 * Lifecycle:
 *   1. WebView mounts and starts the VAPI call.
 *   2. Transcript entries are captured in real-time via [VoiceCallBridge].
 *   3. When the close button is tapped (or call ends), we signal VAPI to stop,
 *      POST the transcript to the API, and reveal an "end-of-call" overlay.
 *   4. After ~1.4s the overlay fades in, then onDismiss() runs.
 */
@Composable
private fun VapiVoiceCallSurface(
    onDismiss: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Pull the JWT and name from local session storage so VAPI tool calls can resolve the user.
    var userJwt by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val session = com.mediconnect.data.session.SessionManager.getInstance(context)
            userJwt = session.getToken().orEmpty()
            userName = session.userNameFlow.first().orEmpty()
        } catch (_: Exception) { /* anonymous call (rare) */ }
    }

    // The closing overlay state
    var endMessage by remember { mutableStateOf<String?>(null) }
    // When user wants to close, set endMessage; after a moment, call onDismiss.
    LaunchedEffect(endMessage) {
        if (endMessage != null) {
            delay(1400)
            onDismiss()
        }
    }

    // —— Transcript capture state ——
    val transcriptEntries = remember { mutableListOf<VoiceCallTranscriptEntry>() }
    var callStartedAt by remember { mutableStateOf<Long>(SystemClock.elapsedRealtime()) }
    var callEnded by remember { mutableStateOf(false) }

    // —— Fetch doctors list and inject into WebView for VAPI assistant ——
    LaunchedEffect(Unit) {
        try {
            val api = com.mediconnect.data.api.MediConnectApi.getInstance()
            val resp = api.getDoctors()
            if (resp.success) {
                val json = resp.data.joinToString(",") { doc ->
                    "{\"firstName\":\"${doc.user.firstName}\",\"lastName\":\"${doc.user.lastName}\",\"specialties\":[${doc.specialties.joinToString(",") { "\"${it.specialty.name}\"" }}],\"fee\":\"${doc.consultationFee.toInt()}\"}"
                }
                val doctorsJson = "[$json]"
                // Wait for WebView to be ready, then inject
                kotlinx.coroutines.delay(1000)
                webView?.evaluateJavascript("window.VapiBridge?.setDoctors('$doctorsJson');", null)
            }
        } catch (_: Exception) { }
    }

    // —— Force loudspeaker (speakerphone) so the AI voice plays out loud, not the earpiece ——
    LaunchedEffect(Unit) {
        audioManager?.let { am ->
            // Make media stream drive the volume for both incoming audio and our UI.
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            audioManager?.let { am ->
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    /** Format an ISO-8601 timestamp for the current moment. */
    fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /** Save the captured transcript to the API and return a status message. */
    fun saveTranscript(): String {
        if (transcriptEntries.isEmpty()) {
            return "Call ended"
        }

        val endedAt = nowIso()
        val durationMs = SystemClock.elapsedRealtime() - callStartedAt
        val durationSec = (durationMs / 1000).toInt()

        scope.launch {
            try {
                val api = MediConnectApi.getInstance()
                val request = SaveVoiceCallRequest(
                    status = "COMPLETED",
                    durationSeconds = durationSec,
                    startedAt = endedAt,
                    endedAt = endedAt,
                    transcript = transcriptEntries.toList()
                )
                val response = api.saveVoiceCall(request)
                if (!response.success) {
                    android.util.Log.w("VoiceCall", "Failed to save transcript: ${response.error}")
                } else {
                    android.util.Log.i("VoiceCall", "Transcript saved: ${response.data?.id}")
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceCall", "Error saving transcript", e)
            }
        }
        return "Call ended"
    }

    val handleClose: () -> Unit = {
        if (!callEnded) {
            callEnded = true
            // Stop the VAPI call cleanly
            webView?.evaluateJavascript("window.VapiBridge?.end();", null)
            // Save the transcript
            endMessage = saveTranscript()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // —— WebView (always present underneath) ——
        VoiceCallWebView(
            audioManager = audioManager,
            userJwt = userJwt,
            userName = userName,
            transcriptEntries = transcriptEntries,
            onWebViewReady = { webView = it },
            onCallEndedExternally = {
                if (!callEnded) {
                    callEnded = true
                    endMessage = saveTranscript()
                }
            }
        )

        // —— Closing overlay with smooth fade ——
        AnimatedVisibility(
            visible = endMessage != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 350)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050608)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = endMessage ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Saving transcript…",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // —— Top bar: title + a clean, large close icon ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎤", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "MediConnect AI",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Clear, prominent close button — circular surface, proper Close icon.
            Surface(
                onClick = handleClose,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "End call",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  JavascriptInterface — bridges transcript data from WebView JS to Kotlin
// ────────────────────────────────────────────────────────────────────────────

/**
 * Bridge object exposed to the WebView JavaScript via [WebView.addJavascriptInterface].
 *
 * The JS side calls:
 *   AndroidBridge.onTranscript(role, text, timestamp, transcriptType)
 *   AndroidBridge.onCallEnded()
 *
 * All arguments are passed as JSON strings for safety across the bridge boundary.
 */
class VoiceCallBridge(
    private val transcriptEntries: MutableList<VoiceCallTranscriptEntry>,
    private val onCallEnded: () -> Unit
) {
    @JavascriptInterface
    fun onTranscript(role: String, text: String, timestamp: String, transcriptType: String) {
        val entry = VoiceCallTranscriptEntry(
            role = role,
            text = text,
            timestamp = timestamp,
            transcriptType = transcriptType
        )
        transcriptEntries.add(entry)
    }

    @JavascriptInterface
    fun onCallEnded() {
        onCallEnded()
    }
}

// ────────────────────────────────────────────────────────────────────────────

/**
 * The WebView that loads vapi_voice.html.
 *
 * - Reports the WebView upward so the parent can call end() on close.
 * - Reports when the server-side call ends so we can show the closing overlay.
 * - Bridges transcript data to Kotlin via [VoiceCallBridge].
 */
@Composable
private fun VoiceCallWebView(
    audioManager: AudioManager?,
    userJwt: String,
    userName: String,
    transcriptEntries: MutableList<VoiceCallTranscriptEntry>,
    onWebViewReady: (WebView) -> Unit,
    onCallEndedExternally: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss error after 20s timeout if nothing connected
    LaunchedEffect(Unit) {
        delay(20_000)
        if (isLoading && errorMessage == null) {
            errorMessage = "Connection timed out. Please try again."
            isLoading = false
        }
    }

    // Load the HTML content with key/assistant/user injected (doctors fetched separately)
    val htmlContent = remember(userJwt, userName) {
        try {
            val inputStream: InputStream = context.assets.open("vapi_voice.html")
            val text = inputStream.bufferedReader().use { it.readText() }
            text
                .replace("__PUBLIC_KEY__", VapiConfig.PUBLIC_KEY)
                .replace("__ASSISTANT_ID__", VapiConfig.ASSISTANT_ID)
                .replace("__USER_JWT__", userJwt.escapeJsString())
                .replace("__USER_NAME__", userName.escapeJsString())
                .replace("__API_BASE__", com.mediconnect.BuildConfig.API_BASE_URL)
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

                    // —— Add the JavaScript bridge for transcript capture ——
                    val bridge = VoiceCallBridge(transcriptEntries, onCallEndedExternally)
                    addJavascriptInterface(bridge, "AndroidBridge")

                    // Load the HTML
                    htmlContent?.let { data ->
                        loadDataWithBaseURL("https://mediconnect.nma-it.com/api/", data, "text/html", "UTF-8", null)
                    } ?: run {
                        loadUrl("about:blank")
                    }

                    onWebViewReady(this)
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
    }
}

/**
 * Escape a string so it can safely be inlined inside a JS string literal.
 * Backslashes, quotes and newlines are sanitised so an attacker-controlled
 * user name can't break out of the literal.
 */
private fun String.escapeJsString(): String =
    this.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("</", "<\\/")
