package com.continueo.autologin.ui.navigation

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Home : Screen("home")
    object Login : Screen("login")
    object Settings : Screen("settings")
}
