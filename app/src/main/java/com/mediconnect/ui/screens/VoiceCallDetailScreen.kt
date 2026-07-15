package com.mediconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.model.VoiceCallDetail
import com.mediconnect.data.model.VoiceCallTranscriptEntry
import com.mediconnect.navigation.Screen
import kotlinx.coroutines.launch

/**
 * Full transcript view for a single voice call, with "Book Now" support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallDetailScreen(navController: NavController, callId: String) {
    val scope = rememberCoroutineScope()
    val api = remember { MediConnectApi.getInstance() }

    var call by remember { mutableStateOf<VoiceCallDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Booking states
    var bookingLoading by remember { mutableStateOf(false) }
    var bookingSuccess by remember { mutableStateOf(false) }
    var bookingError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(callId) {
        try {
            val response = api.getVoiceCall(callId)
            if (response.success) {
                call = response.data
            } else {
                errorMsg = response.message ?: "Failed to load call details"
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Network error"
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Details", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        val popped = navController.popBackStack()
                        if (!popped) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMsg != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                call != null -> {
                    val c = call!!

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Header card with call metadata
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = Color(0xFF22C55E),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("AI Voice Call", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (c.summary != null) {
                                        Text(c.summary!!, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Metadata rows
                                    c.startedAt?.let {
                                        DetailRow("Date", formatCallDateLong(it))
                                    }
                                    c.durationSeconds?.let {
                                        DetailRow("Duration", formatDurationLong(it))
                                    }
                                    c.transcript?.let {
                                        DetailRow("Messages", "${it.size}")
                                    }
                                    c.status?.let {
                                        DetailRow("Status", it)
                                    }

                                    // Booking status
                                    if (isAlreadyBooked(c.metadata)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF22C55E),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Appointment Booked \u2713",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF22C55E)
                                            )
                                        }
                                        c.metadata?.get("appointmentId")?.let { aptId ->
                                            Text(
                                                "Appointment #${aptId.take(8)}...",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        c.metadata?.get("appointmentDate")?.let { date ->
                                            c.metadata?.get("appointmentTime")?.let { time ->
                                                Text(
                                                    "$date at $time",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Transcript", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Transcript bubbles
                        val transcript = c.transcript ?: emptyList()
                        if (transcript.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No transcript available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(transcript) { entry ->
                                TranscriptBubble(entry = entry)
                            }
                        }

                        // "Book Now" button area (only if not already booked)
                        if (!isAlreadyBooked(c.metadata)) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                BookNowSection(
                                    metadata = c.metadata,
                                    callId = callId,
                                    navController = navController,
                                    api = api,
                                    scope = scope,
                                    bookingLoading = bookingLoading,
                                    bookingSuccess = bookingSuccess,
                                    bookingError = bookingError,
                                    onBookingLoadingChange = { bookingLoading = it },
                                    onBookingSuccessChange = { bookingSuccess = it },
                                    onBookingErrorChange = { bookingError = it }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    // Show snackbar for booking errors
                    bookingError?.let { err ->
                        Snackbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            action = {
                                TextButton(onClick = { bookingError = null }) {
                                    Text("Dismiss")
                                }
                            }
                        ) {
                            Text(err)
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Book Now" button section with progressive logic:
 * 1. Null/empty metadata → navigate to doctors list
 * 2. Has doctorId + date + time → book directly
 * 3. Has doctorName but no doctorId → fetch doctors, resolve name, then book
 */
@Composable
private fun BookNowSection(
    metadata: Map<String, String>?,
    callId: String,
    navController: NavController,
    api: MediConnectApi,
    scope: kotlinx.coroutines.CoroutineScope,
    bookingLoading: Boolean,
    bookingSuccess: Boolean,
    bookingError: String?,
    onBookingLoadingChange: (Boolean) -> Unit,
    onBookingSuccessChange: (Boolean) -> Unit,
    onBookingErrorChange: (String?) -> Unit
) {
    // Show success state
    if (bookingSuccess) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF22C55E).copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Appointment booked successfully!", fontWeight = FontWeight.SemiBold, color = Color(0xFF22C55E))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                navController.navigate(Screen.Appointments.route) {
                    popUpTo(Screen.Home.route)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("View Appointments")
        }
        return
    }

    // Extract booking info from metadata
    val doctorId = metadata?.get("doctorId") ?: metadata?.get("doctor_id")
    val doctorName = metadata?.get("doctorName") ?: metadata?.get("doctor_name")
    val appointmentDate = metadata?.get("appointmentDate") ?: metadata?.get("appointment_date") ?: metadata?.get("date")
    val appointmentTime = metadata?.get("appointmentTime") ?: metadata?.get("appointment_time") ?: metadata?.get("startTime") ?: metadata?.get("start_time")

    val hasFullInfo = doctorId != null && appointmentDate != null && appointmentTime != null
    val hasNameOnly = doctorName != null && !doctorName.isBlank() && appointmentDate != null && appointmentTime != null

    Button(
        onClick = {
            if (bookingLoading) return@Button
            onBookingLoadingChange(true)
            onBookingErrorChange(null)

            scope.launch {
                try {
                    if (hasFullInfo) {
                        // Case 2: All info available, book directly
                        val res = api.bookVoiceCall(callId, doctorId!!, appointmentDate!!, appointmentTime!!)
                        if (res.success) {
                            onBookingSuccessChange(true)
                        } else {
                            onBookingErrorChange(res.message ?: "Booking failed")
                        }
                    } else if (hasNameOnly) {
                        // Case 3: Have doctor name but no ID — resolve then book
                        try {
                            val doctorsRes = api.getDoctors()
                            val matchedDoctor = doctorsRes.data.find { d ->
                                "${d.user.firstName} ${d.user.lastName}".contains(doctorName!!, ignoreCase = true) ||
                                doctorName!!.contains(d.user.firstName, ignoreCase = true)
                            }
                            if (matchedDoctor != null) {
                                val res = api.bookVoiceCall(callId, matchedDoctor.id, appointmentDate!!, appointmentTime!!)
                                if (res.success) {
                                    onBookingSuccessChange(true)
                                } else {
                                    onBookingErrorChange(res.message ?: "Booking failed")
                                }
                            } else {
                                // Could not match doctor name — navigate to doctors list
                                onBookingErrorChange("Could not find doctor \"$doctorName\". Please select manually.")
                                navController.navigate(Screen.Doctors.createRoute()) {
                                    popUpTo(Screen.VoiceCallDetail.createRoute(callId)) { inclusive = false }
                                }
                            }
                        } catch (e: Exception) {
                            onBookingErrorChange("Error finding doctor: ${e.message}")
                        }
                    } else {
                        // Case 1: Not enough metadata — navigate to doctors list
                        navController.navigate(Screen.Doctors.createRoute()) {
                            popUpTo(Screen.VoiceCallDetail.createRoute(callId)) { inclusive = false }
                        }
                    }
                } catch (e: Exception) {
                    onBookingErrorChange(e.message ?: "Booking failed. Please try again.")
                } finally {
                    onBookingLoadingChange(false)
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = !bookingLoading
    ) {
        if (bookingLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Booking...")
        } else {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Book Now")
        }
    }

    // Show what info was found in metadata (debug hint)
    if (doctorName != null || doctorId != null || appointmentDate != null) {
        Spacer(modifier = Modifier.height(4.dp))
        val hint = buildString {
            if (doctorName != null) append("Doctor: $doctorName")
            else if (doctorId != null) append("Doctor ID set")
            if (appointmentDate != null) {
                if (isNotEmpty()) append(" | ")
                append("Date: $appointmentDate")
            }
            if (appointmentTime != null) {
                if (isNotEmpty()) append(" | ")
                append("Time: $appointmentTime")
            }
        }
        Text(
            text = hint,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }

    // Show error inline
    bookingError?.let { err ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = err,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** Check if appointment was already booked from metadata. */
private fun isAlreadyBooked(metadata: Map<String, String>?): Boolean {
    if (metadata == null) return false
    return metadata.containsKey("appointmentId") ||
           metadata["booked"]?.toBooleanStrictOrNull() == true
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TranscriptBubble(entry: VoiceCallTranscriptEntry) {
    val isUser = entry.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = entry.text,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        // Timestamp
        Text(
            text = formatTimestamp(entry.timestamp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatCallDateLong(isoDate: String): String {
    return try {
        val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdfIn.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdfIn.parse(isoDate.replace(Regex("\\.[0-9]+Z$"), "").replace("Z", ""))
            ?: return isoDate
        val sdfOut = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.US)
        sdfOut.timeZone = java.util.TimeZone.getDefault()
        sdfOut.format(date)
    } catch (_: Exception) {
        isoDate
    }
}

private fun formatDurationLong(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

private fun formatTimestamp(isoDate: String): String {
    return try {
        val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdfIn.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdfIn.parse(isoDate.replace(Regex("\\.[0-9]+Z$"), "").replace("Z", ""))
            ?: return ""
        val sdfOut = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        sdfOut.format(date)
    } catch (_: Exception) {
        ""
    }
}
