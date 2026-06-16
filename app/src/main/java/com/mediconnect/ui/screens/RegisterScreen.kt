package com.mediconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import com.mediconnect.data.model.RegisterRequest
import com.mediconnect.data.session.SessionManager
import com.mediconnect.navigation.Screen
import kotlinx.coroutines.launch

// Match Login screen colors
private val TealStart = Color(0xFF0D4F4F)
private val TealEnd = Color(0xFF0A1628)
private val CardBg = Color(0xFF0F1E33)
private val InputBg = Color(0xFF162A45)
private val AccentTeal = Color(0xFF1EB9B9)
private val White60 = Color.White.copy(alpha = 0.60f)
private val White30 = Color.White.copy(alpha = 0.30f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { MediConnectApi.getInstance() }
    val session = remember { SessionManager.getInstance(context) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(TealStart, TealEnd))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Back arrow
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Header: Cross icon + brand
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
                    Text(
                        text = "+",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "MediConnect",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = "Create Your Account",
                fontSize = 13.sp,
                color = White60,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Card container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error message
                    errorMsg?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // First Name
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it; errorMsg = null },
                        label = { Text("First Name", color = White60) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = White60) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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

                    // Last Name
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it; errorMsg = null },
                        label = { Text("Last Name", color = White60) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = White60) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMsg = null },
                        label = { Text("Email", color = White60) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = White60) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
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

                    // Phone
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; errorMsg = null },
                        label = { Text("Phone Number", color = White60) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = White60) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
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

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        label = { Text("Password", color = White60) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = White60) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
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

                    // Create Account button
                    Button(
                        onClick = {
                            when {
                                firstName.isBlank() -> errorMsg = "First name is required"
                                lastName.isBlank() -> errorMsg = "Last name is required"
                                email.isBlank() || !email.contains("@") -> errorMsg = "Valid email is required"
                                phone.isBlank() || phone.length < 10 -> errorMsg = "Phone must be at least 10 digits"
                                password.length < 8 -> errorMsg = "Password must be at least 8 characters"
                                else -> {
                                    errorMsg = null
                                    loading = true
                                    scope.launch {
                                        try {
                                            val response = api.register(
                                                RegisterRequest(
                                                    email = email.trim(),
                                                    password = password,
                                                    firstName = firstName.trim(),
                                                    lastName = lastName.trim(),
                                                    phone = phone.trim()
                                                )
                                            )
                                            if (response.success && response.data != null) {
                                                api.setToken(response.data.token)
                                                session.saveSession(response.data.token, response.data.user)
                                                navController.navigate(Screen.Home.route) {
                                                    popUpTo(Screen.Register.route) { inclusive = true }
                                                }
                                            } else {
                                                errorMsg = response.error ?: response.message ?: "Something went wrong"
                                                loading = false
                                            }
                                        } catch (e: Exception) {
                                            val msg = e.message ?: ""
                                            errorMsg = when {
                                                msg.contains("401") -> "Session expired. Please restart."
                                                msg.contains("409") -> "An account with this email already exists."
                                                msg.contains("timeout") -> "Request timed out. Check your connection."
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign In link
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Already have an account? ", fontSize = 13.sp, color = White60)
                        TextButton(
                            onClick = { navController.popBackStack() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Sign In", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentTeal)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
