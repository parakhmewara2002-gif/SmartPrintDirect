package com.smartprint.direct

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartprint.direct.ui.screens.*
import com.smartprint.direct.ui.theme.SmartPrintTheme
import com.smartprint.direct.ui.viewmodel.SmartPrintViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SmartPrintViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (needed.isNotEmpty()) {
            requestPermissionsLauncher.launch(needed.toTypedArray())
        }

        handleIntent(intent)

        setContent {
            SmartPrintTheme {
                AppNavigation(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                viewModel.printDocument(uri, "Shared Document")
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: SmartPrintViewModel) {
    val navController = rememberNavController()

    // iOS UINavigationController push/pop: incoming screen slides in from the right edge, the
    // outgoing one moves left by ~30% (parallax). No alpha animation on purpose: fades force
    // offscreen layers for the whole screen, which is what made transitions stutter.
    val iosEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f) // iOS standard curve
    val durationMs = 380
    val push = tween<IntOffset>(durationMs, easing = iosEasing)

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        enterTransition = { slideInHorizontally(animationSpec = push) { it } },
        exitTransition = { slideOutHorizontally(animationSpec = push) { -(it * 3 / 10) } },
        popEnterTransition = { slideInHorizontally(animationSpec = push) { -(it * 3 / 10) } },
        popExitTransition = { slideOutHorizontally(animationSpec = push) { it } }
    ) {
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToDocumentPrint = { navController.navigate("document_print") },
                onNavigateToPhotoPrint = { navController.navigate("photo_print") },
                onNavigateToCameraScan = { navController.navigate("camera_scan") },
                onNavigateToDirectCopy = { navController.navigate("direct_copy") },
                onNavigateToIdCardPrint = { navController.navigate("id_card_print") },
                onNavigateToReports = { navController.navigate("reports") }
            )
        }
        composable("document_print") {
            DocumentPrintScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("photo_print") {
            PhotoPrintScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("camera_scan") {
            CameraScanScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("direct_copy") {
            DirectCopyScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("id_card_print") {
            IdCardPrintScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("reports") {
            PrinterReportsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
