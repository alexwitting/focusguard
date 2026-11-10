package com.focusguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusguard.core.system.worker.MidnightResetWorker
import com.focusguard.core.system.worker.UsagePollerWorker
import com.focusguard.core.ui.theme.FocusGuardTheme
import com.focusguard.feature.block.BlockScreen
import com.focusguard.feature.home.HomeScreen
import com.focusguard.feature.onboarding.OnboardingScreen
import com.focusguard.feature.settings.SettingsScreen
import com.focusguard.navigation.BlockRoute
import com.focusguard.navigation.FocusGuardNavigationBar
import com.focusguard.navigation.HomeRoute
import com.focusguard.navigation.OnboardingRoute
import com.focusguard.navigation.SettingsRoute
import com.focusguard.navigation.TOP_LEVEL_DESTINATIONS
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MidnightResetWorker.scheduleIfNeeded(applicationContext)
        UsagePollerWorker.schedule(applicationContext)
        enableEdgeToEdge()
        setContent {
            FocusGuardTheme {
                val mainViewModel: MainViewModel = hiltViewModel()
                val startDestination by mainViewModel.startDestination.collectAsStateWithLifecycle()

                val dest = startDestination
                if (dest == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@FocusGuardTheme
                }

                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                val showBottomBar =
                    currentDestination?.let { d ->
                        TOP_LEVEL_DESTINATIONS.any { td ->
                            when (td.route) {
                                HomeRoute -> d.hasRoute<HomeRoute>()
                                BlockRoute -> d.hasRoute<BlockRoute>()
                                SettingsRoute -> d.hasRoute<SettingsRoute>()
                                else -> false
                            }
                        }
                    } ?: false

                // Android 13+ drops every notification (time-limit warnings, the "blocking is
                // off" alert) until POST_NOTIFICATIONS is granted at runtime — declaring it in the
                // manifest isn't enough. Ask once the user reaches the main tabs, i.e. after
                // onboarding, rather than stacking it onto the permission steps there.
                val notificationPermission =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(showBottomBar) {
                    if (showBottomBar && needsNotificationPermission()) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // Zero content insets: each screen's TopAppBar handles the status bar.
                    // innerPadding carries only the NavigationBar height.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showBottomBar) {
                            val selectedRoute =
                                TOP_LEVEL_DESTINATIONS
                                    .firstOrNull { td ->
                                        when (td.route) {
                                            HomeRoute -> currentDestination.hasRoute<HomeRoute>() == true
                                            BlockRoute -> currentDestination.hasRoute<BlockRoute>() == true
                                            SettingsRoute -> currentDestination.hasRoute<SettingsRoute>() == true
                                            else -> false
                                        }
                                    }?.route
                            FocusGuardNavigationBar(
                                selectedRoute = selectedRoute,
                                onSelect = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = dest,
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = { fadeIn(animationSpec = tween(220)) },
                        exitTransition = { fadeOut(animationSpec = tween(220)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                        popExitTransition = { fadeOut(animationSpec = tween(220)) },
                    ) {
                        composable<OnboardingRoute> {
                            OnboardingScreen(
                                onComplete = {
                                    navController.navigate(HomeRoute) {
                                        popUpTo<OnboardingRoute> { inclusive = true }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        composable<HomeRoute> {
                            HomeScreen(
                                modifier = Modifier.fillMaxSize(),
                                onOpenSettings = {
                                    navController.navigate(SettingsRoute) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                        composable<BlockRoute> {
                            BlockScreen(modifier = Modifier.fillMaxSize())
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
