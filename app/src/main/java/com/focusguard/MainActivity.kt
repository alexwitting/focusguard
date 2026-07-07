package com.focusguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // Zero content insets: each screen's TopAppBar handles the status bar.
                    // innerPadding carries only the NavigationBar height.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                TOP_LEVEL_DESTINATIONS.forEach { navDest ->
                                    val selected =
                                        when (navDest.route) {
                                            HomeRoute -> currentDestination.hasRoute<HomeRoute>() == true
                                            BlockRoute -> currentDestination.hasRoute<BlockRoute>() == true
                                            SettingsRoute -> currentDestination.hasRoute<SettingsRoute>() == true
                                            else -> false
                                        }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(navDest.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(navDest.icon, contentDescription = navDest.label) },
                                        label = { Text(navDest.label) },
                                    )
                                }
                            }
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
                            HomeScreen(modifier = Modifier.fillMaxSize())
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
}
