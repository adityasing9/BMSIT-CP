package com.continueo.autologin.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.continueo.autologin.automation.AutomationState
import com.continueo.autologin.automation.StatusMessage
import com.continueo.autologin.ui.viewmodels.LoginViewModel
import com.continueo.autologin.utils.NetworkUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    loginViewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val automationState by loginViewModel.automationState.collectAsState()
    val statusMessages by loginViewModel.statusMessages.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showStatusPanel by remember { mutableStateOf(true) }
    var hasStarted by remember { mutableStateOf(false) }
    var showNoInternet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll status list when new items are added
    LaunchedEffect(statusMessages.size) {
        if (statusMessages.isNotEmpty()) {
            listState.animateScrollToItem(statusMessages.size - 1)
        }
    }

    // Handle back button in WebView
    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onNavigateBack()
        }
    }

    // Check network and start automation
    LaunchedEffect(Unit) {
        if (!hasStarted) {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                showNoInternet = true
            }
            hasStarted = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ContinueO Login") },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webViewRef
                        if (wv != null && wv.canGoBack()) {
                            wv.goBack()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // No internet message
            if (showNoInternet) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No internet connection.\nContinueO requires an internet connection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            if (NetworkUtils.isNetworkAvailable(context)) {
                                showNoInternet = false
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                return@Column
            }

            // Status panel
            AnimatedVisibility(
                visible = showStatusPanel && statusMessages.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(statusMessages) { message ->
                                StatusMessageItem(message)
                            }
                        }

                        // Action buttons on error or manual intervention
                        if (automationState == AutomationState.ERROR ||
                            automationState == AutomationState.MANUAL_INTERVENTION_REQUIRED
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        webViewRef?.let { loginViewModel.retry(it) }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry")
                                }
                                FilledTonalButton(
                                    onClick = { showStatusPanel = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Continue Manually")
                                }
                            }
                        }

                        // Success state — option to hide panel
                        if (automationState == AutomationState.SUCCESS) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showStatusPanel = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            // WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewRef = this
                        if (!showNoInternet) {
                            loginViewModel.startLogin(this)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun StatusMessageItem(message: StatusMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (message.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (message.isSuccess) "Success" else "Warning",
            tint = if (message.isSuccess) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (message.isSuccess) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            fontWeight = if (!message.isSuccess) FontWeight.Medium else FontWeight.Normal
        )
    }
}
