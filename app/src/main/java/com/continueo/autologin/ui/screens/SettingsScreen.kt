package com.continueo.autologin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.continueo.autologin.security.CredentialManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditCredentials: () -> Unit,
    onCredentialsCleared: () -> Unit
) {
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager(context) }
    var showClearDialog by remember { mutableStateOf(false) }

    val savedCredentials = remember { credentialManager.loadCredentials() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Credentials",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Edit Credentials") },
                supportingContent = {
                    Text(
                        savedCredentials?.maskedSummary()
                            ?: "Update your saved login information"
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onNavigateToEditCredentials() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = {
                    Text(
                        "Clear Saved Credentials",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                supportingContent = {
                    Text("Permanently remove all credentials from this device")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { showClearDialog = true }
            )

            HorizontalDivider()

            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("ContinueO Auto Login") },
                supportingContent = { Text("Version 1.0.0") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            HorizontalDivider()

            Text(
                text = "Privacy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Local-Only Security") },
                supportingContent = {
                    Text(
                        "ContinueO Auto Login stores your login information locally on this device using Android Keystore and EncryptedSharedPreferences. " +
                        "The application does not operate a backend server and does not intentionally send your credentials to any third-party service."
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Remove Credentials?") },
            text = { Text("Remove your saved ContinueO credentials from this device? You will need to enter them again before logging in.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        credentialManager.deleteCredentials()
                        showClearDialog = false
                        onCredentialsCleared()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
