package com.mediconnect.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.mediconnect.data.model.Specialty
import com.mediconnect.data.session.SessionManager
import com.mediconnect.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { SessionManager.getInstance(context) }
    val api = remember { MediConnectApi.getInstance() }
    val userName by session.userNameFlow.collectAsState(initial = null)

    var specialties by remember { mutableStateOf<List<Specialty>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Load specialties on first composition
    LaunchedEffect(Unit) {
        try {
            val response = api.getSpecialties()
            if (response.success) {
                specialties = response.data ?: emptyList()
            }
        } catch (_: Exception) { /* silently fall back to empty */ }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediConnect", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
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
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Doctors.createRoute()) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Doctors") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Appointments.route) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    label = { Text("Appointments") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (userName != null) "Welcome, ${userName}!" else "Welcome to MediConnect",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Book appointments with top doctors in your area.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Quick actions
            item {
                Text("Quick Actions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard(
                        icon = Icons.Default.Search,
                        label = "Find a Doctor",
                        onClick = { navController.navigate(Screen.Doctors.createRoute()) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Default.CalendarMonth,
                        label = "My Appointments",
                        onClick = { navController.navigate(Screen.Appointments.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Browse by Specialty
            item {
                Text("Browse by Specialty", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }

            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (specialties.isEmpty()) {
                // Fallback emoji mapping for common specialties
                val fallback = listOf(
                    "General Medicine" to "🩺",
                    "Cardiology" to "❤️",
                    "Dermatology" to "🧴",
                    "Pediatrics" to "👶",
                    "Orthopedics" to "🦴",
                    "Neurology" to "🧠"
                )
                items(fallback) { (name, emoji) ->
                    SpecialtyCard(emoji = emoji, name = name, onClick = {
                        navController.navigate(Screen.Doctors.createRoute(specialty = name))
                    })
                }
            } else {
                // Emoji mapping for specialty icons from API data
                val emojiMap = mapOf(
                    "general" to "🩺", "cardiology" to "❤️", "dermatology" to "🧴",
                    "pediatrics" to "👶", "orthopedics" to "🦴", "neurology" to "🧠",
                    "ophthalmology" to "👁️", "ent" to "👂", "dentistry" to "🦷",
                    "psychiatry" to "🧠", "gynecology" to "♀️", "urology" to "🫁",
                    "gastroenterology" to "🫃", "endocrinology" to "🩻"
                )
                items(specialties) { specialty ->
                    val emoji = emojiMap.entries.firstOrNull { (k, _) ->
                        specialty.name.contains(k, ignoreCase = true) ||
                        k.contains(specialty.name, ignoreCase = true)
                    }?.value ?: "🩺"
                    SpecialtyCard(emoji = emoji, name = specialty.name, onClick = {
                        navController.navigate(Screen.Doctors.createRoute(specialty = specialty.name))
                    })
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SpecialtyCard(emoji: String, name: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
