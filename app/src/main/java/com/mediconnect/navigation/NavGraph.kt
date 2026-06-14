package com.mediconnect.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mediconnect.ui.screens.*

@Composable
fun MediConnectNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Login.route) {

        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }

        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Doctors.route) {
            DoctorsScreen(navController = navController)
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

        composable(
            route = Screen.AppointmentDetail.route,
            arguments = listOf(
                navArgument("appointmentId") { type = NavType.StringType }
            )
        ) {
            AppointmentsScreen(navController = navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }
    }
}
