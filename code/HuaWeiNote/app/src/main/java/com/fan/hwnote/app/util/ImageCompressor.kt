package com.fan.hwnote.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 解码任意来源 URI（gallery / FileProvider / file://），长边压到 ≤ MAX_EDGE，JPEG quality 85 写出。
 * 返回 Result(width, height)，失败返回 null。
 */
object ImageCompressor {

    private const val MAX_EDGE = 1920
    private const val JPEG_QUALITY = 85

    data class Result(val width: Int, val height: Int)

    fun compressToFile(context: Context, source: Uri, target: File): Result? {
        val cr = context.contentResolver
        // 1) 只读 bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2) 算 inSampleSize（让短边解码后 ≥ MAX_EDGE/2，避免一次到位失真）
        val sampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded: Bitmap = runCatching {
            cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return null

        // 3) 长边 > MAX_EDGE 时再用 Matrix 精确缩放
        val finalBitmap = scaleIfNeeded(decoded, MAX_EDGE)
        if (finalBitmap !== decoded) decoded.recycle()

        // 4) JPEG 85 写出
        val written = runCatching {
            FileOutputStream(target).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }.getOrDefault(false)
        val w = finalBitmap.width; val h = finalBitmap.height
        finalBitmap.recycle()

        return if (written) Result(w, h) else null
    }

    private fun calcInSampleSize(width: Int, height: Int, reqEdge: Int): Int {
        val longEdge = maxOf(width, height)
        var sample = 1
        while (longEdge / sample > reqEdge * 2) sample *= 2
        return sample
    }

    private fun scaleIfNeeded(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge
        val m = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
