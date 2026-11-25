package com.smartprint.direct

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
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
        // Must run before super.onCreate: swaps the Starting theme for the app theme.
        val splash = installSplashScreen()
        val splashStart = SystemClock.uptimeMillis()
        // Hold the branded splash just long enough for the icon animation to play.
        splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - splashStart < 650 }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(320)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .withEndAction { provider.remove() }
                .start()
        }
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

        // Only on a fresh launch: after rotation/theme recreation the same SEND intent is still
        // attached and would otherwise print the shared document again.
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            SmartPrintTheme {
                AppNavigation(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        // Consume it so it can never be handled twice
        this.intent = Intent(Intent.ACTION_MAIN)
        uris.forEach { viewModel.printSharedUri(it) }
    }
}

private fun NavController.safeNavigate(route: String) {
    // Ignore taps while a transition is running (double tap pushed the same screen twice)
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route) { launchSingleTop = true }
    }
}

private fun NavController.safeBack() {
    // A double tap on Back used to pop past the start destination and leave a blank screen
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED && previousBackStackEntry != null) {
        popBackStack()
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
                onNavigateToDocumentPrint = { navController.safeNavigate("document_print") },
                onNavigateToPhotoPrint = { navController.safeNavigate("photo_print") },
                onNavigateToCameraScan = { navController.safeNavigate("camera_scan") },
                onNavigateToDirectCopy = { navController.safeNavigate("direct_copy") },
                onNavigateToIdCardPrint = { navController.safeNavigate("id_card_print") },
                onNavigateToReports = { navController.safeNavigate("reports") }
            )
        }
        composable("document_print") {
            DocumentPrintScreen(viewModel = viewModel, onBack = { navController.safeBack() })
        }
        composable("photo_print") {
            PhotoPrintScreen(viewModel = viewModel, onBack = { navController.safeBack() })
        }
        composable("camera_scan") {
            CameraScanScreen(viewModel = viewModel, onBack = { navController.safeBack() })
        }
        composable("direct_copy") {
            DirectCopyScreen(viewModel = viewModel, onBack = { navController.safeBack() })
        }
        composable("id_card_print") {
            IdCardPrintScreen(viewModel = viewModel, onBack = { navController.safeBack() })
        }
        composable("reports") {
            PrinterReportsScreen(viewModel = viewModel, onBack = { navController.safeBack() })
        }
    }
}
