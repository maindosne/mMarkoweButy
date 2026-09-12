package pl.mmarkowebuty.admin.batch

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.random.Random

data class ProcessedPhoto(
    val jpeg: ByteArray,
    val labels: List<String>,
    val colorName: String,
)

class BatchPhotoProcessor(private val context: Context) : AutoCloseable {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.50f).build()
    )
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
    )

    suspend fun process(uri: Uri): ProcessedPhoto {
        val original = decode(uri)
        val input = InputImage.fromBitmap(original, 0)
        val labels = runCatching {
            labeler.process(input).await()
                .sortedByDescending { it.confidence }
                .take(12)
                .map { it.text.lowercase() }
        }.getOrDefault(emptyList())

        val foreground = foregroundWithRetry(input)
        val color = dominantColorName(foreground)
        val studio = renderStudio(foreground)
        val bytes = compress(studio)

        if (original !== foreground && !original.isRecycled) original.recycle()
        if (foreground !== studio && !foreground.isRecycled) foreground.recycle()
        if (!studio.isRecycled) studio.recycle()

        return ProcessedPhoto(bytes, labels, color)
    }

    private suspend fun foregroundWithRetry(input: InputImage): Bitmap {
        var lastError: Throwable? = null
        repeat(12) { attempt ->
            try {
                val result = segmenter.process(input).await()
                result.foregroundBitmap?.let { return it.copy(Bitmap.Config.ARGB_8888, false) }
            } catch (e: Throwable) {
                lastError = e
            }
            if (attempt < 11) delay(2_500)
        }
        throw IllegalStateException(
            "Nie udało się uruchomić lokalnego modelu wycinania tła. Sprawdź internet i Usługi Google Play, odczekaj chwilę i spróbuj ponownie.",
            lastError
        )
    }

    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val largest = maxOf(w, h)
            if (largest > 2000) {
                val scale = 2000.0 / largest
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun alphaCrop(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                if (Color.alpha(pixels[y * w + x]) > 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += 2
            }
            y += 2
        }
        if (maxX < minX || maxY < minY) throw IllegalStateException("Nie wykryto butów na zdjęciu.")
        val padX = ((maxX - minX) * 0.035f).toInt().coerceAtLeast(8)
        val padY = ((maxY - minY) * 0.035f).toInt().coerceAtLeast(8)
        val left = (minX - padX).coerceAtLeast(0)
        val top = (minY - padY).coerceAtLeast(0)
        val right = (maxX + padX).coerceAtMost(w - 1)
        val bottom = (maxY + padY).coerceAtMost(h - 1)
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
    }

    private fun renderStudio(foreground: Bitmap): Bitmap {
        val crop = alphaCrop(foreground)
        val outSize = 1600
        val result = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, outSize.toFloat(),
                intArrayOf(Color.rgb(232, 234, 235), Color.rgb(207, 210, 211), Color.rgb(188, 191, 192)),
                floatArrayOf(0f, 0.63f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, outSize.toFloat(), outSize.toFloat(), background)

        val horizonY = 1040f
        val floor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, horizonY, 0f, outSize.toFloat(),
                Color.rgb(201, 204, 205), Color.rgb(179, 182, 183), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, horizonY, outSize.toFloat(), outSize.toFloat(), floor)

        val texture = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(9, 55, 58, 60) }
        val rnd = Random(1937)
        repeat(420) {
            val x = rnd.nextInt(outSize).toFloat()
            val y = rnd.nextInt(outSize).toFloat()
            canvas.drawCircle(x, y, if (it % 3 == 0) 1.4f else 0.8f, texture)
        }

        val maxW = 1320f
        val maxH = 1060f
        val scale = min(maxW / crop.width, maxH / crop.height)
        val drawW = (crop.width * scale).toInt().coerceAtLeast(1)
        val drawH = (crop.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(crop, drawW, drawH, true)
        val left = (outSize - drawW) / 2f
        val bottom = 1295f
        val top = bottom - drawH

        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(34f, BlurMaskFilter.Blur.NORMAL)
        }
        val shadowOffset = IntArray(2)
        val shadow = scaled.extractAlpha(blurPaint, shadowOffset)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 58
        }
        canvas.drawBitmap(shadow, left + shadowOffset[0], top + shadowOffset[1] + 25f, shadowPaint)

        val reflectionMatrix = Matrix().apply { preScale(1f, -1f) }
        val reflection = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, reflectionMatrix, true)
        canvas.save()
        canvas.clipRect(0f, bottom + 6f, outSize.toFloat(), (bottom + 155f).coerceAtMost(outSize.toFloat()))
        canvas.drawBitmap(reflection, left, bottom + 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 22 })
        canvas.restore()

        canvas.drawBitmap(scaled, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        crop.recycle()
        scaled.recycle()
        shadow.recycle()
        reflection.recycle()
        return result
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        if (out.size() > 5 * 1024 * 1024) {
            out.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out)
        }
        return out.toByteArray().also {
            if (it.size > 5 * 1024 * 1024) throw IllegalStateException("Gotowe zdjęcie jest zbyt duże do wysłania.")
        }
    }

    private fun dominantColorName(bitmap: Bitmap): String {
        val counts = linkedMapOf<String, Int>()
        val step = maxOf(3, min(bitmap.width, bitmap.height) / 180)
        val hsv = FloatArray(3)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) > 90) {
                    Color.colorToHSV(c, hsv)
                    val h = hsv[0]
                    val s = hsv[1]
                    val v = hsv[2]
                    val name = when {
                        v < 0.20f -> "czarne"
                        v > 0.88f && s < 0.16f -> "białe"
                        s < 0.12f -> "szare"
                        h in 24f..55f && s < 0.48f && v > 0.48f -> "beżowe"
                        (h < 14f || h >= 345f) && s > 0.35f -> "czerwone"
                        h in 14f..<35f && s > 0.28f && v < 0.72f -> "brązowe"
                        h in 14f..<42f -> "pomarańczowe"
                        h in 42f..<70f -> "żółte"
                        h in 70f..<170f -> "zielone"
                        h in 170f..<255f -> "niebieskie"
                        h in 255f..<315f -> "fioletowe"
                        h in 315f..<345f -> "różowe"
                        else -> "wielokolorowe"
                    }
                    counts[name] = (counts[name] ?: 0) + 1
                }
                x += step
            }
            y += step
        }
        return counts.maxByOrNull { it.value }?.key ?: ""
    }

    override fun close() {
        labeler.close()
        segmenter.close()
    }

    companion object {
        fun categoryFromLabels(labels: Collection<String>): String {
            val text = labels.joinToString(" ").lowercase()
            return when {
                listOf("high heels", "high-heeled", "heel shoe").any(text::contains) -> "buty na obcasie"
                listOf("sandal").any(text::contains) -> "sandały"
                listOf("flip-flop", "slipper", "slide sandal").any(text::contains) -> "klapki"
                listOf("sneaker", "running shoe", "athletic shoe", "trainer").any(text::contains) -> "sneakersy"
                listOf("loafer").any(text::contains) -> "mokasyny"
                listOf("oxford shoe", "dress shoe").any(text::contains) -> "półbuty"
                listOf("boot", "winter footwear").any(text::contains) -> "buty"
                else -> "buty"
            }
        }
    }
}
