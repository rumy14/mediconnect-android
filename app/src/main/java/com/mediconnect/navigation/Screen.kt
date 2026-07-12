package com.mediconnect.navigation

/**
 * Navigation routes for MediConnect app.
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object Doctors : Screen("doctors?specialty={specialty}") {
        fun createRoute(specialty: String? = null) =
            if (specialty != null) "doctors?specialty=$specialty" else "doctors"
    }
    data object DoctorDetail : Screen("doctors/{doctorId}") {
        fun createRoute(doctorId: String) = "doctors/$doctorId"
    }
    data object Booking : Screen("booking/{doctorId}/{date}/{startTime}") {
        fun createRoute(doctorId: String, date: String, startTime: String) =
            "booking/$doctorId/$date/$startTime"
    }
    data object Appointments : Screen("appointments")
    data object AppointmentDetail : Screen("appointments/{appointmentId}") {
        fun createRoute(appointmentId: String) = "appointments/$appointmentId"
    }
    data object Profile : Screen("profile")
    data object VoiceCallHistory : Screen("call-history")
    data object VoiceCallDetail : Screen("call-history/{callId}") {
        fun createRoute(callId: String) = "call-history/$callId"
    }
}
