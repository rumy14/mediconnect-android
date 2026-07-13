package com.mediconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.model.LoginRequest
import com.mediconnect.data.session.SessionManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mediconnect.BuildConfig
import com.mediconnect.navigation.Screen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom colors for the dark teal theme
private val TealStart = Color(0xFF0D4F4F)       // Dark teal top
private val TealEnd = Color(0xFF0A1628)          // Deep navy bottom
private val CardBg = Color(0xFF0F1E33)           // Card background
private val InputBg = Color(0xFF162A45)           // Input field background
private val AccentTeal = Color(0xFF1EB9B9)        // Button / accent teal
private val GoldenOutline = Color(0xFFC8A45C)     // Golden-brown outline
private val PillBg = Color(0xFF1A3A5C)            // Light blue pill bg
private val White85 = Color.White.copy(alpha = 0.85f)
private val White60 = Color.White.copy(alpha = 0.60f)
private val White30 = Color.White.copy(alpha = 0.30f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { MediConnectApi.getInstance() }
    val session = remember { SessionManager.getInstance(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Check if biometric hardware is available
    val biometricAvailable = remember {
        try {
            val bm = BiometricManager.from(context)
            @Suppress("DEPRECATION")
            bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    // ── Check for app update ──
    var latestVersionInfo by remember { mutableStateOf<com.mediconnect.data.api.MediConnectApi.LatestVersionInfo?>(null) }
    var updateChecked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val info = api.checkLatestVersion()
        if (info != null) {
            latestVersionInfo = info
        }
        updateChecked = true
    }

    val hasUpdate = remember(latestVersionInfo) {
        latestVersionInfo?.let { info ->
            try {
                val current = BuildConfig.VERSION_NAME.split(".").map { it.toInt() }
                val latest = info.latestVersion.split(".").map { it.toInt() }
                // Compare version tuples
                info.latestVersionCode > BuildConfig.VERSION_CODE
            } catch (_: Exception) { false }
        } ?: false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(TealStart, TealEnd)
                )
            )
    ) {
        // ── Update banner (pinned to top) ──
        if (hasUpdate && latestVersionInfo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Color(0xFFC8A45C).copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⬆", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "v${latestVersionInfo!!.latestVersion} available",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0A1628)
                        )
                        latestVersionInfo!!.releaseNotes?.let {
                            Text(it, fontSize = 10.sp, color = Color(0xFF0A1628).copy(alpha = 0.7f), maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(latestVersionInfo!!.downloadUrl))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, Color(0xFF0A1628))
                    ) {
                        Text("Update", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0A1628))
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top: Header area (leave room for update banner overlay) ──
            Spacer(modifier = Modifier.height(if (hasUpdate) 100.dp else 48.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("MediConnect", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("Your Healthcare Connection", fontSize = 13.sp, color = White60)

            // ── Center: Card (expand to fill space) ──
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // User icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(White30),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = White60, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Sign In to Your Account", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Error message
                        errorMsg?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }

                        // Email field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMsg = null },
                            label = { Text("Email", color = White60) },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = White60) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentTeal,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = AccentTeal,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = InputBg,
                                unfocusedContainerColor = InputBg
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Password field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMsg = null },
                            label = { Text("Password", color = White60) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = White60) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentTeal,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = AccentTeal,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = InputBg,
                                unfocusedContainerColor = InputBg
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Sign In button
                        Button(
                            onClick = {
                                errorMsg = null
                                when {
                                    email.isBlank() -> errorMsg = "Email is required"
                                    !email.contains("@") -> errorMsg = "Enter a valid email address"
                                    password.isBlank() -> errorMsg = "Password is required"
                                    password.length < 6 -> errorMsg = "Password must be at least 6 characters"
                                    else -> {
                                        loading = true
                                        scope.launch {
                                            try {
                                                val response = api.login(LoginRequest(email = email.trim(), password = password))
                                                if (response.success && response.data != null) {
                                                    api.setToken(response.data.token)
                                                    session.saveSession(response.data.token, response.data.user)
                                                    navController.navigate(Screen.Home.route) {
                                                        popUpTo(Screen.Login.route) { inclusive = true }
                                                    }
                                                } else {
                                                    errorMsg = response.error ?: response.message ?: "Invalid email or password"
                                                    loading = false
                                                }
                                            } catch (e: Exception) {
                                                val msg = e.message ?: ""
                                                errorMsg = when {
                                                    msg.contains("401") -> "Invalid email or password"
                                                    msg.contains("timeout") -> "Connection timed out"
                                                    msg.contains("resolve") || msg.contains("connect") -> "No internet connection"
                                                    else -> "Something went wrong"
                                                }
                                                loading = false
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }

                        // Biometric login
                        if (biometricAvailable) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val activity = context as? FragmentActivity ?: return@OutlinedButton
                                    val prompt = BiometricPrompt(
                                        activity,
                                        ContextCompat.getMainExecutor(context),
                                        object : BiometricPrompt.AuthenticationCallback() {
                                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                scope.launch {
                                                    try {
                                                        val token = session.getToken()
                                                        if (!token.isNullOrBlank()) {
                                                            api.setToken(token)
                                                            navController.navigate(Screen.Home.route) {
                                                                popUpTo(Screen.Login.route) { inclusive = true }
                                                            }
                                                        } else errorMsg = "No saved session"
                                                    } catch (_: Exception) { errorMsg = "Authentication failed" }
                                                }
                                            }
                                            override fun onAuthenticationError(errCode: Int, errString: CharSequence) {
                                                if (errCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) errorMsg = errString.toString()
                                            }
                                        }
                                    )
                                    prompt.authenticate(
                                        BiometricPrompt.PromptInfo.Builder()
                                            .setTitle("MediConnect")
                                            .setSubtitle("Use fingerprint to sign in")
                                            .setNegativeButtonText("Cancel")
                                            .build()
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Biometric Sign In", fontSize = 14.sp)
                            }
                        }

                        // Register link
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Don't have an account? ", fontSize = 13.sp, color = White60)
                            TextButton(
                                onClick = { navController.navigate(Screen.Register.route) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Register", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentTeal)
                            }
                        }
                    }
                }
            }

            // ── Bottom: Version footer ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(bottom = 48.dp)
            ) {
                Text(
                    text = "MediConnect v${BuildConfig.VERSION_NAME}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = White30,
                    letterSpacing = 2.sp
                )
                val buildDate = remember {
                    try {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(BuildConfig.BUILD_EPOCH))
                    } catch (_: Exception) { "" }
                }
                Text(
                    text = buildDate,
                    fontSize = 9.sp,
                    color = White30.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
            }
        }
    }


}
