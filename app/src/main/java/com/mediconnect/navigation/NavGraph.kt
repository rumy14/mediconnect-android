package com.mediconnect.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.session.SessionManager
import com.mediconnect.ui.screens.*

@Composable
fun MediConnectNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val api = remember { MediConnectApi.getInstance() }

    var startDestination by remember { mutableStateOf<String?>(null) }

    // Check auth state and restore token on startup
    LaunchedEffect(Unit) {
        try {
            val session = SessionManager.getInstance(context)
            val token = session.getToken()
            if (!token.isNullOrBlank()) {
                api.setToken(token)
                startDestination = Screen.Home.route
            } else {
                startDestination = Screen.Login.route
            }
        } catch (_: Exception) {
            startDestination = Screen.Login.route
        }
    }

    // Wait until start destination is determined
    val destination = startDestination ?: return

    NavHost(navController = navController, startDestination = destination) {

        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }

        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = Screen.Doctors.route,
            arguments = listOf(
                navArgument("specialty") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val preselectedSpecialty = backStackEntry.arguments?.getString("specialty")
            DoctorsScreen(
                navController = navController,
                preselectedSpecialty = preselectedSpecialty
            )
        }

        composable(
            route = Screen.DoctorDetail.route,
            arguments = listOf(
                navArgument("doctorId") { type = NavType.StringType }
            )
        ) {
            DoctorDetailScreen(navController = navController)
        }

        composable(
            route = Screen.Booking.route,
            arguments = listOf(
                navArgument("doctorId") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType },
                navArgument("startTime") { type = NavType.StringType }
            )
        ) {
            BookingScreen(navController = navController)
        }

        composable(Screen.Appointments.route) {
            AppointmentsScreen(navController = navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }

        composable(Screen.VoiceCallHistory.route) {
            VoiceCallHistoryScreen(navController = navController)
        }

        composable(
            route = Screen.VoiceCallDetail.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: return@composable
            VoiceCallDetailScreen(navController = navController, callId = callId)
        }
    }
}
