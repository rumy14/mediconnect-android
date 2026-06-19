package com.mediconnect.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.model.DoctorDetail
import com.mediconnect.data.model.TimeSlot
import com.mediconnect.navigation.Screen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDetailScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val api = remember { MediConnectApi.getInstance() }

    // Get doctorId from nav arguments
    val doctorId = navController.currentBackStackEntry?.arguments?.getString("doctorId") ?: ""

    var doctor by remember { mutableStateOf<DoctorDetail?>(null) }
    var slots by remember { mutableStateOf<List<TimeSlot>>(emptyList()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var displayDate by remember { mutableStateOf("Tomorrow") }
    var loading by remember { mutableStateOf(true) }
    var slotsLoading by remember { mutableStateOf(false) }

    // Load doctor info
    LaunchedEffect(doctorId) {
        if (doctorId.isBlank()) return@LaunchedEffect
        try {
            val resp = api.getDoctor(doctorId)
            if (resp.success) doctor = resp.data
        } catch (_: Exception) { }
        loading = false
    }

    // Load slots when date changes
    LaunchedEffect(doctorId, selectedDate) {
        if (doctorId.isBlank()) return@LaunchedEffect
        slotsLoading = true
        try {
            val resp = api.getDoctorSlots(doctorId, selectedDate)
            if (resp.success) slots = resp.data ?: emptyList()
        } catch (_: Exception) { }
        slotsLoading = false
    }

    // Format display date
    LaunchedEffect(selectedDate) {
        val date = try { LocalDate.parse(selectedDate) } catch (_: Exception) { null }
        displayDate = when {
            date == LocalDate.now() -> "Today"
            date == LocalDate.now().plusDays(1) -> "Tomorrow"
            date != null -> date.format(DateTimeFormatter.ofPattern("MMM d"))
            else -> selectedDate
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val doc = doctor

            // Doctor info
            Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (doc != null) "Dr. ${doc.user.firstName} ${doc.user.lastName}" else "Loading...",
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            if (doc != null) {
                Text(
                    doc.specialties.joinToString(", ") { it.specialty.name },
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$${doc.consultationFee.toInt()} / visit",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Bio / experience
            if (doc?.bio != null || doc?.education != null || doc?.experience != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        doc?.bio?.let {
                            Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        doc?.education?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("🎓 $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        doc?.experience?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("💼 $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Available slots
            Text(
                "Available Slots — $displayDate",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (slotsLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (slots.isEmpty()) {
                Text("No available slots for this date", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val availableSlots = slots.filter { !it.isBooked }
                if (availableSlots.isEmpty()) {
                    Text("All slots booked for this date", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSlots.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { slot ->
                                    OutlinedButton(
                                        onClick = {
                                            navController.navigate(
                                                Screen.Booking.createRoute(doctorId, selectedDate, slot.startTime)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(slot.startTime.take(5))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (doc != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            navController.navigate(
                                Screen.Booking.createRoute(doctorId, selectedDate, "now")
                            )
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Book Online", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { /* VAPI call trigger - handled by parent */ },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call to Book", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
