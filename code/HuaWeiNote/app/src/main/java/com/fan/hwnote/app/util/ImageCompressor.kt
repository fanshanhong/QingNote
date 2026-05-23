package com.fan.hwnote.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
        try {
            cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: IOException) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2) 算 inSampleSize（保证解码后长边仍 > MAX_EDGE，便于后续 Matrix 缩放精准命中 MAX_EDGE 而不会反向放大）
        val sampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded: Bitmap = try {
            cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: IOException) {
            null
        } ?: return null

        // 3) 长边 > MAX_EDGE 时再用 Matrix 精确缩放；OOM 时回收源位图后退出
        val finalBitmap = try {
            scaleIfNeeded(decoded, MAX_EDGE)
        } catch (oom: OutOfMemoryError) {
            decoded.recycle()
            return null
        }
        if (finalBitmap !== decoded) decoded.recycle()

        // 4) JPEG 85 写出
        val written = try {
            FileOutputStream(target).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } catch (e: IOException) {
            false
        }
        val w = finalBitmap.width
        val h = finalBitmap.height
        finalBitmap.recycle()

        return if (written) {
            Result(w, h)
        } else {
            // 写出失败时清理半成品目标文件，避免外部看到残留
            if (target.exists()) target.delete()
            null
        }
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
