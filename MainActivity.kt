package com.example.richardstallmaneye

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("CameraXApp", "Permission granted")
        } else {
            Log.e("CameraXApp", "Permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraPreviewWithOverlay()
                }
            }
        }
    }

    @Composable
    fun CameraPreviewWithOverlay() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        var scale by remember { mutableStateOf(1f) }
        var rotation by remember { mutableStateOf(0f) }
        var transparency by remember { mutableStateOf(0.5f) }

        val imagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri: Uri? ->
                selectedImageUri = uri
                uri?.let {
                    selectedBitmap = loadBitmapFromUri(context, it)
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // CameraX Preview
            CameraPreview(
                lensFacing = lensFacing,
                lifecycleOwner = lifecycleOwner
            )

            // Imagen superpuesta
            OverlayImage(selectedBitmap, offsetX, offsetY, scale, rotation, transparency) { dx, dy, newScale ->
                offsetX += dx
                offsetY += dy
                scale *= newScale
            }

            // Controles de la interfaz
            ControlPanel(
                onSelectImage = { imagePicker.launch("image/*") },
                onSwitchCamera = { switchCamera() },
                transparency = transparency,
                onTransparencyChange = { transparency = it },
                rotation = rotation,
                onRotationChange = { rotation = it }
            )
        }
    }

    @Composable
    private fun CameraPreview(
        lensFacing: Int,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        val context = LocalContext.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraXApp", "Use case binding failed", exc)
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    @Composable
    private fun OverlayImage(
        bitmap: Bitmap?,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        rotation: Float,
        transparency: Float,
        onGesture: (Float, Float, Float) -> Unit
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .fillMaxSize()
                    .offset(offsetX.dp, offsetY.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        rotationZ = rotation
                    )
                    .alpha(transparency)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onGesture(pan.x, pan.y, zoom)
                        }
                    },
                contentScale = ContentScale.Fit
            )
        }
    }

    @Composable
    private fun ControlPanel(
        onSelectImage: () -> Unit,
        onSwitchCamera: () -> Unit,
        transparency: Float,
        onTransparencyChange: (Float) -> Unit,
        rotation: Float,
        onRotationChange: (Float) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onSelectImage) {
                Text("Select Image")
            }

            Button(onClick = onSwitchCamera) {
                Text("Switch Camera")
            }

            Slider(
                value = transparency,
                onValueChange = onTransparencyChange,
                valueRange = 0f..1f,
                modifier = Modifier.padding(top = 16.dp)
            )

            Slider(
                value = rotation,
                onValueChange = onRotationChange,
                valueRange = 0f..360f,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        } else {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
