package com.mediconnect.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.mediconnect.data.model.AppointmentSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { MediConnectApi.getInstance() }

    var appointments by remember { mutableStateOf<List<AppointmentSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf<String?>(null) } // null = Upcoming, "cancelled", etc.
    var cancelTarget by remember { mutableStateOf<AppointmentSummary?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Load appointments
    fun loadAppointments(status: String? = null) {
        scope.launch {
            loading = true
            try {
                val resp = api.getAppointments(status = if (status == "all") null else status)
                if (resp.success) appointments = resp.data
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadAppointments("all") }

    // Filter locally — API returns uppercase statuses (PENDING, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW)
    val filteredAppointments = remember(appointments, selectedFilter) {
        val upcoming = listOf("PENDING", "CONFIRMED", "SCHEDULED")
        when (selectedFilter) {
            null -> appointments.filter { it.status.uppercase() in upcoming }
            "past" -> appointments.filter { it.status.uppercase() == "COMPLETED" }
            "cancelled" -> appointments.filter { it.status.uppercase() == "CANCELLED" }
            else -> appointments
        }
    }

    // Status helpers
    fun statusLabel(status: String): String = when (status.uppercase()) {
        "PENDING", "CONFIRMED", "SCHEDULED" -> "Upcoming"
        "COMPLETED" -> "Completed"
        "CANCELLED" -> "Cancelled"
        "NO_SHOW" -> "No Show"
        else -> status.replaceFirstChar { it.uppercase() }
    }

    @Composable
    fun statusColor(status: String): androidx.compose.ui.graphics.Color = when (status.uppercase()) {
        "PENDING", "CONFIRMED", "SCHEDULED" -> MaterialTheme.colorScheme.primary
        "COMPLETED" -> MaterialTheme.colorScheme.tertiary
        "CANCELLED", "NO_SHOW" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Appointments", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    FilterChip(selected = selectedFilter == null, onClick = { selectedFilter = null }, label = { Text("Upcoming") })
                    FilterChip(selected = selectedFilter == "past", onClick = { selectedFilter = "past" }, label = { Text("Past") })
                    FilterChip(selected = selectedFilter == "cancelled", onClick = { selectedFilter = "cancelled" }, label = { Text("Cancelled") })
                    FilterChip(selected = selectedFilter == "all", onClick = { selectedFilter = "all" }, label = { Text("All") })
                }
            }

            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredAppointments.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No appointments found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredAppointments) { appointment ->
                    val doctor = appointment.doctor
                    val name = "Dr. ${doctor.user.firstName} ${doctor.user.lastName}"
                    val specialty = doctor.specialties?.joinToString(", ") { it.specialty.name } ?: ""
                    val dateTime = "${appointment.appointmentDate} • ${appointment.startTime.take(5)}"
                    val status = statusLabel(appointment.status)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (specialty.isNotBlank()) {
                                    Text(specialty, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(dateTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            }
                            // Cancel button for upcoming
                            if (appointment.status.uppercase() in listOf("PENDING", "CONFIRMED", "SCHEDULED")) {
                                IconButton(onClick = { cancelTarget = appointment }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(status, fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = statusColor(appointment.status))
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Cancel confirmation dialog
    cancelTarget?.let { appt ->
        val docName = "Dr. ${appt.doctor.user.firstName} ${appt.doctor.user.lastName}"
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancel Appointment?") },
            text = {
                Text("Cancel your $docName appointment on ${appt.appointmentDate} at ${appt.startTime.take(5)}?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetId = appt.id
                    cancelTarget = null
                    scope.launch {
                        try {
                            val resp = api.cancelAppointment(targetId, "Cancelled by patient")
                            if (resp.success) {
                                snackbarHostState.showSnackbar("✅ Appointment cancelled")
                                loadAppointments()
                            } else {
                                snackbarHostState.showSnackbar(resp.error ?: "Could not cancel appointment")
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Could not cancel appointment")
                        }
                    }
                }) {
                    Text("Yes, cancel it", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) {
                    Text("Keep it")
                }
            }
        )
    }
}
