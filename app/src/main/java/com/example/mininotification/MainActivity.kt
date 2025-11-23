package com.example.mininotification

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mininotification.ui.theme.MiniNotificationTheme

class MainActivity : ComponentActivity() {
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { // Handle the result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 检查并请求通知监听权限
        requestNotificationListenerPermission()
        
        setContent {
            MiniNotificationTheme {
                val navController = rememberNavController()
                
                // 使用 MyApplication 中的统一 SettingsViewModel
                val settingsViewModel = (application as MyApplication).settingsViewModel!!

                NavHost(
                    navController = navController, 
                    startDestination = "main",
                    enterTransition = {
                        slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it })
                    },
                    exitTransition = {
                        slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it / 3 })
                    },
                    popEnterTransition = {
                        slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it / 3 })
                    },
                    popExitTransition = {
                        slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it })
                    }
                ) {
                    composable("main") {
                        MainScreen(
                            viewModel = settingsViewModel,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToManagedApps = { navController.navigate("managedApps") },
                            onNavigateToEnableTime = { navController.navigate("enableTime") }
                        )
                    }
                    composable("managedApps") {
                        ManagedAppsScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("enableTime") {
                        EnableTimeScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
    
    private fun requestNotificationListenerPermission() {
        // 检查是否已经授予权限
        if (!isNotificationServiceEnabled()) {
            // 跳转到通知监听权限设置页面
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            notificationPermissionLauncher.launch(intent)
        }
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val componentName = android.content.ComponentName.unflattenFromString(name)
                if (componentName != null && componentName.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }
}
