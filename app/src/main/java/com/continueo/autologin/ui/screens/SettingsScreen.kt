package com.continueo.autologin.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.continueo.autologin.R
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.security.CredentialManager
import com.continueo.autologin.ui.theme.AppThemeMode
import com.continueo.autologin.ui.theme.ColorAccent
import com.continueo.autologin.ui.theme.ThemeManager
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
    onCredentialsCleared: () -> Unit,
    themeManager: ThemeManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager(context) }

    var accounts by remember { mutableStateOf(credentialManager.getAllAccounts()) }
    var activeAccount by remember { mutableStateOf(credentialManager.getActiveAccount()) }

    var showClearAllDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountProfile?>(null) }

    // Theme states
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeSettings by themeManager.themeSettings.collectAsState()

    // Export & Import states
    val clipboardManager = LocalClipboardManager.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

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

            // Appearance & Theme Section
            Text(
                text = "Appearance & Theme",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            // Theme Mode
            ListItem(
                headlineContent = { Text("Theme Mode") },
                supportingContent = {
                    Text(themeSettings.themeMode.title)
                },
                leadingContent = {
                    Icon(
                        imageVector = when (themeSettings.themeMode) {
                            AppThemeMode.LIGHT -> Icons.Default.LightMode
                            AppThemeMode.DARK -> Icons.Default.DarkMode
                            AppThemeMode.SYSTEM -> Icons.Default.BrightnessMedium
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    TextButton(onClick = { showThemeDialog = true }) {
                        Text("Change")
                    }
                },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            HorizontalDivider()

            // Color Accent Selection (5 vibrant chips)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Color Accent",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (themeSettings.dynamicColor) "Material You (Wallpaper)" else themeSettings.colorAccent.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Color accent chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorAccent.values().forEach { accent ->
                        val isSelected = !themeSettings.dynamicColor && themeSettings.colorAccent == accent
                        Surface(
                            modifier = Modifier
                                .size(46.dp)
                                .clickable {
                                    themeManager.setColorAccent(accent)
                                },
                            shape = CircleShape,
                            color = Color(accent.primaryColor),
                            border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                            shadowElevation = if (isSelected) 4.dp else 1.dp
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Dynamic Colors (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Material You (Dynamic Colors)") },
                    supportingContent = { Text("Extract theme colors from device wallpaper") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = themeSettings.dynamicColor,
                            onCheckedChange = { themeManager.setDynamicColor(it) }
                        )
                    }
                )
                HorizontalDivider()
            }

            // AMOLED Pure Black
            ListItem(
                headlineContent = { Text("AMOLED Pure Black") },
                supportingContent = { Text("Pitch-black background in dark mode to save battery on OLED screens") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Contrast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Switch(
                        checked = themeSettings.amoledDark,
                        onCheckedChange = { themeManager.setAmoledDark(it) }
                    )
                }
            )

            HorizontalDivider()

            // Data Management Section
            Text(
                text = "Data Management (Backup & Restore)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Export Accounts Backup") },
                supportingContent = { Text("Export all saved accounts to JSON for backup or device transfer") },
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_open_in_browser),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                modifier = Modifier.clickable {
                    showExportDialog = true
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Import Accounts Backup") },
                supportingContent = { Text("Restore accounts from a previously exported JSON backup") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable {
                    importInputText = ""
                    importErrorMessage = null
                    showImportDialog = true
                }
            )

            HorizontalDivider()

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

            ListItem(
                headlineContent = { Text("GitHub Releases") },
                supportingContent = { Text("View changelogs, assets, and download APKs from GitHub") },
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_open_in_browser),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    TextButton(
                        onClick = {
                            UpdateManager.openInBrowser(context, UpdateManager.RELEASES_PAGE_URL)
                        }
                    ) {
                        Text("Releases ↗")
                    }
                },
                modifier = Modifier.clickable {
                    UpdateManager.openInBrowser(context, UpdateManager.RELEASES_PAGE_URL)
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

                    Spacer(modifier = Modifier.height(12.dp))
                    // Direct GitHub Releases Link Card
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val targetUrl = update.htmlUrl.ifBlank { UpdateManager.RELEASES_PAGE_URL }
                                UpdateManager.openInBrowser(context, targetUrl)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_open_in_browser),
                                contentDescription = "GitHub Releases",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub Release & Downloads",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = update.htmlUrl.ifBlank { UpdateManager.RELEASES_PAGE_URL },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            val targetUrl = update.htmlUrl.ifBlank { UpdateManager.RELEASES_PAGE_URL }
                            UpdateManager.openInBrowser(context, targetUrl)
                            updateDialogInfo = null
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_open_in_browser),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GitHub")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val id = UpdateManager.startOtaDownload(context, update.downloadUrl, update.latestVersionName)
                            if (id != -1L) {
                                Toast.makeText(context, "OTA download started! Check notification bar for progress.", Toast.LENGTH_LONG).show()
                            } else {
                                UpdateManager.openBrowserDownload(context, update.downloadUrl)
                            }
                            updateDialogInfo = null
                        }
                    ) {
                        Text("Update (OTA)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogInfo = null }) {
                    Text("Later")
                }
            }
        )
    }

    // Export Accounts Dialog
    if (showExportDialog) {
        val exportJson = remember(accounts) { credentialManager.exportBackupJson() }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text("Export Accounts Backup")
            },
            text = {
                Column {
                    Text(
                        text = "Backup of ${accounts.size} account(s). You can copy the JSON data to your clipboard or share it to save in Drive, Notes, or WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(exportJson))
                        Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, exportJson)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Accounts Backup")
                                context.startActivity(shareIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to open share sheet", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Share / Save")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Import Accounts Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                importInputText = ""
                importErrorMessage = null
            },
            title = {
                Text("Import Accounts Backup")
            },
            text = {
                Column {
                    Text(
                        text = "Paste your exported JSON backup data below to restore your accounts:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = {
                            importInputText = it
                            importErrorMessage = null
                        },
                        placeholder = { Text("Paste JSON here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = importErrorMessage != null
                    )
                    if (importErrorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = importErrorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val clip = clipboardManager.getText()
                            if (clip != null && clip.text.isNotBlank()) {
                                importInputText = clip.text
                                importErrorMessage = null
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Paste from Clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = credentialManager.importBackupJson(importInputText)
                        result.onSuccess { count ->
                            refreshAccounts()
                            Toast.makeText(context, "Successfully imported $count account(s)!", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                            importInputText = ""
                            importErrorMessage = null
                        }.onFailure { err ->
                            importErrorMessage = err.message ?: "Failed to import backup"
                        }
                    },
                    enabled = importInputText.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importInputText = ""
                        importErrorMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Theme Mode Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Theme Mode")
                }
            },
            text = {
                Column {
                    AppThemeMode.values().forEach { mode ->
                        val isSelected = themeSettings.themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeManager.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    themeManager.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = when (mode) {
                                        AppThemeMode.SYSTEM -> "Matches your Android system display setting"
                                        AppThemeMode.LIGHT -> "Always light appearance"
                                        AppThemeMode.DARK -> "Always dark appearance"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
