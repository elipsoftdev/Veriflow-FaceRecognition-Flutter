package com.faceplugin.facesdk_plugin.engine

import android.content.Context
import android.graphics.Bitmap

interface FaceEngine {
  fun init(context: Context): Int = 0
  fun setLivenessLevel(level:Int) {}  // 0=off, 1=básico, 2=estricto
  fun faceDetection(bitmap: Bitmap): List<FaceBoxLite>
  fun templateExtraction(bitmap: Bitmap, box: FaceBoxLite): ByteArray
  fun similarityCalculation(t1: ByteArray, t2: ByteArray): Float
  fun yuvToBitmap(yuv: ByteArray, width:Int, height:Int, mode:Int): Bitmap
}
