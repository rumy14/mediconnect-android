package com.mediconnect.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

// ────────────────────────────────────────────────────────────────────────────
//  Colors
// ────────────────────────────────────────────────────────────────────────────
private val SurfaceDark = Color(0xFF0A0A0F)
private val CardSurface = Color(0xFF0F172A)
private val ActiveGreen = Color(0xFF22C55E)
private val SpeakingBlue = Color(0xFF3B82F6)
private val ThinkingPurple = Color(0xFFA855F7)
private val EndRed = Color(0xFFDC2626)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF475569)
private val DividerColor = Color(0xFF1E293B)
private val ControlBg = Color(0xFF1E293B)
private val ControlBgActive = Color(0xFF2563EB)

// ────────────────────────────────────────────────────────────────────────────
//  VAPI config
// ────────────────────────────────────────────────────────────────────────────
object VapiConfig {
    const val PUBLIC_KEY = "903ffbab-e2a4-43de-9db6-772c9d2933f5"
    const val ASSISTANT_ID = "24b96fc8-1e80-4401-8e1f-480caec6b033"
}

// ────────────────────────────────────────────────────────────────────────────
//  Engine state from WebView JS
// ────────────────────────────────────────────────────────────────────────────
private enum class EngineState {
    IDLE, CONNECTING, CONNECTED, LISTENING, SPEAKING, DISCONNECTED, ERROR
}

private data class EngineStatus(
    val state: EngineState = EngineState.IDLE,
    val timerDisplay: String = "00:00",
    val timerElapsed: Int = 0,
    val errorMessage: String? = null,
    val bookingComplete: Boolean = false,
    val bookingInfo: Map<String, String>? = null,
)

// ────────────────────────────────────────────────────────────────────────────
//  Entry point
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun VapiVoiceCallDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onAppointmentBooked: (() -> Unit)? = null,
    doctorName: String? = null  // doctor name to show on call screen
) {
    if (!show) return

    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }
    LaunchedEffect(Unit) { if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
                .systemBarsPadding()
        ) {
            if (!hasMicPermission) {
                PermissionPrompt(
                    onRetry = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onDismiss = onDismiss
                )
            } else {
                VoiceCallScreen(
                    onDismiss = onDismiss,
                    onAppointmentBooked = onAppointmentBooked,
                    doctorName = doctorName
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Permission screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionPrompt(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎤", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Microphone Access Needed",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The AI voice assistant needs mic access to listen and respond.",
            color = TextSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen)) {
            Text("Grant Permission", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Main call screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceCallScreen(
    onDismiss: () -> Unit,
    onAppointmentBooked: (() -> Unit)?,
    doctorName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager }

    // WebView ref
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Engine status
    var engine by remember { mutableStateOf(EngineStatus()) }
    var transcriptEntries by remember { mutableStateOf<List<TranscriptItem>>(emptyList()) }
    var savedCallId by remember { mutableStateOf<String?>(null) }
    var savedAppointment by remember { mutableStateOf<AppointmentInfo?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var callStartedAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    // UI state
    var isMuted by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }

    // Auth
    var userJwt by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val session = com.mediconnect.data.session.SessionManager.getInstance(context)
            userJwt = session.getToken().orEmpty()
            userName = session.userNameFlow.first().orEmpty()
        } catch (_: Exception) {}
    }

    // Force speaker
    LaunchedEffect(Unit) { forceSpeakerphoneOn(audioManager) }
    DisposableEffect(Unit) { onDispose { restoreSpeakerphone(audioManager) } }

    // Convert transcript entries list to string for log
    fun transcriptLog() = transcriptEntries.joinToString(" | ") { "[${it.role}] ${it.text.take(50)}" }

    // ── End call & save ──
    fun endCallAndSave() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            try {
                val api = MediConnectApi.getInstance()
                val endedAt = nowIso()
                val durationSec = ((SystemClock.elapsedRealtime() - callStartedAt) / 1000).toInt()

                val metadata = mutableMapOf<String, String>()
                // Try to extract metadata from bookable items if we have any
                // The actual metadata is sent via AndroidBridge from WebView

                val request = SaveVoiceCallRequest(
                    status = if (transcriptEntries.isEmpty()) "INTERRUPTED" else "COMPLETED",
                    durationSeconds = durationSec,
                    startedAt = endedAt,
                    endedAt = endedAt,
                    transcript = transcriptEntries.map {
                        VoiceCallTranscriptEntry(
                            role = it.role,
                            text = it.text,
                            timestamp = it.timestamp,
                            transcriptType = "final"
                        )
                    },
                    metadata = if (metadata.isNotEmpty()) metadata else null
                )

                val response = api.saveVoiceCall(request)
                if (response.success && response.data != null) {
                    savedCallId = response.data.id
                }
            } catch (_: Exception) {}
            isSaving = false
        }
    }

    val handleEndCall: () -> Unit = {
        webView?.evaluateJavascript("window.VapiBridge?.end();", null)
        endCallAndSave()
        // Brief delay before dismiss allows end screen to show
        scope.launch {
            delay(2000)
            onDismiss()
        }
    }

    // ── End screen dismiss timer ──
    val shouldAutoDismiss = engine.state == EngineState.DISCONNECTED || engine.bookingComplete
    LaunchedEffect(shouldAutoDismiss) {
        if (shouldAutoDismiss) {
            delay(3000)
            onDismiss()
        }
    }

    // ── Audio wave animation ──
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "wavePhase"
    )

    // ── Pulsing ring animation ──
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.05f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringAlpha"
    )

    val isActive = engine.state == EngineState.LISTENING || engine.state == EngineState.SPEAKING || engine.state == EngineState.CONNECTED
    val avatarInnerColor = when (engine.state) {
        EngineState.SPEAKING -> SpeakingBlue
        EngineState.CONNECTING, EngineState.IDLE -> ThinkingPurple
        EngineState.LISTENING, EngineState.CONNECTED -> ActiveGreen
        else -> Color(0xFF475569)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background: WebView (transparent, audio engine only) ──
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowContentAccess = true
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            if (request.origin != null) request.grant(request.resources)
                        }
                    }
                    addJavascriptInterface(
                        VoiceCallEngineBridge(
                            onStateChange = { newEngine -> engine = newEngine },
                            onTranscript = { item ->
                                transcriptEntries = transcriptEntries + item
                            },
                            onCallEnded = { endCallAndSave() },
                            onBookingComplete = { info ->
                                savedAppointment = info
                            }
                        ),
                        "AndroidBridge"
                    )
                    try {
                        val inputStream = ctx.assets.open("vapi_voice.html")
                        val html = inputStream.bufferedReader().use { it.readText() }
                            .replace("__PUBLIC_KEY__", VapiConfig.PUBLIC_KEY)
                            .replace("__ASSISTANT_ID__", VapiConfig.ASSISTANT_ID)
                            .replace("__USER_JWT__", userJwt.escapeJs())
                            .replace("__USER_NAME__", userName.escapeJs())
                            .replace("__API_BASE__", com.mediconnect.BuildConfig.API_BASE_URL)
                        loadDataWithBaseURL(
                            "https://mediconnect.nma-it.com/api/",
                            html, "text/html", "UTF-8", null
                        )
                    } catch (_: Exception) {}
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize().alpha(0.01f)
        )

        // ── UI Content ──
        when {
            engine.state == EngineState.DISCONNECTED || engine.bookingComplete -> {
                EndScreen(
                    isBooked = savedAppointment != null || engine.bookingComplete,
                    appointment = savedAppointment,
                    engine = engine,
                    onDismiss = onDismiss
                )
            }
            engine.state == EngineState.ERROR -> {
                ErrorScreen(message = engine.errorMessage ?: "Something went wrong", onDismiss = onDismiss)
            }
            else -> {
                ActiveCallScreen(
                    engine = engine,
                    doctorName = doctorName,
                    isMuted = isMuted,
                    isSpeakerOn = isSpeakerOn,
                    showTranscript = showTranscript,
                    transcriptEntries = transcriptEntries,
                    avatarColor = avatarInnerColor,
                    ringScale = ringScale,
                    ringAlpha = ringAlpha,
                    wavePhase = wavePhase,
                    onToggleMute = { isMuted = !isMuted },
                    onToggleSpeaker = {
                        isSpeakerOn = !isSpeakerOn
                        if (isSpeakerOn) forceSpeakerphoneOn(audioManager)
                        else restoreSpeakerphone(audioManager)
                    },
                    onToggleTranscript = { showTranscript = !showTranscript },
                    onEndCall = handleEndCall,
                    onClose = onDismiss
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Active call screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveCallScreen(
    engine: EngineStatus,
    doctorName: String?,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    showTranscript: Boolean,
    transcriptEntries: List<TranscriptItem>,
    avatarColor: Color,
    ringScale: Float,
    ringAlpha: Float,
    wavePhase: Float,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleTranscript: () -> Unit,
    onEndCall: () -> Unit,
    onClose: () -> Unit,
) {
    val displayName = doctorName ?: "MediConnect AI"
    val statusText = when (engine.state) {
        EngineState.CONNECTING -> "Connecting..."
        EngineState.LISTENING -> "Listening..."
        EngineState.SPEAKING -> "Speaking..."
        EngineState.CONNECTED -> "Connected"
        else -> ""
    }
    val subStatus = when (engine.state) {
        EngineState.CONNECTING -> "Setting up voice connection"
        EngineState.LISTENING -> "Your turn to speak"
        EngineState.SPEAKING -> "AI assistant is replying"
        else -> ""
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SurfaceDark).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top bar: Close + Timer ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))

            // Timer badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x1AFFFFFF),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (engine.state == EngineState.SPEAKING) SpeakingBlue else ActiveGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        engine.timerDisplay,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Chat transcript toggle
            Surface(
                onClick = onToggleTranscript,
                shape = RoundedCornerShape(20.dp),
                color = if (showTranscript) ControlBgActive else Color(0x1AFFFFFF),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (showTranscript) Icons.Filled.Chat else Icons.Filled.Forum,
                        contentDescription = "Transcript",
                        tint = if (showTranscript) Color.White else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Chat",
                        color = if (showTranscript) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.weight(0.3f))

        // ── Avatar area ──
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            // Outer rings
            if (engine.state == EngineState.LISTENING) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    for (i in 0..2) {
                        val r = ringScale * (80f + i * 18f)
                        drawCircle(
                            color = ActiveGreen.copy(alpha = ringAlpha * (1f - i * 0.3f)),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5f)
                        )
                    }
                }
            }
            if (engine.state == EngineState.SPEAKING) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    for (i in 0..2) {
                        val r = ringScale * (85f + i * 15f)
                        drawCircle(
                            color = SpeakingBlue.copy(alpha = ringAlpha * (1f - i * 0.3f)),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5f)
                        )
                    }
                }
            }
            if (engine.state == EngineState.CONNECTING) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    for (i in 0..2) {
                        val sweepAngle = (wavePhase / PI.toFloat() * 180f) % 360f
                        drawArc(
                            color = ThinkingPurple.copy(alpha = 0.4f),
                            startAngle = 0f,
                            sweepAngle = sweepAngle + i * 120f,
                            useCenter = false,
                            topLeft = Offset(cx - 70f - i * 12f, cy - 70f - i * 12f),
                            size = androidx.compose.ui.geometry.Size(140f + i * 24f, 140f + i * 24f),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // Main avatar circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(avatarColor, avatarColor.copy(alpha = 0.6f), ThinkingPurple.copy(alpha = 0.3f), avatarColor)
                        )
                    )
                    .border(2.dp, avatarColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◆", fontSize = 34.sp, color = Color.White)
                    Text(
                        "AI",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Name and status ──
        Text(
            displayName,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            statusText,
            color = avatarColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        if (subStatus.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subStatus, color = TextMuted, fontSize = 12.sp)
        }

        Spacer(Modifier.weight(0.2f))

        // ── Sound wave visualization ──
        if (engine.state == EngineState.SPEAKING || engine.state == EngineState.LISTENING) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 40.dp)
            ) {
                val barCount = 40
                val barWidth = size.width / barCount
                val waveColor = if (engine.state == EngineState.SPEAKING) SpeakingBlue else ActiveGreen
                for (i in 0 until barCount) {
                    val x = i * barWidth
                    val normalizedPhase = (i.toFloat() / barCount) * 2f * PI.toFloat() + wavePhase
                    val amplitude = 8f + sin(normalizedPhase) * 12f + sin(normalizedPhase * 0.5f) * 6f
                    val barHeight = maxOf(4f, amplitude * 2f)
                    val y = size.height / 2f - barHeight / 2f
                    drawRoundRect(
                        color = waveColor.copy(alpha = 0.5f + sin(normalizedPhase * 0.5f) * 0.3f),
                        topLeft = Offset(x + 1f, y),
                        size = androidx.compose.ui.geometry.Size(maxOf(2f, barWidth - 2f), barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                    )
                }
            }
            Spacer(Modifier.weight(0.15f))
        } else {
            Spacer(Modifier.weight(0.35f))
        }

        // ── Control buttons row ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute button
            CallControlButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                isActive = isMuted,
                activeTint = Color(0xFFF59E0B),
                onClick = onToggleMute
            )

            // Speaker button
            CallControlButton(
                icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeDown,
                label = if (isSpeakerOn) "Speaker" else "Earpiece",
                isActive = isSpeakerOn,
                activeTint = SpeakingBlue,
                onClick = onToggleSpeaker
            )
        }

        Spacer(Modifier.weight(0.2f))

        // ── End Call button ──
        Button(
            onClick = onEndCall,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = EndRed),
            modifier = Modifier.size(72.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "End Call", modifier = Modifier.size(30.dp), tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text("End Call", color = EndRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(24.dp))
    }

    // ── Transcript panel sliding up ──
    AnimatedVisibility(
        visible = showTranscript,
        enter = slideInVertically { it } + fadeIn(tween(200)),
        exit = slideOutVertically { it } + fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize()
    ) {
        TranscriptPanel(
            entries = transcriptEntries,
            onClose = onToggleTranscript
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Call control button
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeTint: Color = SpeakingBlue,
    onClick: () -> Unit,
) {
    val bgColor = if (isActive) activeTint.copy(alpha = 0.15f) else ControlBg
    val iconColor = if (isActive) activeTint else TextSecondary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = bgColor,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  End screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun EndScreen(
    isBooked: Boolean,
    appointment: AppointmentInfo?,
    engine: EngineStatus,
    onDismiss: () -> Unit
) {
    val fadeAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fadeAnim.animateTo(1f, tween(500)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .alpha(fadeAnim.value),
        contentAlignment = Alignment.Center
    ) {
        if (isBooked && appointment != null) {
            // Appointment booked screen
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = ActiveGreen,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Appointment Booked!", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your AI assistant booked an appointment for you.",
                    color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        EndDetailRow("Doctor", "Dr. ${appointment.doctorName}")
                        Spacer(Modifier.height(10.dp))
                        EndDetailRow("Date", formatDateNice(appointment.date))
                        Spacer(Modifier.height(10.dp))
                        EndDetailRow("Time", appointment.time)
                        Spacer(Modifier.height(10.dp))
                        EndDetailRow("Status", appointment.status.replace("_", " "))
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (engine.timerElapsed > 0) {
                    val dur = if (engine.timerElapsed < 60) "${engine.timerElapsed}s"
                    else "${engine.timerElapsed / 60}m ${engine.timerElapsed % 60}s"
                    Text("Call duration: $dur", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else if (isBooked) {
            // Booking complete from engine (no appointment info)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActiveGreen, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                Text("Appointment Booked!", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Your appointment details will appear in the app.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
            // Call ended, no booking
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CallEnd, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Call Ended", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (engine.timerElapsed > 0) {
                    Spacer(Modifier.height(4.dp))
                    val dur = if (engine.timerElapsed < 60) "${engine.timerElapsed}s"
                    else "${engine.timerElapsed / 60}m ${engine.timerElapsed % 60}s"
                    Text("Duration: $dur", color = TextMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EndDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 14.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDateNice(date: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = sdf.parse(date.split("T")[0])
        val out = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        out.format(d!!)
    } catch (_: Exception) { date }
}

// ────────────────────────────────────────────────────────────────────────────
//  Error screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorScreen(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(SurfaceDark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(message, color = TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = EndRed)) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Transcript panel
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun TranscriptPanel(
    entries: List<TranscriptItem>,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceDark
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Conversation", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            if (entries.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No conversation yet", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(entries) { item ->
                        val isUser = item.role == "user"
                        val bg = if (isUser) ActiveGreen.copy(alpha = 0.12f) else Color(0x12FFFFFF)
                        val border = if (isUser) ActiveGreen.copy(alpha = 0.15f) else Color(0x0AFFFFFF)
                        val textColor = if (isUser) TextPrimary else TextSecondary

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                ),
                                color = bg
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 280.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (isUser) "👤" else "🤖",
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        item.text,
                                        color = textColor,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  JavascriptInterface — bridges VAPI engine events to Kotlin
// ────────────────────────────────────────────────────────────────────────────

private data class TranscriptItem(
    val role: String,
    val text: String,
    val timestamp: String
)

private data class AppointmentInfo(
    val id: String = "",
    val doctorName: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "CONFIRMED"
)

private class VoiceCallEngineBridge(
    private val onStateChange: (EngineStatus) -> Unit,
    private val onTranscript: (TranscriptItem) -> Unit,
    private val onCallEnded: () -> Unit,
    private val onBookingComplete: (AppointmentInfo) -> Unit,
) {
    @JavascriptInterface
    fun onEngineState(type: String, dataJson: String) {
        val current = currentState()
        when (type) {
            "connecting" -> onStateChange(current.copy(state = EngineState.CONNECTING))
            "connected" -> onStateChange(current.copy(state = EngineState.CONNECTED))
            "listening" -> onStateChange(current.copy(state = EngineState.LISTENING))
            "speaking" -> onStateChange(current.copy(state = EngineState.SPEAKING))
            "disconnected" -> onStateChange(current.copy(state = EngineState.DISCONNECTED))
            "error" -> {
                val msg = parseJsonField(dataJson, "message") ?: "Connection error"
                onStateChange(current.copy(state = EngineState.ERROR, errorMessage = msg))
            }
            "timer" -> {
                val display = parseJsonField(dataJson, "display") ?: current.timerDisplay
                val elapsed = parseJsonField(dataJson, "elapsed")?.toIntOrNull() ?: current.timerElapsed
                onStateChange(current.copy(timerDisplay = display, timerElapsed = elapsed))
            }
            "booking_ready" -> {
                onStateChange(current.copy(bookingComplete = true, bookingInfo = parseDataMap(dataJson)))
            }
        }
    }

    @JavascriptInterface
    fun onTranscript(role: String, text: String, timestamp: String, transcriptType: String) {
        if (text.isNotBlank()) {
            onTranscript(TranscriptItem(role, text, timestamp))
        }
    }

    @JavascriptInterface
    fun onCallEnded() {
        onCallEnded()
    }

    @JavascriptInterface
    fun onBookingComplete(jsonStr: String) {
        val doctorId = parseJsonField(jsonStr, "doctorId")
        val doctorName = parseJsonField(jsonStr, "doctorName")
        val date = parseJsonField(jsonStr, "date")
        val time = parseJsonField(jsonStr, "time")

        onBookingComplete(AppointmentInfo(
            id = doctorId ?: "",
            doctorName = doctorName ?: "",
            date = date ?: "",
            time = time ?: "",
            status = "CONFIRMED"
        ))

        val current = currentState()
        onStateChange(current.copy(bookingComplete = true))
    }

    @JavascriptInterface
    fun onMetadata(key: String, value: String) {
        // Reserved for future direct metadata bridge
    }

    private fun currentState() = EngineStatus()

    private fun parseJsonField(json: String?, field: String): String? {
        if (json.isNullOrBlank() || json == "{}") return null
        val pattern = "\"$field\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    private fun parseDataMap(json: String?): Map<String, String>? {
        if (json.isNullOrBlank()) return null
        val result = mutableMapOf<String, String>()
        val pattern = "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        pattern.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (key != "message") result[key] = value
        }
        return result.ifEmpty { null }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Audio helpers
// ────────────────────────────────────────────────────────────────────────────

private fun forceSpeakerphoneOn(audioManager: AudioManager?) {
    if (audioManager == null) return
    try {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // Crank media volume
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentMusic < (maxMusic * 0.7).toInt()) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxMusic * 0.85).toInt(), 0)
        }
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val speakerDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speakerDevice != null) audioManager.setCommunicationDevice(speakerDevice)
            } catch (_: Exception) { try { audioManager.isSpeakerphoneOn = true } catch (_: Exception) {} }
        } else {
            try { audioManager.isSpeakerphoneOn = true } catch (_: Exception) {}
        }
        try { audioManager.setParameters("audioParam;force_speaker=true") } catch (_: Exception) {}
        try { audioManager.isBluetoothScoOn = false } catch (_: Exception) {}
    } catch (_: Exception) {}
}

private fun restoreSpeakerphone(audioManager: AudioManager?) {
    if (audioManager == null) return
    try {
        try { audioManager.setParameters("audioParam;force_speaker=false") } catch (_: Exception) {}
        try { audioManager.abandonAudioFocus(null) } catch (_: Exception) {}
        audioManager.mode = AudioManager.MODE_NORMAL
        try { audioManager.isSpeakerphoneOn = false } catch (_: Exception) {}
    } catch (_: Exception) {}
}

private fun nowIso(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

private fun String.escapeJs(): String =
    this.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ")
