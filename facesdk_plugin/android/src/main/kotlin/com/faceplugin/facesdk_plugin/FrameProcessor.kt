package com.faceplugin.facesdk_plugin

import android.graphics.Bitmap

/** Implementa esto para conectar tu modelo (MediaPipe/TFLite). */
fun interface FrameProcessor {
    /**
     * Procesa un frame RGB.
     * @param bitmap RGB sin rotación aplicada (aplica rotationDegrees si tu modelo lo requiere)
     * @param rotationDegrees 0/90/180/270
     * @return Cualquier objeto con tu resultado (puede ser Map<String, Any>, JSON, etc.)
     */
    fun process(bitmap: Bitmap, rotationDegrees: Int): Any?
}
