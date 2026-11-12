package com.continueo.autologin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.security.CredentialManager
import com.continueo.autologin.utils.UpdateInfo
import com.continueo.autologin.utils.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToEditAccount: (AccountProfile) -> Unit,
    onCredentialsCleared: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager(context) }

    var accounts by remember { mutableStateOf(credentialManager.getAllAccounts()) }
    var activeAccount by remember { mutableStateOf(credentialManager.getActiveAccount()) }

    var showClearAllDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountProfile?>(null) }

    // Auto-update states
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateDialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    fun refreshAccounts() {
        accounts = credentialManager.getAllAccounts()
        activeAccount = credentialManager.getActiveAccount()
    }

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
            // Accounts Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Accounts (${accounts.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onNavigateToAddAccount,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Account")
                }
            }

            // List Accounts
            if (accounts.isEmpty()) {
                ListItem(
                    headlineContent = { Text("No accounts saved") },
                    supportingContent = { Text("Tap Add Account to set up login credentials") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier.clickable { onNavigateToAddAccount() }
                )
            } else {
                accounts.forEach { account ->
                    val isActive = account.id == activeAccount?.id
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = account.getDisplayName(),
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        },
                        supportingContent = {
                            Text(account.credentials.maskedSummary())
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isActive) {
                                    IconButton(
                                        onClick = {
                                            credentialManager.setActiveAccountId(account.id)
                                            refreshAccounts()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Set Active",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                IconButton(onClick = { onNavigateToEditAccount(account) }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Account",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { accountToDelete = account }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Account",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            // Clear All Accounts
            if (accounts.isNotEmpty()) {
                ListItem(
                    headlineContent = {
                        Text(
                            "Clear All Accounts",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = {
                        Text("Permanently remove all credentials and accounts from this device")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { showClearAllDialog = true }
                )
                HorizontalDivider()
            }

            // Updates Section
            Text(
                text = "Updates",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Check for Updates") },
                supportingContent = {
                    Text("Current version: v${UpdateManager.getCurrentVersionName(context)}")
                },
                leadingContent = {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                trailingContent = {
                    TextButton(
                        onClick = {
                            if (!isCheckingUpdate) {
                                isCheckingUpdate = true
                                coroutineScope.launch {
                                    val result = UpdateManager.checkForUpdates(context)
                                    isCheckingUpdate = false
                                    result.onSuccess { info ->
                                        if (info.isUpdateAvailable) {
                                            updateDialogInfo = info
                                        } else {
                                            Toast.makeText(context, "You're on the latest version! (v${info.latestVersionName})", Toast.LENGTH_SHORT).show()
                                        }
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Update check failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text(if (isCheckingUpdate) "Checking..." else "Check Now")
                    }
                }
            )

            HorizontalDivider()

            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("ContinueO Auto Login") },
                supportingContent = { Text("Version ${UpdateManager.getCurrentVersionName(context)}") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            HorizontalDivider()

            // Privacy Section
            Text(
                text = "Privacy & Security",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Local-Only Encrypted Storage") },
                supportingContent = {
                    Text(
                        "ContinueO Auto Login stores your credentials locally on this device using Android Keystore and AES-256 EncryptedSharedPreferences. " +
                        "The application does not operate any backend server and never sends your credentials to any third-party service."
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

    // Delete Single Account Confirmation Dialog
    accountToDelete?.let { targetAccount ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Account?") },
            text = {
                Text("Are you sure you want to remove \"${targetAccount.getDisplayName()}\" (${targetAccount.credentials.maskedSummary()}) from this device?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        credentialManager.deleteAccount(targetAccount.id)
                        accountToDelete = null
                        refreshAccounts()
                        if (credentialManager.getAllAccounts().isEmpty()) {
                            onCredentialsCleared()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Accounts Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Accounts?") },
            text = {
                Text("This will permanently remove all saved accounts and login credentials from this device. You will need to enter them again before logging in.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        credentialManager.deleteCredentials()
                        showClearAllDialog = false
                        onCredentialsCleared()
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Update Dialog
    updateDialogInfo?.let { update ->
        AlertDialog(
            onDismissRequest = { updateDialogInfo = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update Available")
                }
            },
            text = {
                Column {
                    Text(
                        text = "A new version of ContinueO Auto Login is available:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "New Version: v${update.latestVersionName}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (update.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        UpdateManager.openBrowserDownload(context, update.downloadUrl)
                        updateDialogInfo = null
                    }
                ) {
                    Text("Download Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogInfo = null }) {
                    Text("Later")
                }
            }
        )
    }
}
