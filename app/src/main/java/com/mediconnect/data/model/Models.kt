package com.mediconnect.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Generic API wrappers ──

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class PaginatedResponse<T>(
    val success: Boolean,
    val data: List<T>,
    val pagination: Pagination
)

@Serializable
data class Pagination(
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

// ── Auth ──

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val role: String = "PATIENT"
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val data: AuthData,
    val message: String? = null
)

@Serializable
data class AuthData(
    val user: UserResponse,
    val token: String
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val role: String,
    val isActive: Boolean? = null,
    val createdAt: String? = null
)

// ── Specialty ──

@Serializable
data class Specialty(
    val id: String,
    val name: String,
    val icon: String? = null,
    val description: String? = null,
    @SerialName("_count")
    val count: DoctorCount? = null
)

@Serializable
data class DoctorCount(val doctors: Int = 0)

// ── Doctor ──

@Serializable
data class DoctorSummary(
    val id: String,
    val user: UserName,
    val specialties: List<DoctorSpecialtyInfo>,
    val consultationFee: Double,
    val averageRating: Float? = null,
    val totalReviews: Int = 0,
    val isAvailable: Boolean = true
)

@Serializable
data class DoctorDetail(
    val id: String,
    val user: UserNameWithEmail,
    val bio: String? = null,
    val education: String? = null,
    val experience: String? = null,
    val consultationFee: Double,
    val averageRating: Float? = null,
    val totalReviews: Int = 0,
    val specialties: List<DoctorSpecialtyInfo>
)

@Serializable
data class UserName(
    val id: String,
    val firstName: String,
    val lastName: String
)

@Serializable
data class UserNameWithEmail(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null
)

@Serializable
data class DoctorSpecialtyInfo(
    val specialty: SpecialtyInfo
)

@Serializable
data class SpecialtyInfo(
    val id: String,
    val name: String,
    val icon: String? = null,
    val description: String? = null
)

// ── Time Slot ──

@Serializable
data class TimeSlot(
    val id: String,
    val doctorId: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val isBooked: Boolean = false
)

// ── Appointment ──

@Serializable
data class CreateAppointmentRequest(
    val doctorId: String,
    val appointmentDate: String,
    val startTime: String,
    val reason: String? = null
)

@Serializable
data class AppointmentSummary(
    val id: String,
    val appointmentDate: String,
    val startTime: String,
    val endTime: String,
    val status: String,
    val reason: String? = null,
    val patient: UserName,
    val doctor: DoctorAppointmentInfo
)

@Serializable
data class DoctorAppointmentInfo(
    val id: String,
    val consultationFee: Double,
    val user: UserName,
    val specialties: List<DoctorSpecialtyInfo>? = null
)

@Serializable
data class AppointmentDetail(
    val id: String,
    val appointmentDate: String,
    val startTime: String,
    val endTime: String,
    val status: String,
    val reason: String? = null,
    val notes: String? = null,
    val patient: UserNameWithEmail,
    val doctor: DoctorDetailInfo
)

@Serializable
data class DoctorDetailInfo(
    val id: String,
    val consultationFee: Double,
    val bio: String? = null,
    val user: UserNameWithEmail,
    val specialties: List<DoctorSpecialtyInfo>
)
