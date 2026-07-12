package com.mediconnect.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.mediconnect.data.model.DoctorSummary
import com.mediconnect.data.model.Specialty
import com.mediconnect.navigation.Screen
import kotlinx.coroutines.launch

private val navItems = listOf("Home", "Doctors", "Appointments")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorsScreen(
    navController: NavController,
    preselectedSpecialty: String? = null
) {
    val scope = rememberCoroutineScope()
    val api = remember { MediConnectApi.getInstance() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSpecialty by remember { mutableStateOf<String?>(preselectedSpecialty) }
    var specialties by remember { mutableStateOf<List<Specialty>>(emptyList()) }
    var doctors by remember { mutableStateOf<List<DoctorSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Load specialties & doctors
    LaunchedEffect(Unit) {
        try {
            val specsResp = api.getSpecialties()
            if (specsResp.success) specialties = specsResp.data ?: emptyList()
            val docsResp = api.getDoctors()
            if (docsResp.success) doctors = docsResp.data ?: emptyList()
        } catch (_: Exception) { /* fallback to empty */ }
        loading = false
    }

    // Filter doctors locally
    val filteredDoctors = remember(doctors, selectedSpecialty, searchQuery) {
        doctors.filter { doc ->
            val matchesSpecialty = selectedSpecialty == null ||
                doc.specialties.any { it.specialty.name == selectedSpecialty }
            val matchesSearch = searchQuery.isBlank() ||
                doc.user.firstName.contains(searchQuery, ignoreCase = true) ||
                doc.user.lastName.contains(searchQuery, ignoreCase = true) ||
                doc.specialties.any { it.specialty.name.contains(searchQuery, ignoreCase = true) }
            matchesSpecialty && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find a Doctor", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Home.route) { popUpTo(0) { saveState = true }; launchSingleTop = true } },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Doctors") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Appointments.route) { popUpTo(0) { saveState = true }; launchSingleTop = true } },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    label = { Text("Appointments") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.VoiceCallHistory.route) { popUpTo(0) { saveState = true }; launchSingleTop = true } },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Call History") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or specialty...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium
                )
            }

            // Specialty filter chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    item {
                        FilterChip(
                            selected = selectedSpecialty == null,
                            onClick = { selectedSpecialty = null },
                            label = { Text("All") }
                        )
                    }
                    items(specialties) { spec ->
                        FilterChip(
                            selected = selectedSpecialty == spec.name,
                            onClick = { selectedSpecialty = if (selectedSpecialty == spec.name) null else spec.name },
                            label = { Text(spec.name) }
                        )
                    }
                }
            }

            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredDoctors.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No doctors found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredDoctors) { doctor ->
                    val name = "Dr. ${doctor.user.firstName} ${doctor.user.lastName}"
                    val specialtyNames = doctor.specialties.joinToString(" • ") { it.specialty.name }
                    val ratingText = if (doctor.averageRating != null) "★ ${doctor.averageRating}" else null

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            navController.navigate(Screen.DoctorDetail.createRoute(doctor.id))
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar placeholder
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(specialtyNames, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (ratingText != null) {
                                    Text(ratingText, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "$${doctor.consultationFee.toInt()}",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("/ visit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
