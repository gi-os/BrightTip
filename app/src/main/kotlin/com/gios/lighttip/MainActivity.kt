package com.gios.lighttip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        }
    }
}
