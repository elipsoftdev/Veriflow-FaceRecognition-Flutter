package com.faceplugin.facesdk_plugin

import android.content.Context
import android.view.View
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val frameProcessor: FrameProcessor
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private lateinit var previewView: PreviewView
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    fun createPreviewView(): View {
        previewView = PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            keepScreenOn = true
        }
        return previewView
    }

    fun startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(previewView.display.rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(cameraExecutor!!, FaceAnalyzer(frameProcessor)) }

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    }

    fun toggleCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
            CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        bindUseCases()
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    fun getView(): View = previewView
}
