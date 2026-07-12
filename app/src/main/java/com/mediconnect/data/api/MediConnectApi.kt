package com.mediconnect.data.api

import com.mediconnect.BuildConfig
import com.mediconnect.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * MediConnect REST API client.
 * Uses Ktor for lightweight, coroutine-native HTTP.
 */
class MediConnectApi private constructor() {

    companion object {
        @Volatile
        private var instance: MediConnectApi? = null

        /** Get or create the singleton API client. */
        fun getInstance(): MediConnectApi {
            return instance ?: synchronized(this) {
                instance ?: MediConnectApi().also { instance = it }
            }
        }

        /** Convenience: restore token from SessionManager and return the instance. */
        suspend fun init(context: android.content.Context): MediConnectApi {
            val api = getInstance()
            val token = com.mediconnect.data.session.SessionManager.getInstance(context).getToken()
            if (!token.isNullOrBlank()) {
                api.setToken(token)
            }
            return api
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        prettyPrint = false
    }

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.BODY
            logger = Logger.DEFAULT
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            url(BuildConfig.API_BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }

    private var token: String? = null

    fun setToken(newToken: String?) {
        token = newToken
    }

    private fun HttpRequestBuilder.withAuth() {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    // ── Auth ──

    suspend fun register(body: RegisterRequest): AuthResponse =
        client.post("auth/register") { setBody(body) }.body()

    suspend fun login(body: LoginRequest): AuthResponse =
        client.post("auth/login") { setBody(body) }.body()

    suspend fun getMe(): ApiResponse<UserResponse> =
        client.get("auth/me") { withAuth() }.body()

    // ── Specialties ──

    suspend fun getSpecialties(): ApiResponse<List<Specialty>> =
        client.get("specialties").body()

    // ── Doctors ──

    suspend fun getDoctors(specialty: String? = null, page: Int = 1): PaginatedResponse<DoctorSummary> =
        client.get("doctors") {
            parameter("page", page)
            parameter("limit", 20)
            specialty?.let { parameter("specialty", it) }
        }.body()

    suspend fun getDoctor(id: String): ApiResponse<DoctorDetail> =
        client.get("doctors/$id").body()

    suspend fun getDoctorSlots(doctorId: String, date: String): ApiResponse<List<TimeSlot>> =
        client.get("doctors/$doctorId/slots") {
            parameter("date", date)
        }.body()

    // ── Appointments ──

    suspend fun bookAppointment(body: CreateAppointmentRequest): ApiResponse<AppointmentDetail> =
        client.post("appointments") {
            withAuth()
            setBody(body)
        }.body()

    suspend fun getAppointments(page: Int = 1, status: String? = null, limit: Int = 20): PaginatedResponse<AppointmentSummary> =
        client.get("appointments") {
            withAuth()
            parameter("page", page)
            parameter("limit", limit)
            status?.let { parameter("status", it) }
        }.body()

    suspend fun getAppointment(id: String): ApiResponse<AppointmentDetail> =
        client.get("appointments/$id") { withAuth() }.body()

    suspend fun cancelAppointment(id: String, reason: String? = null): ApiResponse<AppointmentDetail> =
        client.patch("appointments/$id/cancel") {
            withAuth()
            reason?.let { setBody(mapOf("reason" to it)) }
        }.body()

    // ── Voice Call History ──

    suspend fun saveVoiceCall(body: SaveVoiceCallRequest): ApiResponse<VoiceCallResponse> =
        client.post("vapi/calls") {
            withAuth()
            setBody(body)
        }.body()

    suspend fun getVoiceCalls(page: Int = 1, limit: Int = 20): PaginatedResponse<VoiceCallSummary> =
        client.get("vapi/calls") {
            withAuth()
            parameter("page", page)
            parameter("limit", limit)
        }.body()

    suspend fun getVoiceCall(id: String): ApiResponse<VoiceCallDetail> =
        client.get("vapi/calls/$id") { withAuth() }.body()

    suspend fun deleteVoiceCall(id: String): ApiResponse<Unit> =
        client.delete("vapi/calls/$id") { withAuth() }.body()
}
