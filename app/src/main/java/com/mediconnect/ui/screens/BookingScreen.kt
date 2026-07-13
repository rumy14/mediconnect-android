package com.mediconnect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.mediconnect.navigation.Screen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.model.CreateAppointmentRequest
import com.mediconnect.data.model.DoctorDetail
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { MediConnectApi.getInstance() }

    val backStack = navController.currentBackStackEntry
    val doctorId = backStack?.arguments?.getString("doctorId") ?: ""
    val dateStr = backStack?.arguments?.getString("date") ?: ""
    val startTime = backStack?.arguments?.getString("startTime") ?: ""

    var reason by remember { mutableStateOf("") }
    var doctor by remember { mutableStateOf<DoctorDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var booking by remember { mutableStateOf(false) }
    var bookedSuccess by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(doctorId) {
        if (doctorId.isNotBlank()) {
            try {
                val resp = api.getDoctor(doctorId)
                if (resp.success) doctor = resp.data
            } catch (_: Exception) { }
        }
        loading = false
    }

    val displayDate = remember(dateStr) {
        try {
            val date = LocalDate.parse(dateStr)
            date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        } catch (_: Exception) { dateStr.ifBlank { "\u2014" } }
    }

    val displayTime = remember(startTime) {
        if (startTime.length >= 5) startTime.take(5) else startTime
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Appointment", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } } }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            val doc = doctor

            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Appointment Summary", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("Doctor:", if (doc != null) "Dr. ${doc.user.firstName} ${doc.user.lastName}" else "\u2014")
                    DetailRow("Specialty:", doc?.specialties?.joinToString(", ") { it.specialty.name } ?: "\u2014")
                    DetailRow("Date:", displayDate)
                    DetailRow("Time:", displayTime)
                    DetailRow("Fee:", if (doc != null) "$${doc.consultationFee.toInt()}.00" else "\u2014")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason for visit (optional)") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 4,
                enabled = !booking
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (doctorId.isBlank() || dateStr.isBlank() || startTime.isBlank()) {
                        errorMsg = "Missing appointment details. Please go back and try again."
                        return@Button
                    }
                    errorMsg = null
                    booking = true
                    scope.launch {
                        try {
                            val resp = api.bookAppointment(
                                CreateAppointmentRequest(
                                    doctorId = doctorId,
                                    appointmentDate = dateStr,
                                    startTime = startTime,
                                    reason = reason.trim().ifBlank { null }
                                )
                            )
                            if (resp.success) {
                                bookedSuccess = true
                                booking = false
                            } else {
                                errorMsg = resp.error ?: resp.message ?: "Booking failed. Please try again."
                                booking = false
                            }
                        } catch (e: Exception) {
                            val msg = e.message ?: ""
                            errorMsg = when {
                                msg.contains("401") -> "Session expired. Please log in again."
                                msg.contains("timeout") -> "Connection timed out."
                                msg.contains("resolve") || msg.contains("connect") -> "Could not reach the server."
                                else -> "Booking failed: ${e.message}"
                            }
                            booking = false
                        }
                    }
                },
                enabled = !booking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (booking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Confirm Booking", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }

    // Success dialog
    if (bookedSuccess) {
        val doc = doctor
        AlertDialog(
            onDismissRequest = {
                bookedSuccess = false
                navController.navigate(Screen.Appointments.route) { popUpTo(Screen.Home.route) }
            },
            icon = {
                Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF22C55E).copy(alpha = 0.15f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color(0xFF22C55E)) }
                }
            },
            title = { Text("Appointment Booked!", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your appointment has been confirmed", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            BookingSummaryRow("Doctor", if (doc != null) "Dr. ${doc.user.firstName} ${doc.user.lastName}" else "\u2014")
                            BookingSummaryRow("Specialty", doc?.specialties?.joinToString(", ") { it.specialty.name } ?: "\u2014")
                            BookingSummaryRow("Date", displayDate)
                            BookingSummaryRow("Time", displayTime)
                            if (doc != null) BookingSummaryRow("Fee", "$${doc.consultationFee.toInt()}.00")
                            if (reason.isNotBlank()) BookingSummaryRow("Reason", reason)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    bookedSuccess = false
                    navController.navigate(Screen.Appointments.route) { popUpTo(Screen.Home.route) }
                }) { Text("View My Appointments", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    bookedSuccess = false
                    navController.navigate(Screen.Home.route) { popUpTo(0) }
                }) { Text("Back to Home") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun BookingSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
