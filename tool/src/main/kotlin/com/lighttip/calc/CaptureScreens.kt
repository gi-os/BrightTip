package com.lighttip.calc

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.asKotlinResult
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mirrors LightQrCodeScanner's own permission handling for the plain camera use case. */
@Composable
private fun PermissionGate(
    permission: String,
    rationale: String,
    content: @Composable () -> Unit,
) {
    val launcher = rememberPermissionRequestLauncher(permission)
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf<Boolean?>(null) }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val ok = checkPermission(permission).asKotlinResult
                .getOrNull()?.permissionResult == LightServiceMethod.GetPermission.Result.Granted
            granted = ok
            if (!ok && !asked) {
                launcher?.launch()
                asked = true
            }
        }
    }
    when (granted) {
        true -> content()
        false -> CenterMessage(rationale)
        null -> CenterMessage("…")
    }
}

class CaptureChooserScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
) : LightScreen<Unit, EmptyViewModel>(sealedActivity) {

    override val viewModelClass = EmptyViewModel::class.java
    override fun createViewModel() = EmptyViewModel()

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Add receipt"),
                )
                MenuRow("Take a photo") {
                    navigateTo({ CameraCaptureScreen(it, repository) })
                }
                MenuRow("Choose from album") {
                    navigateTo({ AlbumPickerScreen(it, repository) })
                }
            }
        }
    }
}

class CameraCaptureScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
) : LightScreen<Unit, EmptyViewModel>(sealedActivity) {

    override val viewModelClass = EmptyViewModel::class.java
    override fun createViewModel() = EmptyViewModel()

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        var capturing by remember { mutableStateOf(false) }

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Photograph the bill"),
                )
                PermissionGate(Manifest.permission.CAMERA, "Camera permission is required.") {
                    val controller = remember {
                        LifecycleCameraController(context).apply {
                            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
                        }
                    }
                    LaunchedEffect(Unit) { controller.bindToLifecycle(lifecycleOwner) }
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { PreviewView(it).apply { this.controller = controller } },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3f.gridUnitsAsDp())
                                .background(LightThemeTokens.colors.background)
                                .lightClickable(onClickLabel = "Capture", role = Role.Button) {
                                    if (capturing) return@lightClickable
                                    capturing = true
                                    val out = repository.newCaptureFile()
                                    val options = ImageCapture.OutputFileOptions.Builder(out).build()
                                    controller.takePicture(
                                        options,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(
                                                results: ImageCapture.OutputFileResults,
                                            ) {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        repository.addReceiptFromFile(out)
                                                    }
                                                    goBack()
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                capturing = false
                                            }
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(
                                if (capturing) "Reading…" else "Capture",
                                variant = LightTextVariant.Button,
                            )
                        }
                    }
                }
            }
        }
    }
}

class AlbumPickerScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
) : LightScreen<Unit, EmptyViewModel>(sealedActivity) {

    override val viewModelClass = EmptyViewModel::class.java
    override fun createViewModel() = EmptyViewModel()

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Choose from album"),
                )
                PermissionGate(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    "Photos permission is required.",
                ) {
                    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                    LaunchedEffect(Unit) {
                        uris = withContext(Dispatchers.IO) { queryAlbumImages(context) }
                    }
                    if (uris.isEmpty()) {
                        CenterMessage("No photos found.")
                    } else {
                        LightLazyScrollView(uniformItemHeightGridUnits = 4f) {
                            items(uris, key = { it.toString() }) { uri ->
                                AlbumRow(uri) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            repository.addReceiptFromUri(uri, context)
                                        }
                                        goBack()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AlbumRow(uri: Uri, onClick: () -> Unit) {
        val context = LocalContext.current
        val thumbnail: Bitmap? = remember(uri) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(240, 240), null) }
                .getOrNull()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4f.gridUnitsAsDp())
                .lightClickable(onClickLabel = "Use this photo", role = Role.Button) { onClick() }
                .padding(0.5f.gridUnitsAsDp()),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(3.5f.gridUnitsAsDp()),
                )
            } else {
                LightText("Photo", variant = LightTextVariant.Detail)
            }
        }
    }
}

private fun queryAlbumImages(context: Context, limit: Int = 60): List<Uri> {
    val out = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sort,
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        var n = 0
        while (cursor.moveToNext() && n < limit) {
            out.add(
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idColumn),
                ),
            )
            n++
        }
    }
    return out
}

/** Scans the QR produced by the companion web page and stores the key. */
class KeyScannerScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
) : LightScreen<Unit, EmptyViewModel>(sealedActivity) {

    override val viewModelClass = EmptyViewModel::class.java
    override fun createViewModel() = EmptyViewModel()

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val scope = rememberCoroutineScope()
        LightTheme(colors = colors) {
            LightQrCodeScanner(
                title = "Scan API key",
                onScanned = { value ->
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.setApiKey(value.trim()) }
                        goBack()
                    }
                },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
