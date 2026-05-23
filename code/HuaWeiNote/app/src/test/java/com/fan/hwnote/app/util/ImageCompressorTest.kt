package com.fan.hwnote.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class ImageCompressorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val tmpDir: File get() = File(context.cacheDir, "compressor_test").apply { mkdirs() }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun writeSolidColor(w: Int, h: Int, file: File) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bmp.recycle()
    }

    @Test
    fun `short edge image is written without scaling`() {
        val src = File(tmpDir, "src_small.jpg")
        writeSolidColor(800, 600, src)
        val dst = File(tmpDir, "dst_small.jpg")

        val out = ImageCompressor.compressToFile(context, android.net.Uri.fromFile(src), dst)

        assertNotNull(out)
        assertEquals(800, out!!.width)
        assertEquals(600, out.height)
        assertTrue(dst.exists() && dst.length() > 0)
    }

    @Test
    fun `long edge image is scaled to 1920`() {
        val src = File(tmpDir, "src_large.jpg")
        writeSolidColor(4000, 3000, src)
        val dst = File(tmpDir, "dst_large.jpg")

        val out = ImageCompressor.compressToFile(context, android.net.Uri.fromFile(src), dst)

        assertNotNull(out)
        assertEquals(1920, out!!.width)
        assertEquals(1440, out.height) // 4000:3000 → 1920:1440
        assertTrue(dst.exists())
    }

    @Test
    fun `invalid uri returns null`() {
        val dst = File(tmpDir, "dst_bad.jpg")
        val out = ImageCompressor.compressToFile(
            context, android.net.Uri.parse("file:///nonexistent.jpg"), dst,
        )
        assertNull(out)
    }
}
