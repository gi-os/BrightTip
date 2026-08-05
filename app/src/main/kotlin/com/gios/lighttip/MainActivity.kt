package com.gios.lighttip

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gios.light.common.report.LightReport
import com.gios.light.common.report.ReportOverlay
import com.gios.light.common.hw.LightKey
import com.gios.light.common.hw.LightKeys
import com.gios.light.common.hw.LocalWheelBus
import com.gios.light.common.hw.WheelBus
import com.gios.lighttip.ui.CameraScreen
import com.gios.lighttip.ui.HomeScreen
import com.gios.lighttip.ui.PeopleScreen
import com.gios.lighttip.ui.ReceiptScreen
import com.gios.lighttip.ui.SettingsScreen
import com.gios.lighttip.ui.TipViewModel
import com.gios.lighttip.ui.TotalsScreen
import com.gios.lighttip.ui.theme.LightTipTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {

    /** Wheel notches on their way to whichever screen is up. */
    private val wheel = WheelBus()

    /**
     * Every hardware key arrives here first — `DecorView` calls the window callback
     * before it walks the view hierarchy — which is what lets the wheel beat the
     * focused amount field. Both halves of a notch are consumed: one notch is a
     * complete DOWN+UP pair, and letting the UP through means a text field can read
     * it as a keypress.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (LightKeys.of(event)) {
            LightKey.WheelUp -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(1)
                return true
            }

            LightKey.WheelDown -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(-1)
                return true
            }

            else -> Unit
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First thing, before anything else can throw: the handler chains onto whatever is
        // already installed and only writes a file, so it is safe this early.
        LightReport.install(
            context = this,
            appName = "LightTip",
            label = "tip",
            token = BuildConfig.REPORT_TOKEN,
        )
        setContent {
            LightTipTheme {
                val nav = rememberNavController()
                val vm: TipViewModel = viewModel()

                val scanQr = rememberLauncherForActivityResult(ScanContract()) { result ->
                    val raw = result.contents?.trim() ?: return@rememberLauncherForActivityResult
                    // The companion page can prefix the payload; accept it either way.
                    val key = if (raw.startsWith("anthropic:", true)) {
                        raw.substringAfter(':').trim()
                    } else {
                        raw
                    }
                    vm.setApiKey(key)
                }

                // Every screen below can reach the wheel.
                CompositionLocalProvider(LocalWheelBus provides wheel) {
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                vm = vm,
                                onCapture = { nav.navigate("camera") },
                                onOpenReceipt = { id -> nav.navigate("receipt/$id") },
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("camera") {
                            CameraScreen(
                                newFile = { vm.newCaptureFile() },
                                onCaptured = { file ->
                                    vm.addFromFile(file)
                                    nav.popBackStack("home", false)
                                },
                            )
                        }
                        composable(
                            "receipt/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            val id = entry.arguments!!.getString("id")!!
                            ReceiptScreen(
                                vm = vm,
                                receiptId = id,
                                onPeople = { nav.navigate("people/$id") },
                                onTotals = { nav.navigate("totals/$id") },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(
                            "people/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            PeopleScreen(
                                vm = vm,
                                receiptId = entry.arguments!!.getString("id")!!,
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable(
                            "totals/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            TotalsScreen(
                                vm = vm,
                                receiptId = entry.arguments!!.getString("id")!!,
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                vm = vm,
                                onScanQr = {
                                    scanQr.launch(
                                        ScanOptions().setBeepEnabled(false)
                                            .setPrompt("Scan Anthropic API key QR"),
                                    )
                                },
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
                // Shake to report, the crash offer on next launch, and the app's own noticed
                // failures. A sibling, not a wrapper — the sheet is its own window, so it covers
                // the app whether or not it contains it.
                ReportOverlay()
            }
        }
    }
}
