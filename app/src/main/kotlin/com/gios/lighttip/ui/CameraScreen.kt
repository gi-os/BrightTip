package com.gios.lighttip.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun CameraScreen(newFile: () -> File, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        EmptyState("Camera permission is needed to photograph the bill.")
        return
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var captured by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { controller.bindToLifecycle(lifecycleOwner) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    preview = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = {
                if (captured) return@Button
                // Grab the frame already on screen — instant, no capture round-trip.
                val bmp = preview?.bitmap ?: return@Button
                captured = true
                val out = newFile()
                out.outputStream().use {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
                }
                onCaptured(out)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .height(64.dp)
                .fillMaxWidth(0.7f),
        ) {
            Text(
                if (captured) "Reading…" else "Capture",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
