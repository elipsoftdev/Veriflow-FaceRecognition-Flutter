package com.faceplugin.facesdk_plugin

import androidx.annotation.NonNull
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.util.Log
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.fotoapparat.Fotoapparat
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.preview.Frame
import io.fotoapparat.selector.front
import io.fotoapparat.selector.back
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.view.CameraView
import io.fotoapparat.util.FrameProcessor
import com.faceplugin.facesdk_plugin.engine.FaceEngineProvider

class CameraBaseView(activity: Activity): io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener {

  private val activity: Activity = activity
  private var cameraLens = 0 // 0=back, 1=front
  private var cameraViewInterface: CameraViewInterface? = null
  private val linearLayout: FrameLayout = FrameLayout(activity)
  private val dummyLayout: FrameLayout = FrameLayout(activity)
  private val cameraView: CameraView = CameraView(activity)
  private val frontFotoapparat: Fotoapparat

  @Volatile private var busy = false
  private var lastProc = 0L
  private val minIntervalMs = 160L // ~6 fps procesados

  init {
    linearLayout.layoutParams = FrameLayout.LayoutParams(
      RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
    )
    linearLayout.setBackgroundColor(Color.parseColor("#000000"))

    cameraView.layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
    )
    linearLayout.addView(cameraView)

    dummyLayout.layoutParams = FrameLayout.LayoutParams(
      RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
    )
    dummyLayout.setBackgroundColor(Color.parseColor("#000000"))
    linearLayout.addView(dummyLayout)

    frontFotoapparat = Fotoapparat.with(activity)
      .into(cameraView)
      .lensPosition(front())
      .frameProcessor(SampleFrameProcessor())
      .previewResolution { Resolution(1280, 720) }
      .cameraErrorCallback { error -> Log.e("FaceEngine", "camera error: $error") }
      .build()
  }

  fun setCameraViewInterface(cameraViewInterface: CameraViewInterface) {
    this.cameraViewInterface = cameraViewInterface
  }

  fun getContext(): Context = activity

  fun startCamera(cameraLens: Int) {
    this.cameraLens = cameraLens
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
      ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), 1)
    } else {
      val configuration = CameraConfiguration(previewResolution = { Resolution(1280, 720) })
      if (this.cameraLens == 1) {
        frontFotoapparat.switchTo(lensPosition = front(), cameraConfiguration = configuration)
      } else {
        frontFotoapparat.switchTo(lensPosition = back(), cameraConfiguration = configuration)
      }
      frontFotoapparat.start()
    }
  }

  fun stopCamera() {
    try { frontFotoapparat.stop() } catch (_:Exception){}
    dummyLayout.visibility = View.VISIBLE
  }

  fun getView(): View = linearLayout

  fun dispose() {
    stopCamera()
    linearLayout.removeAllViews()
    cameraViewInterface = null
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
    if (requestCode == 1 && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      val configuration = CameraConfiguration(previewResolution = { Resolution(1280, 720) })
      if (this.cameraLens == 1) {
        frontFotoapparat.switchTo(lensPosition = front(), cameraConfiguration = configuration)
      } else {
        frontFotoapparat.switchTo(lensPosition = back(), cameraConfiguration = configuration)
      }
      frontFotoapparat.start()
    }
    return true
  }

  inner class SampleFrameProcessor : FrameProcessor {
    override fun invoke(frame: Frame) {
      val now = System.currentTimeMillis()
      if (busy || now - lastProc < minIntervalMs) return
      busy = true; lastProc = now

      activity.runOnUiThread { dummyLayout.visibility = View.INVISIBLE }
      val mode = if (cameraLens == 0) 6 else 7

      val bmp: Bitmap = FaceEngineProvider.get().yuvToBitmap(frame.image, frame.size.width, frame.size.height, mode)
      try {
        cameraViewInterface?.onFrame(bmp)
      } finally {
        bmp.recycle()
        busy = false
      }
    }
  }
}
