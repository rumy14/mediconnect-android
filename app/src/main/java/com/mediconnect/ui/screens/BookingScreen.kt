package com.mediconnect.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.mediconnect.navigation.Screen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    // Get route params
    val backStack = navController.currentBackStackEntry
    val doctorId = backStack?.arguments?.getString("doctorId") ?: ""
    val dateStr = backStack?.arguments?.getString("date") ?: ""
    val startTime = backStack?.arguments?.getString("startTime") ?: ""

    var reason by remember { mutableStateOf("") }
    var doctor by remember { mutableStateOf<DoctorDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var booking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Load doctor details
    LaunchedEffect(doctorId) {
        if (doctorId.isNotBlank()) {
            try {
                val resp = api.getDoctor(doctorId)
                if (resp.success) doctor = resp.data
            } catch (_: Exception) { }
        }
        loading = false
    }

    // Format date for display
    val displayDate = remember(dateStr) {
        try {
            val date = LocalDate.parse(dateStr)
            date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        } catch (_: Exception) {
            dateStr.ifBlank { "—" }
        }
    }

    val displayTime = remember(startTime) {
        if (startTime.length >= 5) startTime.take(5) else startTime
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Appointment", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
        ) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val doc = doctor

            // Error message
            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            // Summary card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Appointment Summary", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow(
                        "Doctor:",
                        if (doc != null) "Dr. ${doc.user.firstName} ${doc.user.lastName}" else "—"
                    )
                    DetailRow(
                        "Specialty:",
                        doc?.specialties?.joinToString(", ") { it.specialty.name } ?: "—"
                    )
                    DetailRow("Date:", displayDate)
                    DetailRow("Time:", displayTime)
                    DetailRow(
                        "Fee:",
                        if (doc != null) "$${doc.consultationFee.toInt()}.00" else "—"
                    )
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
                                Toast.makeText(context, "Appointment booked!", Toast.LENGTH_SHORT).show()
                                navController.navigate(Screen.Appointments.route) {
                                    popUpTo(Screen.Home.route)
                                }
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Confirm Booking", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
