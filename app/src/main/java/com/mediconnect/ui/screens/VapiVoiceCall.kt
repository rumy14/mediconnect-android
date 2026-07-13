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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
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
import com.mediconnect.data.model.VoiceCallResponse
import com.mediconnect.data.model.VoiceCallTranscriptEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object VapiConfig {
    const val PUBLIC_KEY = "903ffbab-e2a4-43de-9db6-772c9d2933f5"
    const val ASSISTANT_ID = "24b96fc8-1e80-4401-8e1f-480caec6b033"
}

/**
 * Fullscreen voice call dialog powered by VAPI.ai via WebView.
 */
@Composable
fun VapiVoiceCallDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }

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
                PermissionPrompt(
                    onRetry = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onDismiss = onDismiss
                )
            } else {
                VapiVoiceCallSurface(
                    onDismiss = onDismiss,
                    scope = scope
                )
            }
        }
    }
}

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
 *   3. Booking metadata is collected from the VAPI assistant's JavaScript bridge.
 *   4. When the close button is tapped (or call ends), we:
 *      a) Signal VAPI to stop
 *      b) POST the transcript to the API (which auto-books if metadata present)
 *      c) Show an end-of-call overlay with appointment confirmation if booked
 *   5. After a brief pause, onDismiss() runs.
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

    var userJwt by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val session = com.mediconnect.data.session.SessionManager.getInstance(context)
            userJwt = session.getToken().orEmpty()
            userName = session.userNameFlow.first().orEmpty()
        } catch (_: Exception) { }
    }

    // ── End-of-call state ──
    var endScreenState by remember { mutableStateOf<EndScreenState>(EndScreenState.Hidden) }
    LaunchedEffect(endScreenState) {
        if (endScreenState !is EndScreenState.Hidden) {
            delay(2500)
            onDismiss()
        }
    }

    // ── Call tracking state ──
    val transcriptEntries = remember { mutableListOf<VoiceCallTranscriptEntry>() }
    val bookingMetadata = remember { mutableMapOf<String, String>() }
    var callStartedAt by remember { mutableStateOf<Long>(SystemClock.elapsedRealtime()) }
    var callEnded by remember { mutableStateOf(false) }
    var savedCallId by remember { mutableStateOf<String?>(null) }
    var savedAppointmentInfo by remember { mutableStateOf<AppointmentInfo?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // ── Fetch doctors list and inject into WebView for VAPI assistant ──
    LaunchedEffect(Unit) {
        try {
            val api = com.mediconnect.data.api.MediConnectApi.getInstance()
            val resp = api.getDoctors()
            if (resp.success) {
                val json = resp.data.joinToString(",") { doc ->
                    "{\"firstName\":\"${doc.user.firstName}\",\"lastName\":\"${doc.user.lastName}\",\"specialties\":[${doc.specialties.joinToString(",") { "\"${it.specialty.name}\"" }}],\"fee\":\"${doc.consultationFee.toInt()}\"}"
                }
                val doctorsJson = "[$json]"
                kotlinx.coroutines.delay(1000)
                webView?.evaluateJavascript("window.VapiBridge?.setDoctors('$doctorsJson');", null)
            }
        } catch (_: Exception) { }
    }

    // ── Force loudspeaker ──
    LaunchedEffect(Unit) {
        audioManager?.let { am ->
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

    fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Save the transcript, optionally book from metadata, and update UI state.
     * Runs in a coroutine scope.
     */
    fun endCallAndSave() {
        if (isSaving) return
        isSaving = true

        scope.launch {
            try {
                val api = MediConnectApi.getInstance()
                val endedAt = nowIso()
                val durationMs = SystemClock.elapsedRealtime() - callStartedAt
                val durationSec = (durationMs / 1000).toInt()

                // Build metadata from bridge-collected data
                val metadata = if (bookingMetadata.isNotEmpty()) {
                    bookingMetadata.toMap()
                } else null

                val request = SaveVoiceCallRequest(
                    status = if (transcriptEntries.isEmpty()) "INTERRUPTED" else "COMPLETED",
                    durationSeconds = durationSec,
                    startedAt = endedAt,
                    endedAt = endedAt,
                    transcript = transcriptEntries.toList(),
                    metadata = metadata
                )

                val response = api.saveVoiceCall(request)

                if (response.success && response.data != null) {
                    savedCallId = response.data.id
                    android.util.Log.i("VoiceCall", "Transcript saved: ${response.data.id}")

                    // Check if auto-booking happened on the backend
                    val data = response.data
                    // The backend V1 response doesn't include appointmentBooked/appointment fields
                    // But we can try to book explicitly if we have metadata
                    if (bookingMetadata.containsKey("doctorId") &&
                        bookingMetadata.containsKey("appointmentDate") &&
                        bookingMetadata.containsKey("appointmentTime")) {

                        try {
                            val doctorId = bookingMetadata["doctorId"]!!
                            val apptDate = bookingMetadata["appointmentDate"]!!
                            val apptTime = bookingMetadata["appointmentTime"]!!
                            val reason = bookingMetadata["reason"] ?: "Booked via voice AI assistant"

                            val bookResponse = api.bookVoiceCall(
                                callId = response.data.id,
                                doctorId = doctorId,
                                appointmentDate = apptDate,
                                startTime = apptTime,
                                reason = reason
                            )

                            if (bookResponse.success && bookResponse.data != null) {
                                val appointment = bookResponse.data.appointment
                                savedAppointmentInfo = AppointmentInfo(
                                    id = appointment.id,
                                    doctorName = "${appointment.doctor.user.firstName} ${appointment.doctor.user.lastName}",
                                    date = appointment.appointmentDate,
                                    time = appointment.startTime,
                                    status = appointment.status
                                )
                                android.util.Log.i("VoiceCall", "Appointment auto-booked: ${appointment.id}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("VoiceCall", "Auto-book failed (may already be booked by backend): ${e.message}")
                        }
                    }

                    // Show appropriate end screen
                    endScreenState = if (savedAppointmentInfo != null) {
                        EndScreenState.AppointmentBooked(
                            savedAppointmentInfo!!,
                            durationSec
                        )
                    } else {
                        EndScreenState.CallEnded(durationSec)
                    }
                } else {
                    android.util.Log.w("VoiceCall", "Failed to save: ${response.error}")
                    endScreenState = EndScreenState.Error(response.error ?: "Failed to save call")
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceCall", "Error ending call", e)
                endScreenState = EndScreenState.Error(e.message ?: "Unknown error")
            } finally {
                isSaving = false
            }
        }
    }

    val handleClose: () -> Unit = {
        if (!callEnded) {
            callEnded = true
            webView?.evaluateJavascript("window.VapiBridge?.end();", null)
            endCallAndSave()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── WebView ──
        VoiceCallWebView(
            audioManager = audioManager,
            userJwt = userJwt,
            userName = userName,
            transcriptEntries = transcriptEntries,
            bookingMetadata = bookingMetadata,
            onWebViewReady = { webView = it },
            onCallEndedExternally = {
                if (!callEnded) {
                    callEnded = true
                    endCallAndSave()
                }
            }
        )

        // ── End screen overlay ──
        AnimatedVisibility(
            visible = endScreenState !is EndScreenState.Hidden,
            enter = fadeIn(animationSpec = tween(durationMillis = 350)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = endScreenState) {
                is EndScreenState.AppointmentBooked -> AppointmentConfirmationOverlay(state)
                is EndScreenState.CallEnded -> CallEndedOverlay(state)
                is EndScreenState.Error -> ErrorOverlay(state)
                else -> {}
            }
        }

        // ── Top bar ──
        if (endScreenState is EndScreenState.Hidden) {
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
}

// ────────────────────────────────────────────────────────────────────────────
//  End Screen States
// ────────────────────────────────────────────────────────────────────────────

data class AppointmentInfo(
    val id: String,
    val doctorName: String,
    val date: String,
    val time: String,
    val status: String
)

sealed class EndScreenState {
    data object Hidden : EndScreenState()
    data class CallEnded(val durationSeconds: Int) : EndScreenState()
    data class AppointmentBooked(val appointment: AppointmentInfo, val durationSeconds: Int) : EndScreenState()
    data class Error(val message: String) : EndScreenState()
}

@Composable
private fun CallEndedOverlay(state: EndScreenState.CallEnded) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF050608)),
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
                text = "Call ended",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            val durationStr = if (state.durationSeconds < 60) {
                "${state.durationSeconds}s"
            } else {
                "${state.durationSeconds / 60}m ${state.durationSeconds % 60}s"
            }
            Text(
                text = "Duration: $durationStr",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AppointmentConfirmationOverlay(state: EndScreenState.AppointmentBooked) {
    val appt = state.appointment

    // Format the date nicely
    val formattedDate = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(appt.date.split("T")[0])
        val outFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        outFmt.format(date!!)
    } catch (_: Exception) {
        appt.date
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050608)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Success icon with green check
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Appointment booked",
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Appointment Booked!",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your AI assistant has booked an appointment for you.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Appointment details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DetailRow("Doctor", "Dr. ${appt.doctorName}")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Date", formattedDate)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Time", appt.time)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Status", appt.status.replace("_", " "))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val durationStr = if (state.durationSeconds < 60) {
                "${state.durationSeconds}s"
            } else {
                "${state.durationSeconds / 60}m ${state.durationSeconds % 60}s"
            }
            Text(
                text = "Call duration: $durationStr",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ErrorOverlay(state: EndScreenState.Error) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF050608)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("⚠️", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.message,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  JavascriptInterface — bridges transcript + metadata from WebView JS to Kotlin
// ────────────────────────────────────────────────────────────────────────────

class VoiceCallBridge(
    private val transcriptEntries: MutableList<VoiceCallTranscriptEntry>,
    private val bookingMetadata: MutableMap<String, String>,
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

    /**
     * Receives booking metadata from the VAPI assistant JavaScript bridge.
     * Called from JS like: AndroidBridge.onMetadata("doctorId", "abc123")
     */
    @JavascriptInterface
    fun onMetadata(key: String, value: String) {
        if (key.isNotBlank() && value.isNotBlank()) {
            bookingMetadata[key] = value
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceCallWebView(
    audioManager: AudioManager?,
    userJwt: String,
    userName: String,
    transcriptEntries: MutableList<VoiceCallTranscriptEntry>,
    bookingMetadata: MutableMap<String, String>,
    onWebViewReady: (WebView) -> Unit,
    onCallEndedExternally: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(20_000)
        if (isLoading && errorMessage == null) {
            errorMessage = "Connection timed out. Please try again."
            isLoading = false
        }
    }

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
                            if (request.origin != null) {
                                request.grant(request.resources)
                            }
                        }
                    }

                    val bridge = VoiceCallBridge(transcriptEntries, bookingMetadata, onCallEndedExternally)
                    addJavascriptInterface(bridge, "AndroidBridge")

                    htmlContent?.let { data ->
                        loadDataWithBaseURL("https://mediconnect.nma-it.com/api/", data, "text/html", "UTF-8", null)
                    } ?: run {
                        loadUrl("about:blank")
                    }

                    onWebViewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { }
        )

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

private fun String.escapeJsString(): String =
    this.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("</", "<\\/")
