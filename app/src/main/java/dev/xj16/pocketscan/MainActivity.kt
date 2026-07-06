package dev.xj16.pocketscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.xj16.pocketscan.ui.HomeViewModel
import dev.xj16.pocketscan.ui.ScanViewModel
import dev.xj16.pocketscan.ui.screen.HomeScreen
import dev.xj16.pocketscan.ui.screen.ReviewScreen
import dev.xj16.pocketscan.ui.screen.ScannerScreen
import dev.xj16.pocketscan.ui.theme.PocketScanTheme
import dev.xj16.pocketscan.util.ImageStore

/**
 * Single-activity host. Wires the three destinations (home → scanner → review)
 * and a gallery-import path, all sharing a single [ScanViewModel] so a scan in
 * progress survives navigation between the scanner and the review screen.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PocketScanApp

        setContent {
            PocketScanTheme {
                val navController = rememberNavController()
                val context = LocalContext.current

                val homeVm: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(app.repository),
                )
                val scanVm: ScanViewModel = viewModel(
                    factory = ScanViewModel.Factory(app.repository),
                )

                // Gallery import: decode the picked image and run the same
                // offline pipeline used for camera captures.
                val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri ->
                    if (uri != null) {
                        val bmp = ImageStore.loadFromUri(context, uri)
                        if (bmp != null) {
                            scanVm.onPhotoCaptured(context, bmp)
                            navController.navigate(Route.REVIEW)
                        }
                    }
                }

                NavHost(navController = navController, startDestination = Route.HOME) {
                    composable(Route.HOME) {
                        HomeScreen(
                            viewModel = homeVm,
                            onScanClick = {
                                scanVm.reset()
                                navController.navigate(Route.SCANNER)
                            },
                            onImportClick = {
                                scanVm.reset()
                                importLauncher.launch("image/*")
                            },
                            onReceiptClick = { /* detail screen reserved for a future release */ },
                        )
                    }
                    composable(Route.SCANNER) {
                        ScannerScreen(
                            viewModel = scanVm,
                            onReviewReady = {
                                navController.navigate(Route.REVIEW) {
                                    popUpTo(Route.SCANNER) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Route.REVIEW) {
                        ReviewScreen(
                            viewModel = scanVm,
                            onSaved = {
                                scanVm.reset()
                                navController.popBackStack(Route.HOME, inclusive = false)
                            },
                            onDiscard = {
                                navController.popBackStack(Route.HOME, inclusive = false)
                            },
                        )
                    }
                }
            }
        }
    }

    private object Route {
        const val HOME = "home"
        const val SCANNER = "scanner"
        const val REVIEW = "review"
    }
}
