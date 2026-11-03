package com.docscan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.docscan.util.AppPinSecurity
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.docscan.ui.navigation.AppNavGraph
import com.docscan.ui.theme.MyApplicationTheme
import com.docscan.ui.viewmodel.ScannerViewModel
import com.docscan.util.ExternalFileRouter
import com.docscan.util.NotificationHelper
import com.docscan.util.ThemeManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels()
    private val pendingShortcutDocumentId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            org.opencv.android.OpenCVLoader.initLocal()
        } catch (_: Throwable) {
            try {
                org.opencv.android.OpenCVLoader.initDebug()
            } catch (_: Throwable) {}
        }
        try {
            ThemeManager.init(this)
        } catch (_: Throwable) {}

        try {
            NotificationHelper.initChannel(this)
        } catch (_: Throwable) {}

        try {
            handleIncomingIntent(intent)
        } catch (_: Throwable) {}

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
            val isDark = ThemeManager.isDarkThemeActive(themeMode)
            // Keep legacy custom screen palettes in sync with the selected app theme.
            SideEffect { ThemeManager.updateEffectiveDarkTheme(isDark) }

            // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Dynamically adjust status and navigation bars for clear text & icon contrast
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            MyApplicationTheme(darkTheme = isDark) {
                val navController = rememberNavController()
                val shortcutDocumentId by pendingShortcutDocumentId.collectAsStateWithLifecycle()

                LaunchedEffect(shortcutDocumentId) {
                    val docId = shortcutDocumentId ?: return@LaunchedEffect
                    navController.navigate("detail/$docId") {
                        launchSingleTop = true
                    }
                    pendingShortcutDocumentId.value = null
                }

                var unlocked by remember { mutableStateOf(!AppPinSecurity.isEnabled(this@MainActivity)) }
                if (unlocked) {
                    AppNavGraph(navController = navController, viewModel = viewModel)
                } else {
                    AppPinUnlockScreen { pin ->
                        AppPinSecurity.verify(this@MainActivity, pin).also { valid ->
                            if (valid) unlocked = true
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return

        val shortcutDocId = intent.getLongExtra(EXTRA_OPEN_DOCUMENT_ID, -1L)
        if (shortcutDocId > 0L) {
            pendingShortcutDocumentId.value = shortcutDocId
            return
        }

        lifecycleScope.launch {
            ExternalFileRouter.processIntent(this@MainActivity, intent)
        }
    }

    companion object {
        const val EXTRA_OPEN_DOCUMENT_ID = "open_document_id"
    }
}




@androidx.compose.runtime.Composable
private fun AppPinUnlockScreen(checkPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("Docscanner locked", style = MaterialTheme.typography.headlineSmall)
                Text("Enter your 4-digit app PIN", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all(Char::isDigit)) {
                            pin = value
                            error = false
                        }
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error) Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
                Button(enabled = pin.length == 4, onClick = {
                    if (!checkPin(pin)) {
                        pin = ""
                        error = true
                    }
                }) { Text("Unlock") }
            }
        }
    }
}
