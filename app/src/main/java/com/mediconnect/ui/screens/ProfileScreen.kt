package com.mediconnect.ui.screens

import androidx.compose.foundation.layout.*
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
import com.mediconnect.data.model.UserResponse
import com.mediconnect.data.session.SessionManager
import com.mediconnect.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session = remember { SessionManager.getInstance(context) }
    val api = remember { MediConnectApi.getInstance() }

    var user by remember { mutableStateOf<UserResponse?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Load user profile
    LaunchedEffect(Unit) {
        try {
            val resp = api.getMe()
            if (resp.success) user = resp.data
        } catch (_: Exception) { }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
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
                    selected = false,
                    onClick = { navController.navigate(Screen.Doctors.createRoute()) { popUpTo(0) { saveState = true }; launchSingleTop = true } },
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            if (loading) {
                CircularProgressIndicator()
                return@Column
            }

            val u = user

            // Avatar
            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (u != null) "${u.firstName} ${u.lastName}" else "User",
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                u?.email ?: "",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (u?.phone != null) {
                Text(u.phone, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()

            // Info rows
            ProfileInfoRow(Icons.Default.Person, "Role", u?.role?.replaceFirstChar { it.uppercase() } ?: "Patient")
            if (u?.createdAt != null) {
                ProfileInfoRow(Icons.Default.CalendarMonth, "Member since", u.createdAt.take(10))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Sign Out button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        session.clearSession()
                        api.setToken(null)
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    HorizontalDivider()
}
