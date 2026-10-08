package com.continueo.autologin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    val startDestination = if (hasCredentials) Screen.Home.route else Screen.Setup.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Setup.route) {
            val existing = remember { credentialManager.loadCredentials() }
            SetupScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                existingCredentials = existing
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
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
                onNavigateToEditCredentials = {
                    navController.navigate(Screen.Setup.route)
                },
                onCredentialsCleared = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
