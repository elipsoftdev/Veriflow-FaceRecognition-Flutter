package com.faceplugin.facesdk_plugin

import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformViewRegistry
import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream

// Tu engine (como ya lo usabas)
import com.faceplugin.facesdk_plugin.engine.*

/** FacesdkPlugin (modernizado con CameraX) */
class FacesdkPlugin :
  FlutterPlugin,
  MethodChannel.MethodCallHandler,
  ActivityAware {

  // --- Flutter / Engine refs ---
  private lateinit var channel: MethodChannel
  private lateinit var registry: PlatformViewRegistry
  private lateinit var dartExecutor: DartExecutor
  private lateinit var appContext: Context

  // --- Activity / Lifecycle ---
  private var activity: Activity? = null

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    appContext = binding.applicationContext
    registry = binding.platformViewRegistry
    dartExecutor = binding.dartExecutor

    // Canal de métodos (conserva tu API)
    channel = MethodChannel(binding.binaryMessenger, "facesdk_plugin")
    channel.setMethodCallHandler(this)

    // Instala tu motor (igual que antes)
    FaceEngineProvider.set(MlPipeFaceEngine())
    FaceDetectionFlutterView.livenessDetectionLevel = 1

    // 👇 Registrar CameraX PlatformView (se completa cuando tengamos Activity)
    // Lo registramos aquí pero con proveedores que leen activity/lifecycle en runtime.
    registry.registerViewFactory(
      "facesdk_plugin/camerax_view",
      CameraXPlatformViewFactory(
        messenger = binding.binaryMessenger,
        activityProvider = {
          activity ?: throw IllegalStateException("Activity is null. Ensure ActivityAware attached.")
        },
        lifecycleProvider = {
          (activity as? LifecycleOwner)
            ?: throw IllegalStateException("Activity must implement LifecycleOwner (use FlutterFragmentActivity).")
        },
        processorProvider = {
          // FrameProcessor que conecta con tu motor
          FrameProcessor { bitmap, rotationDegrees ->
            // ⚠️ Aquí enchufas tu pipeline (MediaPipe/TFLite).
            // Ejemplo: detección + extracción (modo demo, síncrono)
            try {
              val faces = FaceEngineProvider.get().faceDetection(bitmap)
              // Si quieres, puedes emitir resultados a Flutter con EventChannel (no incluido aquí)
              // o guardar estado según tu diseño. Este ejemplo solo ejecuta el pipeline.
              // Para embeddings por frame:
              // faces.forEach { f -> FaceEngineProvider.get().templateExtraction(bitmap, f) }
            } catch (_: Throwable) {
              // Log opcional
            }
          }
        }
      )
    )

    // ✅ Si quieres seguir exponiendo tu view anterior:
    // (Se completa cuando tengamos Activity en onAttachedToActivity)
    // Nada que hacer aquí.
  }

  // ----------------- MethodChannel API (igual que tu versión) -----------------
  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
    when (call.method) {
      "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")

      // init nativo (carga modelos)
      "init" -> {
        val ret = FaceEngineProvider.get().init(appContext)
        result.success(ret)
      }

      // ajustar nivel de liveness activo
      "setParam" -> {
        val level: Int? = call.argument("check_liveness_level")
        if (level != null) {
          FaceDetectionFlutterView.livenessDetectionLevel = level
          FaceEngineProvider.get().setLivenessLevel(level)
        }
        result.success(0)
      }

      // extracción off-line sobre una imagen (sin cámara)
      "extractFaces" -> {
        val imagePath: String? = call.argument("imagePath")
        if (imagePath.isNullOrEmpty()) {
          result.error("ARG", "imagePath vacío", null); return
        }
        val bmp = BitmapFactory.decodeFile(imagePath)
        val faces = FaceEngineProvider.get().faceDetection(bmp)

        val out: ArrayList<HashMap<String, Any>> = ArrayList()
        for (f in faces) {
          val faceBmp = Utils.cropFaceLite(bmp, f)
          val baos = ByteArrayOutputStream()
          faceBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
          val faceJpg = baos.toByteArray()
          val templ = FaceEngineProvider.get().templateExtraction(bmp, f)

          val e = HashMap<String, Any>()
          e["x1"] = f.x1; e["y1"] = f.y1; e["x2"] = f.x2; e["y2"] = f.y2
          e["liveness"] = f.livenessScore
          e["yaw"] = f.yaw; e["roll"] = f.roll; e["pitch"] = f.pitch
          e["livenessEvents"] = f.livenessEvents
          e["templates"] = templ
          e["faceJpg"] = faceJpg
          e["frameWidth"] = bmp.width; e["frameHeight"] = bmp.height
          out.add(e)
        }
        result.success(out)
      }

      "similarityCalculation" -> {
        val t1: ByteArray? = call.argument("templates1")
        val t2: ByteArray? = call.argument("templates2")
        if (t1 == null || t2 == null || t1.isEmpty() || t2.isEmpty()) {
          result.error("ARG", "templates vacíos", null); return
        }
        val sim = FaceEngineProvider.get().similarityCalculation(t1, t2)
        result.success(sim)
      }

      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  // ----------------- ActivityAware -----------------
  override fun onAttachedToActivity(@NonNull binding: ActivityPluginBinding) {
    activity = binding.activity

    // (Opcional) Seguir registrando TU view anterior basada en tu engine/cámara previa:
    // Si aún la usas:
    registry.registerViewFactory(
      "facedetectionview",
      FaceDetectionViewFactory(binding, dartExecutor) // <- tu implementación existente
    )
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(@NonNull binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }
}
