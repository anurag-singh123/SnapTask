package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.min

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { exception -> cont.resumeWithException(exception) }
    }

class OcrProcessor(private val context: Context) {

    // Bundled ML Kit recognizer instance (offline), lazily initialized when OCR runs
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadAndScaleBitmap(uri)
                ?: return@withContext Result.failure(Exception("Unable to decode bitmap from URI"))

            // If bitmap is very tall (scrolling screenshot), tile it
            val fullText = if (bitmap.height > 3000 && bitmap.height > bitmap.width * 2) {
                processTiledBitmap(bitmap)
            } else {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).awaitTask()
                visionText.text
            }

            Result.success(fullText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun processTiledBitmap(bitmap: Bitmap): String {
        val maxTileHeight = 2048
        val totalHeight = bitmap.height
        val width = bitmap.width
        val tilesCount = ceil(totalHeight.toDouble() / maxTileHeight).toInt()
        val textBuilder = StringBuilder()

        for (i in 0 until tilesCount) {
            val startY = i * maxTileHeight
            val tileHeight = min(maxTileHeight, totalHeight - startY)
            if (tileHeight <= 0) break

            val tileBitmap = Bitmap.createBitmap(bitmap, 0, startY, width, tileHeight)
            val inputImage = InputImage.fromBitmap(tileBitmap, 0)
            try {
                val tileResult = recognizer.process(inputImage).awaitTask()
                if (tileResult.text.isNotBlank()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                    textBuilder.append(tileResult.text)
                }
            } finally {
                if (tileBitmap != bitmap) {
                    tileBitmap.recycle()
                }
            }
        }
        return textBuilder.toString()
    }

    private fun loadAndScaleBitmap(uri: Uri): Bitmap? {
        val cr = context.contentResolver

        // 1. First measure dimensions
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        cr.openInputStream(uri)?.use { stream: InputStream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // Downscale very large images: target max width ~1440px
        var inSampleSize = 1
        if (origWidth > 1440) {
            inSampleSize = (origWidth / 1440)
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return cr.openInputStream(uri)?.use { stream: InputStream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }
}
