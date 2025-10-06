package com.faceplugin.facesdk_plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RSRuntimeException
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import androidx.annotation.WorkerThread
import java.nio.ByteBuffer

/**
 * Conversor YUV_420_888 -> Bitmap RGB.
 * Nota: RenderScript está deprecado pero aún es rápido en muchos dispositivos.
 * Para máxima compatibilidad, puedes migrar a un conversor puro Kotlin si lo prefieres.
 */
class YuvToRgbConverter(context: Context) {

    private var rs: RenderScript? = null
    private var scriptYuvToRgb: ScriptIntrinsicYuvToRGB? = null
    private var yuvBuffer: ByteBuffer? = null
    private var inputAllocation: Allocation? = null
    private var outputAllocation: Allocation? = null

    init {
        try {
            rs = RenderScript.create(context)
            scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
        } catch (_: RSRuntimeException) {
            // Fallback: si RenderScript no está disponible, deberás usar otro conversor
        }
    }

    @WorkerThread
    fun yuvToRgb(image: Image, output: Bitmap) {
        val rsLocal = rs ?: return manualFallback(image, output)

        val yuvBytes = imageToYuv(image)
        val yuvSize = yuvBytes.size

        if (yuvBuffer == null || yuvBuffer!!.capacity() < yuvSize) {
            yuvBuffer = ByteBuffer.allocateDirect(yuvSize)
        }
        yuvBuffer!!.rewind()
        yuvBuffer!!.put(yuvBytes)

        val elemYuv = Element.U8(rsLocal)
        val yuvType = android.renderscript.Type.Builder(rsLocal, elemYuv).setX(yuvSize)
        if (inputAllocation == null || inputAllocation!!.bytesSize != yuvSize) {
            inputAllocation?.destroy()
            inputAllocation = Allocation.createTyped(rsLocal, yuvType.create(), Allocation.USAGE_SCRIPT)
        }
        inputAllocation!!.copyFrom(yuvBytes)

        val elemOut = Element.RGBA_8888(rsLocal)
        val outType = android.renderscript.Type.Builder(rsLocal, elemOut).setX(output.width).setY(output.height)
        if (outputAllocation == null ||
            outputAllocation!!.type.x != output.width ||
            outputAllocation!!.type.y != output.height) {
            outputAllocation?.destroy()
            outputAllocation = Allocation.createTyped(rsLocal, outType.create(), Allocation.USAGE_SCRIPT)
        }

        scriptYuvToRgb!!.setInput(inputAllocation)
        scriptYuvToRgb!!.forEach(outputAllocation)
        outputAllocation!!.copyTo(output)
    }

    private fun manualFallback(image: Image, output: Bitmap) {
        // Conversión básica YUV420 a RGB (más lenta); implementa si quieres evitar RenderScript.
        // Por simplicidad, aquí dejamos un placeholder:
        val rect = Rect(0, 0, image.width, image.height)
        val planes = image.planes
        if (image.format != ImageFormat.YUV_420_888 || planes.size < 3) {
            return
        }
        // Implementa un conversor YUV->RGB puro si eliminas RenderScript.
        // (O usa librerías existentes según tu pipeline.)
    }

    private fun imageToYuv(image: Image): ByteArray {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)

        // **NV21**: VU interleaved
        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStride = image.planes[1].pixelStride
        var offset = ySize
        val width = image.width
        val height = image.height

        val bufferU = image.planes[1].buffer
        val bufferV = image.planes[2].buffer

        val rowLength = chromaRowStride
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        val uBytes = ByteArray(bufferU.remaining())
        val vBytes = ByteArray(bufferV.remaining())
        bufferU.get(uBytes)
        bufferV.get(vBytes)

        // Interleave V y U
        var vuIndex = 0
        for (row in 0 until chromaHeight) {
            val rowStartU = row * chromaRowStride
            val rowStartV = row * chromaRowStride
            for (col in 0 until chromaWidth) {
                val u = uBytes[rowStartU + col * chromaPixelStride]
                val v = vBytes[rowStartV + col * chromaPixelStride]
                nv21[offset + vuIndex++] = v
                nv21[offset + vuIndex++] = u
            }
        }
        return nv21
    }

    fun destroy() {
        inputAllocation?.destroy()
        outputAllocation?.destroy()
        scriptYuvToRgb?.destroy()
        rs?.destroy()
    }
}
