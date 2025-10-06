package com.faceplugin.facesdk_plugin.engine

import android.graphics.*
import android.graphics.ImageFormat.NV21
import android.graphics.Rect

class NoopFaceEngine : FaceEngine {
  override fun faceDetection(bitmap: Bitmap) = emptyList<FaceBoxLite>()
  override fun templateExtraction(bitmap: Bitmap, box: FaceBoxLite) = ByteArray(0)
  override fun similarityCalculation(t1: ByteArray, t2: ByteArray) = 0f
  override fun yuvToBitmap(yuv: ByteArray, width:Int, height:Int, mode:Int): Bitmap {
    val yuvImg = YuvImage(yuv, NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImg.compressToJpeg(Rect(0,0,width,height), 80, out)
    val data = out.toByteArray()
    return BitmapFactory.decodeByteArray(data, 0, data.size)
  }
}
