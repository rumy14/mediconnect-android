package com.mediconnect.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.model.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val ActiveBlue = Color(0xFF2563EB)
private val CardBg = Color(0xFF0F172A)
private val SurfaceDark = Color(0xFF0A0A0F)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF475569)
private val ActiveGreen = Color(0xFF22C55E)
private val DividerColor = Color(0xFF1E293B)

private enum class BookingStep { DOCTOR, DATE, TIME, REASON, CONFIRM }

private val stepEnter = slideInHorizontally { it / 4 } + fadeIn()
private val stepExit = slideOutHorizontally { -it / 4 } + fadeOut()

// ────────────────────────────────────────────────────────────────────────────

@Composable
fun SuggestionBookingDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onBookingSuccess: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MediConnectApi.getInstance() }
    val session = remember { com.mediconnect.data.session.SessionManager.getInstance(context) }

    var doctors by remember { mutableStateOf<List<DoctorSummary>>(emptyList()) }
    var loadingDoctors by remember { mutableStateOf(true) }
    var doctorError by remember { mutableStateOf<String?>(null) }

    var selectedDoctor by remember { mutableStateOf<DoctorSummary?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedTimeSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var reason by remember { mutableStateOf("") }

    var currentStep by remember { mutableStateOf(BookingStep.DOCTOR) }
    var availableSlots by remember { mutableStateOf<List<TimeSlot>>(emptyList()) }
    var loadingSlots by remember { mutableStateOf(false) }
    var isBooking by remember { mutableStateOf(false) }
    var bookingError by remember { mutableStateOf<String?>(null) }
    var bookingSuccess by remember { mutableStateOf(false) }
    var bookedAppointment by remember { mutableStateOf<AppointmentDetail?>(null) }
    var doctorSearch by remember { mutableStateOf("") }

    // Calendar state
    val today = remember { Calendar.getInstance() }
    var calMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var calYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // Load doctors
    LaunchedEffect(Unit) {
        try {
            val resp = api.getDoctors()
            if (resp.success) {
                doctors = resp.data
            } else {
                doctorError = "Failed to load doctors"
            }
        } catch (e: Exception) {
            doctorError = e.message ?: "Failed to load doctors"
        }
        loadingDoctors = false
    }

    // Fetch slots when doctor + date selected
    LaunchedEffect(selectedDoctor, selectedDate) {
        if (selectedDoctor != null && selectedDate != null) {
            loadingSlots = true
            try {
                val resp = api.getDoctorSlots(selectedDoctor!!.id, selectedDate!!)
                if (resp.success) {
                    availableSlots = resp.data?.filter { !it.isBooked } ?: emptyList()
                } else {
                    availableSlots = emptyList()
                }
            } catch (_: Exception) {
                availableSlots = emptyList()
            }
            loadingSlots = false
        }
    }

    // ── Dialog ──
    Dialog(
        onDismissRequest = { if (!isBooking && !bookingSuccess) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep != BookingStep.DOCTOR && !bookingSuccess) {
                        IconButton(onClick = {
                            currentStep = when (currentStep) {
                                BookingStep.TIME -> BookingStep.DATE
                                BookingStep.REASON -> BookingStep.TIME
                                BookingStep.CONFIRM -> BookingStep.REASON
                                else -> BookingStep.DOCTOR
                            }
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }

                    Spacer(Modifier.weight(1f))
                    Text(
                        if (bookingSuccess) "Appointment Booked! ✅"
                        else "Book via Suggestion",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.weight(1f))

                    if (!bookingSuccess && !isBooking) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }

                // Step dots
                if (!bookingSuccess) {
                    Spacer(Modifier.height(8.dp))
                    val steps = BookingStep.entries.filter { it != BookingStep.CONFIRM }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { i, step ->
                            val isActive = currentStep == step
                            val isDone = currentStep.ordinal > step.ordinal
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 10.dp else 7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            isDone -> ActiveGreen
                                            isActive -> ActiveBlue
                                            else -> TextMuted.copy(alpha = 0.4f)
                                        }
                                    )
                            )
                            if (i < steps.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(2.dp)
                                        .background(if (isDone) ActiveGreen else TextMuted.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Content ──
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = if (bookingSuccess) "success" else currentStep.name,
                        transitionSpec = { stepEnter togetherWith stepExit },
                        label = "step"
                    ) { target ->
                        when {
                            target == "success" -> BookingSuccessContent(bookedAppointment)
                            currentStep == BookingStep.DOCTOR -> {
                                DoctorSelector(
                                    doctors = doctors, loading = loadingDoctors, error = doctorError,
                                    search = doctorSearch, selectedDoctor = selectedDoctor,
                                    onSearchChange = { doctorSearch = it },
                                    onSelect = { selectedDoctor = it; currentStep = BookingStep.DATE }
                                )
                            }
                            currentStep == BookingStep.DATE -> {
                                DateSelector(
                                    today = today,
                                    calMonth = calMonth,
                                    calYear = calYear,
                                    selectedDay = selectedDay,
                                    onDaySelect = { day ->
                                        selectedDay = day
                                        selectedDate = String.format(Locale.US, "%04d-%02d-%02d", calYear, calMonth + 1, day)
                                        currentStep = BookingStep.TIME
                                    },
                                    onMonthChange = { calMonth = it },
                                    onYearChange = { calYear = it }
                                )
                            }
                            currentStep == BookingStep.TIME -> {
                                TimeSlotSelector(
                                    slots = availableSlots, loading = loadingSlots,
                                    selectedTimeSlot = selectedTimeSlot,
                                    doctorName = "${selectedDoctor?.user?.firstName ?: ""} ${selectedDoctor?.user?.lastName ?: ""}",
                                    date = selectedDate ?: "",
                                    onSelect = { selectedTimeSlot = it; currentStep = BookingStep.REASON }
                                )
                            }
                            currentStep == BookingStep.REASON -> {
                                ReasonInput(
                                    reason = reason,
                                    onReasonChange = { reason = it },
                                    onNext = { currentStep = BookingStep.CONFIRM }
                                )
                            }
                            currentStep == BookingStep.CONFIRM -> {
                                ConfirmBooking(
                                    doctor = selectedDoctor, date = selectedDate ?: "",
                                    timeSlot = selectedTimeSlot, reason = reason,
                                    isBooking = isBooking, bookingError = bookingError,
                                    onConfirm = {
                                        scope.launch {
                                            isBooking = true; bookingError = null
                                            try {
                                                val r = api.bookAppointment(
                                                    CreateAppointmentRequest(
                                                        doctorId = selectedDoctor!!.id,
                                                        appointmentDate = selectedDate!!,
                                                        startTime = selectedTimeSlot!!.startTime,
                                                        reason = reason.ifBlank { "Booked via suggestion" }
                                                    )
                                                )
                                                if (r.success && r.data != null) {
                                                    bookedAppointment = r.data; bookingSuccess = true
                                                } else {
                                                    bookingError = r.error ?: r.message ?: "Booking failed"
                                                }
                                            } catch (e: Exception) {
                                                bookingError = e.message ?: "Booking failed"
                                            }
                                            isBooking = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Success close button
                if (bookingSuccess) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onDismiss(); onBookingSuccess() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveBlue)
                    ) {
                        Text("View My Appointments", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Step composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DoctorSelector(
    doctors: List<DoctorSummary>, loading: Boolean, error: String?,
    search: String, selectedDoctor: DoctorSummary?,
    onSearchChange: (String) -> Unit, onSelect: (DoctorSummary) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text("Select a Doctor", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("Choose who you'd like to see", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search, onValueChange = onSearchChange,
            placeholder = { Text("Search...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ActiveBlue, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = ActiveBlue),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        val filtered = if (search.isBlank()) doctors
        else doctors.filter { d ->
            "${d.user.firstName} ${d.user.lastName}".contains(search, ignoreCase = true) ||
            d.specialties.any { it.specialty.name.contains(search, ignoreCase = true) }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ActiveBlue) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "", color = TextMuted) }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 36.sp); Spacer(Modifier.height(8.dp))
                    Text("No doctors found", color = TextMuted, fontSize = 14.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { doc ->
                    val isSelected = selectedDoctor?.id == doc.id
                    Surface(
                        onClick = { onSelect(doc) }, shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ActiveBlue.copy(alpha = 0.12f) else CardBg
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ActiveBlue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Text("👨‍⚕️", fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${doc.user.firstName} ${doc.user.lastName}", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(doc.specialties.joinToString(", ") { it.specialty.name }, color = TextSecondary, fontSize = 12.sp)
                            }
                            Text("$${doc.consultationFee.toInt()}", color = ActiveGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (isSelected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActiveGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Date calendar ──
@Composable
private fun DateSelector(
    today: Calendar, calMonth: Int, calYear: Int, selectedDay: Int?,
    onDaySelect: (Int) -> Unit, onMonthChange: (Int) -> Unit, onYearChange: (Int) -> Unit
) {
    val monthNames = listOf("January","February","March","April","May","June","July","August","September","October","November","December")
    val dayNames = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")

    fun daysInMonth(m: Int, y: Int): Int {
        val c = Calendar.getInstance(); c.set(y, m, 1); return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    fun monthStartDay(m: Int, y: Int): Int {
        val c = Calendar.getInstance(); c.set(y, m, 1); return c.get(Calendar.DAY_OF_WEEK) - 1
    }
    fun isPast(d: Int, m: Int, y: Int): Boolean {
        val c = Calendar.getInstance(); c.set(y, m, d); return c.before(Calendar.getInstance())
    }

    Column(Modifier.fillMaxSize()) {
        Text("Pick a Date", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("Choose when you'd like to visit", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (calMonth > 0) onMonthChange(calMonth - 1) else { onMonthChange(11); onYearChange(calYear - 1) }
            }) { Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = TextSecondary) }
            Text("${monthNames[calMonth]} $calYear", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = {
                if (calMonth < 11) onMonthChange(calMonth + 1) else { onMonthChange(0); onYearChange(calYear + 1) }
            }) { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary) }
        }
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            dayNames.forEach { Text(it, modifier = Modifier.weight(1f), color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium) }
        }
        Spacer(Modifier.height(6.dp))

        var dayCounter = 1
        val totalDays = daysInMonth(calMonth, calYear)
        val startDay = monthStartDay(calMonth, calYear)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (week in 0 until 6) {
                if (dayCounter > totalDays) break
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val d = if (week == 0 && col < startDay) null else if (dayCounter > totalDays) null else dayCounter++
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (d != null) {
                                val past = isPast(d, calMonth, calYear)
                                val sel = selectedDay == d
                                val isToday = today.get(Calendar.DAY_OF_MONTH) == d && today.get(Calendar.MONTH) == calMonth && today.get(Calendar.YEAR) == calYear
                                Surface(
                                    onClick = { if (!past) onDaySelect(d) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = when { sel -> ActiveBlue; isToday -> ActiveBlue.copy(alpha = 0.15f); else -> Color.Transparent }
                                ) {
                                    Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                                        Text(d.toString(), fontSize = 14.sp,
                                            fontWeight = if (sel || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = when { past -> TextMuted.copy(alpha = 0.4f); sel -> Color.White; isToday -> ActiveBlue; else -> TextPrimary }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Time slots ──
@Composable
private fun TimeSlotSelector(
    slots: List<TimeSlot>, loading: Boolean, selectedTimeSlot: TimeSlot?,
    doctorName: String, date: String, onSelect: (TimeSlot) -> Unit
) {
    val formattedDate = remember(date) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = sdf.parse(date); val out = SimpleDateFormat("EEEE, MMMM d", Locale.US)
            out.format(d!!)
        } catch (_: Exception) { date }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Pick a Time", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("$doctorName — $formattedDate", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ActiveBlue)
                    Spacer(Modifier.height(8.dp)); Text("Loading...", color = TextMuted, fontSize = 13.sp)
                }
            }
            slots.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏰", fontSize = 36.sp); Spacer(Modifier.height(8.dp))
                    Text("No slots available", color = TextMuted, fontSize = 14.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val morning = slots.filter { it.startTime < "12:00" }
                val afternoon = slots.filter { it.startTime >= "12:00" && it.startTime < "17:00" }
                val evening = slots.filter { it.startTime >= "17:00" }
                listOf("Morning" to morning, "Afternoon" to afternoon, "Evening" to evening)
                    .filter { it.second.isNotEmpty() }
                    .forEach { (label, group) ->
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                        }
                        group.chunked(3).forEach { row ->
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { slot ->
                                        val sel = selectedTimeSlot?.id == slot.id
                                        Surface(onClick = { onSelect(slot) }, shape = RoundedCornerShape(10.dp), color = if (sel) ActiveBlue else CardBg, modifier = Modifier.weight(1f)) {
                                            Box(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(slot.startTime.take(5), color = if (sel) Color.White else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("- ${slot.endTime.take(5)}", color = if (sel) Color.White.copy(alpha = 0.7f) else TextMuted, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Reason ──
@Composable
private fun ReasonInput(reason: String, onReasonChange: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Reason (Optional)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("Briefly describe why you're visiting", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = reason, onValueChange = onReasonChange,
            placeholder = { Text("e.g. Routine checkup...", color = TextMuted) },
            modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ActiveBlue, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = ActiveBlue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onNext() })
        )
        Spacer(Modifier.weight(1f))
        Text("Quick options", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Checkup", "Follow-up", "Consultation").forEach { option ->
                Surface(onClick = { onReasonChange(option) }, shape = RoundedCornerShape(8.dp), color = if (reason == option) ActiveBlue.copy(alpha = 0.15f) else CardBg) {
                    Text(option, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (reason == option) ActiveBlue else TextSecondary, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ActiveBlue)) {
            Text("Review & Book", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ── Confirm ──
@Composable
private fun ConfirmBooking(
    doctor: DoctorSummary?, date: String, timeSlot: TimeSlot?, reason: String,
    isBooking: Boolean, bookingError: String?, onConfirm: () -> Unit
) {
    val formattedDate = remember(date) {
        try { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!) }
        catch (_: Exception) { date }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Confirm Booking", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text("Review before booking", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        Surface(shape = RoundedCornerShape(14.dp), color = CardBg) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("👨‍⚕️", fontSize = 20.sp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dr. ${doctor?.user?.firstName} ${doctor?.user?.lastName}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(doctor?.specialties?.joinToString(", ") { it.specialty.name } ?: "", color = TextMuted, fontSize = 12.sp)
                    }
                    Text("$${doctor?.consultationFee?.toInt() ?: 0}", color = ActiveGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DividerColor); Spacer(Modifier.height(12.dp))
                Row { Text("📅", fontSize = 16.sp); Spacer(Modifier.width(10.dp)); Text(formattedDate, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.height(10.dp))
                Row { Text("⏰", fontSize = 16.sp); Spacer(Modifier.width(10.dp)); Text("${timeSlot?.startTime?.take(5)} - ${timeSlot?.endTime?.take(5)}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                if (reason.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row { Text("📝", fontSize = 16.sp); Spacer(Modifier.width(10.dp)); Text(reason, color = TextSecondary, fontSize = 14.sp) }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DividerColor); Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", color = TextMuted, fontSize = 14.sp)
                    Text("$${doctor?.consultationFee?.toInt() ?: 0}.00", color = ActiveGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        bookingError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFDC2626).copy(alpha = 0.1f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(err, color = Color(0xFFDC2626), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onConfirm, enabled = !isBooking,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen)
        ) {
            if (isBooking) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("Booking...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text("Confirm & Book", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ── Success ──
@Composable
private fun BookingSuccessContent(appointment: AppointmentDetail?) {
    val formattedDate = try {
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
            .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(appointment?.appointmentDate?.split("T")?.get(0) ?: "")!!)
    } catch (_: Exception) { appointment?.appointmentDate ?: "" }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActiveGreen, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Appointment Booked!", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Confirmed successfully.", color = TextSecondary, fontSize = 14.sp)
        if (appointment != null) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = CardBg, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Doctor", color = TextMuted, fontSize = 13.sp)
                        Text("Dr. ${appointment.doctor.user.firstName} ${appointment.doctor.user.lastName}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Date", color = TextMuted, fontSize = 13.sp)
                        Text(formattedDate, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Time", color = TextMuted, fontSize = 13.sp)
                        Text(appointment.startTime, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status", color = TextMuted, fontSize = 13.sp)
                        Text(appointment.status.replace("_", " "), color = ActiveGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
