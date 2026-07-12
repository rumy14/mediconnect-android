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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mediconnect.data.api.MediConnectApi
import com.mediconnect.data.model.VoiceCallSummary
import com.mediconnect.navigation.Screen
import kotlinx.coroutines.launch

/**
 * Screen listing the authenticated user's past voice call history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallHistoryScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val api = remember { MediConnectApi.getInstance() }

    var calls by remember { mutableStateOf<List<VoiceCallSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        loading = currentPage == 1
        loadingMore = currentPage > 1
        try {
            val response = api.getVoiceCalls(page = currentPage, limit = 20)
            if (response.success) {
                if (currentPage == 1) {
                    calls = response.data
                } else {
                    calls = calls + response.data
                }
                totalPages = response.pagination.totalPages
            } else {
                errorMsg = "Failed to load call history"
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Network error"
        }
        loading = false
        loadingMore = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call History", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading && calls.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMsg != null && calls.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { currentPage = 1; errorMsg = null }) {
                            Text("Retry")
                        }
                    }
                }
                calls.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No call history yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Use the AI voice assistant to start a conversation", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(calls) { call ->
                            VoiceCallCard(call = call, onClick = {
                                navController.navigate(Screen.VoiceCallDetail.createRoute(call.id))
                            })
                        }

                        // Load more button
                        if (currentPage < totalPages) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        TextButton(onClick = { currentPage++ }) {
                                            Text("Load more")
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCallCard(call: VoiceCallSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = when (call.status) {
                        "COMPLETED" -> Color(0xFF22C55E)
                        "FAILED" -> MaterialTheme.colorScheme.error
                        "IN_PROGRESS" -> Color(0xFF3B82F6)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Summary or preview
                Text(
                    text = call.summary ?: call.transcriptPreview ?: "Voice call",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = call.startedAt?.let { formatCallDate(it) } ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (call.durationSeconds != null) {
                        Text(
                            text = " · ${formatDuration(call.durationSeconds)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (call.messageCount != null) {
                        Text(
                            text = " · ${call.messageCount} messages",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Format ISO date string to a readable short date/time. */
private fun formatCallDate(isoDate: String): String {
    return try {
        val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdfIn.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdfIn.parse(isoDate.replace(Regex("\\.[0-9]+Z$"), "").replace("Z", ""))
            ?: return isoDate.take(10)
        val sdfOut = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
        sdfOut.timeZone = java.util.TimeZone.getDefault()
        sdfOut.format(date)
    } catch (_: Exception) {
        isoDate.take(10)
    }
}

/** Format seconds to "Xm Ys" or "Xs". */
private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}
