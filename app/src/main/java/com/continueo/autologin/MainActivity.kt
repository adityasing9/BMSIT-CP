package com.continueo.autologin

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContineoAutoLoginTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val credentialManager = remember { CredentialManager(context) }
    val hasCredentials = remember { credentialManager.hasCredentials() }

    var editingAccount by remember { mutableStateOf<AccountProfile?>(null) }

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
                }
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
                }
            )
        }
    }
}
