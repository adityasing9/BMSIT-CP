package com.continueo.autologin

import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import com.continueo.autologin.widget.AttendanceWidgetProvider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.continueo.autologin.models.AccountProfile
import com.continueo.autologin.security.CredentialManager
import com.continueo.autologin.ui.navigation.Screen
import com.continueo.autologin.ui.screens.HomeScreen
import com.continueo.autologin.ui.screens.LoginScreen
import com.continueo.autologin.ui.screens.SettingsScreen
import com.continueo.autologin.ui.screens.SetupScreen
import com.continueo.autologin.ui.theme.ContineoAutoLoginTheme
import com.continueo.autologin.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    private var widgetLoginRequestId by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)
        val themeManager = ThemeManager(applicationContext)
        setContent {
            val themeSettings by themeManager.themeSettings.collectAsState()
            ContineoAutoLoginTheme(themeSettings = themeSettings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        themeManager = themeManager,
                        widgetLoginRequestId = widgetLoginRequestId
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.getStringExtra(AttendanceWidgetProvider.EXTRA_WIDGET_ACTION) ?: intent.action
        if (action == AttendanceWidgetProvider.ACTION_WIDGET_LOGIN) {
            widgetLoginRequestId++
            intent.removeExtra(AttendanceWidgetProvider.EXTRA_WIDGET_ACTION)
            intent.action = Intent.ACTION_MAIN
        }
    }
}

@Composable
fun AppNavigation(
    themeManager: ThemeManager,
    widgetLoginRequestId: Int = 0
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val credentialManager = remember { CredentialManager(context) }
    val hasCredentials = remember { credentialManager.hasCredentials() }

    var editingAccount by remember { mutableStateOf<AccountProfile?>(null) }

    LaunchedEffect(widgetLoginRequestId) {
        if (widgetLoginRequestId > 0 && credentialManager.hasCredentials()) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
            }
        }
    }

    val startDestination = if (hasCredentials) Screen.Home.route else Screen.Setup.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Setup.route) {
            val allAccounts = credentialManager.getAllAccounts()
            val canGoBack = allAccounts.isNotEmpty()

            SetupScreen(
                onNavigateToHome = {
                    editingAccount = null
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                onNavigateBack = if (canGoBack) {
                    {
                        editingAccount = null
                        navController.popBackStack()
                    }
                } else null,
                accountToEdit = editingAccount
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAddAccount = {
                    editingAccount = null
                    navController.navigate(Screen.Setup.route)
                },
                themeManager = themeManager
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddAccount = {
                    editingAccount = null
                    navController.navigate(Screen.Setup.route)
                },
                onNavigateToEditAccount = { account ->
                    editingAccount = account
                    navController.navigate(Screen.Setup.route)
                },
                onCredentialsCleared = {
                    editingAccount = null
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                themeManager = themeManager
            )
        }
    }
}
