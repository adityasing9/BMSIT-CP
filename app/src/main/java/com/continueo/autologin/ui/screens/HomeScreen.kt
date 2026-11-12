package com.continueo.autologin.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.security.CredentialManager
import com.continueo.autologin.utils.UpdateInfo
import com.continueo.autologin.utils.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddAccount: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager(context) }

    var accounts by remember { mutableStateOf(credentialManager.getAllAccounts()) }
    var activeAccount by remember { mutableStateOf(credentialManager.getActiveAccount()) }

    var showAccountSwitcher by remember { mutableStateOf(false) }

    // Auto-update states
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isUpdateDismissed by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Check for updates on screen launch
    LaunchedEffect(Unit) {
        val result = UpdateManager.checkForUpdates(context)
        result.onSuccess { info ->
            if (info.isUpdateAvailable) {
                updateInfo = info
            }
        }
    }

    // Refresh accounts when returning to screen
    fun refreshAccounts() {
        accounts = credentialManager.getAllAccounts()
        activeAccount = credentialManager.getActiveAccount()
    }

    LaunchedEffect(Unit) {
        refreshAccounts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // Update Notification Card (if update available and not dismissed)
        AnimatedVisibility(
            visible = updateInfo != null && !isUpdateDismissed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            updateInfo?.let { update ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Update Available",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Update Available: v${update.latestVersionName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { isUpdateDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        if (update.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = update.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isDownloadingUpdate) {
                            Spacer(modifier = Modifier.height(12.dp))
                            if (downloadProgress >= 0) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Downloading APK: $downloadProgress%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Downloading APK...",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { UpdateManager.openBrowserDownload(context, update.downloadUrl) }
                                ) {
                                    Text("Browser Link")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        isDownloadingUpdate = true
                                        coroutineScope.launch {
                                            val downloadResult = UpdateManager.downloadApk(
                                                context,
                                                update.downloadUrl
                                            ) { percent, _, _ ->
                                                downloadProgress = percent
                                            }
                                            isDownloadingUpdate = false
                                            downloadResult.onSuccess { apkFile ->
                                                downloadedApkFile = apkFile
                                                if (!UpdateManager.canRequestPackageInstalls(context)) {
                                                    showPermissionDialog = true
                                                } else {
                                                    val installResult = UpdateManager.installApk(context, apkFile)
                                                    if (installResult.isFailure) {
                                                        Toast.makeText(context, "Failed to start install: ${installResult.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }.onFailure { err ->
                                                Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Update Now")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text(
            text = "ContinueO",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Security badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Secured",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Credentials Secured On-Device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Active Account Profile Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAccountSwitcher = true },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Active Account",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeAccount?.getDisplayName() ?: "No Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (accounts.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${accounts.size} Accounts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = activeAccount?.credentials?.maskedSummary() ?: "Tap to configure",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = { showAccountSwitcher = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (accounts.size > 1) "Switch" else "Accounts")
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // LOGIN NOW Button
        ElevatedButton(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = "LOGIN NOW 🚀",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onNavigateToSettings,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings")
        }
    }

    // Account Switcher Dialog
    if (showAccountSwitcher) {
        AlertDialog(
            onDismissRequest = { showAccountSwitcher = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Account")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose which account to log in with:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    accounts.forEach { account ->
                        val isSelected = account.id == activeAccount?.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    credentialManager.setActiveAccountId(account.id)
                                    activeAccount = account
                                    showAccountSwitcher = false
                                },
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (isSelected) 4.dp else 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.getDisplayName(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = account.credentials.maskedSummary(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            showAccountSwitcher = false
                            onNavigateToAddAccount()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Another Account")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountSwitcher = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Install Permission Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Required") },
            text = {
                Text("Android requires permission to install APK updates from this app. Please enable \"Allow from this source\" in the next screen, then tap the APK to install.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        UpdateManager.openInstallPermissionSettings(context)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        downloadedApkFile?.let { UpdateManager.installApk(context, it) }
                    }
                ) {
                    Text("Try Install Anyway")
                }
            }
        )
    }
}
