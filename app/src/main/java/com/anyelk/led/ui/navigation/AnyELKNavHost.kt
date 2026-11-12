package com.anyelk.led.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anyelk.led.R
import com.anyelk.led.ui.screens.control.ControlScreen
import com.anyelk.led.ui.screens.devices.DevicesScreen
import com.anyelk.led.ui.screens.music.MusicScreen
import com.anyelk.led.ui.screens.settings.SettingsScreen

sealed class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Control : Destination(
        "control", R.string.nav_control,
        Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb
    )

    data object Devices : Destination(
        "devices", R.string.nav_devices,
        Icons.Filled.Bluetooth, Icons.Outlined.Bluetooth
    )

    data object Music : Destination(
        "music", R.string.nav_music,
        Icons.Filled.MusicNote, Icons.Outlined.MusicNote
    )

    data object Settings : Destination(
        "settings", R.string.nav_settings,
        Icons.Filled.Settings, Icons.Outlined.Settings
    )

    companion object {
        val all = listOf(Control, Devices, Music, Settings)
    }
}

@Composable
fun AnyELKNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.all.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon
                                else destination.unselectedIcon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Control.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Control.route) { ControlScreen() }
            composable(Destination.Devices.route) { DevicesScreen() }
            composable(Destination.Music.route) { MusicScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
