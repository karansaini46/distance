package com.karan.distancewidget.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.Autorenew
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.karan.distancewidget.data.CommentData
import com.karan.distancewidget.data.FirebaseHelper
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.data.StorageHelper
import com.karan.distancewidget.ui.theme.DistanceWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class StoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DistanceWidgetTheme {
                StoryApp(onClose = { finish() })
            }
        }
    }
}

@Composable
fun StoryApp(onClose: () -> Unit) {
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        CameraScreen(
            onClose = { showCamera = false },
            onPhotoSent = { showCamera = false }
        )
    } else {
        StoryScreen(
            onClose = onClose,
            onOpenCamera = { showCamera = true }
        )
    }
}

@Composable
fun StoryScreen(onClose: () -> Unit, onOpenCamera: () -> Unit) {
    val context = LocalContext.current
    val partnerId = Prefs.getPartnerId(context)
    val myId = Prefs.getUserId(context)

    if (partnerId == null || myId == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Setup required.", color = Color.White)
        }
        return
    }

    // Load photo count from cache
    val photoCount = remember { StorageHelper.getCachedPhotoCount(context, partnerId) }
    
    if (photoCount == 0) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos to show.", color = Color.White)
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(40.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            // Add Camera button so user can send the first photo
            IconButton(
                onClick = onOpenCamera,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.CameraAlt, "Camera", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        return
    }

    var currentIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    val currentPhotoFile = remember(currentIndex) {
        File(context.filesDir, "photo_${partnerId}_$currentIndex.jpg")
    }

    // Story progress animation
    val progressAnim = remember { Animatable(0f) }
    
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused) {
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = (5000 * (1f - progressAnim.value)).toInt(), easing = LinearEasing)
            )
            // Move to next photo
            if (currentIndex < photoCount - 1) {
                currentIndex++
                progressAnim.snapTo(0f)
            } else {
                onClose() // Close when done
            }
        } else {
            progressAnim.stop()
        }
    }

    // Comments for current photo
    var comments by remember { mutableStateOf<List<CommentData>>(emptyList()) }
    LaunchedEffect(currentIndex) {
        FirebaseHelper.getComments(fromId = myId, toId = partnerId, photoIndex = currentIndex).collect { 
            comments = it
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        val release = tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth * 0.3f) {
                            if (currentIndex > 0) {
                                currentIndex--
                                isPaused = true // force restart effect
                            }
                        } else {
                            if (currentIndex < photoCount - 1) {
                                currentIndex++
                                isPaused = true
                            } else {
                                onClose()
                            }
                        }
                        isPaused = false
                    }
                )
            }
    ) {
        // Image
        AsyncImage(
            model = currentPhotoFile,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlays
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).align(Alignment.TopCenter).background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
            )
        ))
        
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).align(Alignment.BottomCenter).background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
            )
        ))

        // Progress bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until photoCount) {
                LinearProgressIndicator(
                    progress = { 
                        if (i < currentIndex) 1f 
                        else if (i == currentIndex) progressAnim.value 
                        else 0f 
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // Top Buttons (Download & Close)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 8.dp)
        ) {
            IconButton(
                onClick = {
                    val success = StorageHelper.savePhotoToGallery(context, currentPhotoFile)
                    if (success) {
                        Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.Download, "Download", tint = Color.White)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }

        // Comments list
        LazyColumn(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                .heightIn(max = 200.dp),
            reverseLayout = true
        ) {
            items(comments.reversed()) { comment ->
                Text(
                    text = comment.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom Bar (Input & Camera)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var commentText by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            
            OutlinedTextField(
                value = commentText,
                onValueChange = { 
                    commentText = it 
                    isPaused = it.isNotEmpty() 
                },
                placeholder = { Text("Send comment...", color = Color.White.copy(alpha = 0.7f)) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    if (commentText.isNotEmpty()) {
                        IconButton(onClick = {
                            val txt = commentText
                            commentText = ""
                            isPaused = false
                            scope.launch {
                                FirebaseHelper.sendComment(myId, partnerId, currentIndex, txt)
                            }
                        }) {
                            Icon(Icons.Default.Send, "Send", tint = Color.White)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = onOpenCamera,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.CameraAlt, "Camera", tint = Color.White)
            }
        }
    }
}

@Composable
fun CameraScreen(onClose: () -> Unit, onPhotoSent: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCamPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    
    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider.value = future.get()
        }, ContextCompat.getMainExecutor(context))
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }
    var capturedPhoto by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myId = Prefs.getUserId(context)

    // Camera state
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }

    // Bind Camera UseCases
    LaunchedEffect(cameraProvider.value, lensFacing) {
        val provider = cameraProvider.value ?: return@LaunchedEffect
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        
        try {
            provider.unbindAll()
            // Check if selected camera is available, fallback if not
            val finalSelector = if (provider.hasCamera(cameraSelector)) {
                cameraSelector
            } else {
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            }
            
            provider.bindToLifecycle(lifecycleOwner, finalSelector, preview, imageCapture)
        } catch(e: Exception) {
            Log.e("Camera", "Use case binding failed", e)
        }
    }

    // Update Flash Mode dynamically
    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    // Gallery picker
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            capturedPhoto = uri
        }
    }

    if (!hasCamPermission) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Camera permission required.", color = Color.White)
        }
        return
    }

    if (capturedPhoto != null) {
        // --- Review Screen ---
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = capturedPhoto,
                contentDescription = "Review Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { capturedPhoto = null },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Cancel", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save to Gallery Button
                Button(
                    onClick = {
                        val file = File(capturedPhoto!!.path ?: "")
                        if (file.exists()) {
                            val saved = StorageHelper.savePhotoToGallery(context, file)
                            if (saved) {
                                Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Already in Gallery", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Download, "Save", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", color = Color.White)
                }

                // Send Button
                Button(
                    onClick = {
                        if (myId == null) return@Button
                        isUploading = true
                        scope.launch {
                            val success = StorageHelper.uploadPhoto(context, capturedPhoto!!, myId)
                            withContext(Dispatchers.Main) {
                                isUploading = false
                                if (success) {
                                    Toast.makeText(context, "Photo sent to partner!", Toast.LENGTH_SHORT).show()
                                    onPhotoSent()
                                } else {
                                    Toast.makeText(context, "Failed to send", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    enabled = !isUploading,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Send, "Send", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    } else {
        // --- Camera View ---
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Top bar (Close + Flash toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                
                // Flash Toggle
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    val flashIcon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    val tint = if (flashMode == ImageCapture.FLASH_MODE_ON) Color.Yellow else Color.White
                    Icon(flashIcon, "Flash", tint = tint, modifier = Modifier.size(24.dp))
                }
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp, start = 24.dp, end = 24.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Button
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                }

                // Capture Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .clickable(enabled = !isUploading) {
                            if (myId == null) return@clickable
                            val photoFile = File(
                                context.cacheDir,
                                java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis()) + ".jpg"
                            )
                            
                            // Apply metadata to prevent mirror effect on front camera
                            val metadata = ImageCapture.Metadata().apply {
                                isReversedHorizontal = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                            }
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                                .setMetadata(metadata)
                                .build()
                            
                            val executor = ContextCompat.getMainExecutor(context)
                            
                            isUploading = true
                            try {
                                imageCapture.takePicture(
                                    outputOptions, executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            isUploading = false
                                            capturedPhoto = Uri.fromFile(photoFile)
                                        }
                                        override fun onError(exc: ImageCaptureException) {
                                            isUploading = false
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                isUploading = false
                                Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White))
                    }
                }

                // Flip Camera Button
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            CameraSelector.LENS_FACING_BACK
                        } else {
                            CameraSelector.LENS_FACING_FRONT
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Autorenew, "Flip Camera", tint = Color.White)
                }
            }
        }
    }
}
