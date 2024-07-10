package com.example.richardstallmaneye

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.PI

class MainActivity : ComponentActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var textureView: TextureView? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Log.e("MainActivity", "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

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
                    selectedBitmap = if (Build.VERSION.SDK_INT < 28) {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, it)
                        ImageDecoder.decodeBitmap(source)
                    }
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { view ->
                        textureView = view
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(textureView) {
                textureView?.let {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        openCamera()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            selectedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
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
                                offsetX += pan.x
                                offsetY += pan.y
                                scale *= zoom
                                //rotation += rotate * (180f / PI.toFloat())
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Button(onClick = { imagePicker.launch("image/*") }) {
                    Text("Select Image")
                }

                Slider(
                    value = transparency,
                    onValueChange = { transparency = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Slider(
                    value = rotation,
                    onValueChange = { rotation = it },
                    valueRange = 0f..360f,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCameraPreviewSession()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        cameraDevice?.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e("MainActivity", "Camera error: $error")
                        cameraDevice?.close()
                        cameraDevice = null
                    }
                }, null)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open camera", e)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView?.surfaceTexture
            texture?.setDefaultBufferSize(1080, 1920) // Adjust as needed

            val surface = texture?.let { Surface(it) }

            val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            surface?.let { previewRequestBuilder?.addTarget(it) }

            surface?.let {
                cameraDevice?.createCaptureSession(listOf(it), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            previewRequestBuilder?.let { builder ->
                                session.setRepeatingRequest(builder.build(), null, null)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to start camera preview", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("MainActivity", "Failed to configure camera session")
                    }
                }, null)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to create camera preview session", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice?.close()
    }
}