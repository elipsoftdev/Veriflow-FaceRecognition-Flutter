package com.faceplugin.facesdk_plugin.engine

import android.content.Context
import android.graphics.*
import android.graphics.ImageFormat.NV21
import android.graphics.Rect
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerOptions
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class MlPipeFaceEngine : FaceEngine {

  private lateinit var appContext: Context
  private var livenessLevel = 1
  private var detector: FaceDetector? = null
  private var landmarker: FaceLandmarker? = null
  private var tflite: Interpreter? = null
  private val liveness = LivenessState()

  override fun init(context: Context): Int {
    appContext = context.applicationContext

    // Face Detector
    val detOpts = FaceDetectorOptions.builder()
      .setRunningMode(RunningMode.IMAGE)
      .setMinDetectionConfidence(0.5f)
      .setMinSuppressionThreshold(0.3f)
      .build()
    detector = FaceDetector.createFromOptions(appContext, detOpts)

    // Face Landmarker (para liveness activo)
    val lmOpts = FaceLandmarkerOptions.builder()
      .setBaseOptions(
        com.google.mediapipe.tasks.core.BaseOptions.builder().setModelAssetPath(
          // usa el modelo default embebido de MediaPipe Face Landmarker
          "face_landmarker.task" // colócalo en assets si tu versión lo requiere
        ).build()
      )
      .setRunningMode(RunningMode.IMAGE)
      .setNumFaces(1)
      .setMinFaceDetectionConfidence(0.5f)
      .setMinFacePresenceConfidence(0.5f)
      .setMinTrackingConfidence(0.5f)
      .build()
    landmarker = FaceLandmarker.createFromOptions(appContext, lmOpts)

    // TFLite embeddings (MobileFaceNet 112x112 float32)
    val afd = appContext.assets.openFd("mobilefacenet.tflite")
    val file = afd.createInputStream().readBytes()
    tflite = Interpreter(file)

    return 0
  }

  override fun setLivenessLevel(level: Int) {
    livenessLevel = level
  }

  override fun faceDetection(bitmap: Bitmap): List<FaceBoxLite> {
    val mpImg: MPImage = BitmapImageBuilder(bitmap).build()
    val imgOpts = ImageProcessingOptions.builder().build()

    val detRes = detector?.detect(mpImg, imgOpts) ?: return emptyList()
    if (detRes.detections().isEmpty()) return emptyList()

    val list = mutableListOf<FaceBoxLite>()
    val now = System.currentTimeMillis()

    // Usamos FaceMesh para extraer eventos de liveness (blink/left/right/mouth)
    val lmRes = landmarker?.detect(mpImg) // 1 cara esperada
    var events = emptyList<String>()
    var yaw = 0f; var roll = 0f; var pitch = 0f

    if (lmRes != null && lmRes.faceLandmarks().isNotEmpty()) {
      val lm = lmRes.faceLandmarks()[0]
      val e = detectLivenessEvents(lm)     // ["blink","left","right","mouth"]
      events = e
      e.forEach { liveness.register(it, now) }
      val (y, r, p) = estimatePose(lm)
      yaw = y; roll = r; pitch = p
    }

    detRes.detections().forEach { d ->
      val bbox = d.boundingBox()
      val lScore = if (livenessLevel <= 0) 1f else (if (liveness.passed(now)) 1f else 0f)
      list.add(
        FaceBoxLite(
          x1 = max(0, bbox.left()),
          y1 = max(0, bbox.top()),
          x2 = min(bitmap.width-1, bbox.right()),
          y2 = min(bitmap.height-1, bbox.bottom()),
          yaw = yaw, roll = roll, pitch = pitch,
          livenessScore = lScore,
          livenessEvents = liveness.eventsWithin(now)
        )
      )
      // Si ya pasó liveness, resetea para la siguiente verificación en unos segundos
      if (livenessLevel > 0 && liveness.passed(now)) liveness.reset()
    }

    return list
  }

  override fun templateExtraction(bitmap: Bitmap, box: FaceBoxLite): ByteArray {
    // recorta y normaliza a 112x112
    val crop = Bitmap.createBitmap(
      bitmap,
      box.x1.coerceAtLeast(0),
      box.y1.coerceAtLeast(0),
      (box.x2 - box.x1 + 1).coerceAtMost(bitmap.width - box.x1),
      (box.y2 - box.y1 + 1).coerceAtMost(bitmap.height - box.y1)
    )
    val input = Bitmap.createScaledBitmap(crop, 112, 112, true)
    val tensor = bitmapToFloatBuffer(input) // 1x112x112x3 float32 [0..1]

    val out = ByteBuffer.allocateDirect(4 * 128).order(ByteOrder.nativeOrder())
    tflite?.run(tensor, out)
    out.rewind()
    val floats = FloatArray(128)
    out.asFloatBuffer().get(floats)

    // L2 normalize y serializa como bytes
    val norm = sqrt(floats.sumOf { (it * it).toDouble() }.toFloat())
    val emb = floats.map { it / (norm + 1e-6f) }.toFloatArray()

    val bb = ByteBuffer.allocate(4 * emb.size).order(ByteOrder.BIG_ENDIAN)
    bb.asFloatBuffer().put(emb)
    return bb.array()
  }

  override fun similarityCalculation(t1: ByteArray, t2: ByteArray): Float {
    val f1 = toFloatArray(t1); val f2 = toFloatArray(t2)
    var dot = 0f; var n1 = 0f; var n2 = 0f
    for (i in f1.indices) { dot += f1[i]*f2[i]; n1 += f1[i]*f1[i]; n2 += f2[i]*f2[i] }
    return (dot / (sqrt(n1)*sqrt(n2) + 1e-6f)).coerceIn(-1f, 1f)
  }

  override fun yuvToBitmap(yuv: ByteArray, width:Int, height:Int, mode:Int): Bitmap {
    val yuvImg = YuvImage(yuv, NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImg.compressToJpeg(Rect(0,0,width,height), 80, out)
    val data = out.toByteArray()
    var bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
    // modo 7(front)/6(back) si necesitas mirror para frontal:
    if (mode == 7) {
      val m = Matrix(); m.preScale(-1f, 1f)
      bmp = Bitmap.createBitmap(bmp, 0,0,bmp.width,bmp.height,m,true)
    }
    return bmp
  }

  // ======= helpers =======

  private fun bitmapToFloatBuffer(bmp: Bitmap): ByteBuffer {
    val bb = ByteBuffer.allocateDirect(4*1*112*112*3).order(ByteOrder.nativeOrder())
    val pixels = IntArray(112*112)
    bmp.getPixels(pixels, 0, 112, 0, 0, 112, 112)
    var idx = 0
    for (y in 0 until 112) {
      for (x in 0 until 112) {
        val p = pixels[idx++]
        val r = ((p shr 16) and 0xFF) / 255f
        val g = ((p shr 8) and 0xFF) / 255f
        val b = (p and 0xFF) / 255f
        bb.putFloat(r); bb.putFloat(g); bb.putFloat(b)
      }
    }
    bb.rewind()
    return bb
  }

  private fun toFloatArray(bytes: ByteArray): FloatArray {
    val fb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer()
    val arr = FloatArray(fb.remaining()); fb.get(arr); return arr
  }

  /** Detecta eventos básicos de liveness desde landmarks de MediaPipe. */
  private fun detectLivenessEvents(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): List<String> {
    val ev = mutableListOf<String>()
    // índices aproximados (MediaPipe Face Mesh):
    val leftEye = listOf(159, 145)   // párpado sup/inf izquierdo
    val rightEye = listOf(386, 374)  // párpado sup/inf derecho
    val mouth = listOf(13, 14)       // labio sup/inf
    val nose = 1
    val leftEar = 234; val rightEar = 454

    fun dist(a:Int, b:Int): Float {
      val p = landmarks[a]; val q = landmarks[b]
      return hypot(p.x() - q.x(), p.y() - q.y()).toFloat()
    }

    // Parpadeo (EAR simple)
    val earL = dist(leftEye[0], leftEye[1])
    val earR = dist(rightEye[0], rightEye[1])
    val blink = (earL + earR)/2f < 0.015f
    if (blink) ev.add("blink")

    // Boca abierta
    val mouthOpen = dist(mouth[0], mouth[1]) > 0.03f
    if (mouthOpen) ev.add("mouth")

    // Giro izquierda/derecha (nariz hacia orejas)
    val dLeft = dist(nose, leftEar)
    val dRight = dist(nose, rightEar)
    if (dLeft / (dRight + 1e-6f) < 0.75f) ev.add("right") // nariz más cerca oreja derecha -> mirando derecha
    if (dRight / (dLeft + 1e-6f) < 0.75f) ev.add("left")

    return ev.distinct()
  }

  /** Pose aproximada usando landmarks (roll de línea ojos; yaw según nariz; pitch por relación ojos-boca). */
  private fun estimatePose(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Triple<Float,Float,Float> {
    val lEye = landmarks[33]; val rEye = landmarks[263]
    val nose = landmarks[1];  val mouth = landmarks[13]
    val eyeSlope = atan2((rEye.y()-lEye.y()).toFloat(), (rEye.x()-lEye.x()).toFloat())
    val roll = (eyeSlope * 180f / Math.PI.toFloat())

    val faceCenterX = (lEye.x()+rEye.x())/2f
    val yaw = ((nose.x()-faceCenterX)*200f) // escala empírica grados aprox

    val eyeY = (lEye.y()+rEye.y())/2f
    val pitch = ((eyeY - mouth.y())*200f)  // arriba positivo
    return Triple(yaw, roll, pitch)
  }
}
