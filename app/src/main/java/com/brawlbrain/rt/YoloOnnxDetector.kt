package com.brawlbrain.rt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class YoloOnnxDetector(
    private val modelBytes: ByteArray,
    private val labels: Array<String>,
    private val confidenceThreshold: Float,
    private val iouThreshold: Float = 0.45f
) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private data class SessionBundle(val session: OrtSession, val backend: String)

    private val initialBundle = createSession(preferNnapi = true)
    private var session = initialBundle.session
    @Volatile var backend: String = initialBundle.backend
        private set
    @Volatile var lastError: String = ""
        private set
    @Volatile var lastDetectionCount: Int = 0
        private set

    private val inputName: String
    private val inputSize: Int

    init {
        inputName = session.inputNames.first()
        val info = session.inputInfo[inputName]?.info as? TensorInfo
        val shape = info?.shape ?: longArrayOf(1, 3, 640, 640)
        inputSize = (shape.lastOrNull { it > 0 }?.toInt() ?: 640).coerceIn(320, 640)
    }

    private fun createSession(
        preferNnapi: Boolean,
        preferXnnpack: Boolean = true
    ): SessionBundle {
        if (preferNnapi) {
            try {
                val options = OrtSession.SessionOptions()
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                options.addNnapi()
                return SessionBundle(
                    env.createSession(modelBytes, options),
                    "NNAPI"
                )
            } catch (_: Throwable) {
            }
        }

        try {
                val options = OrtSession.SessionOptions()
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                options.addXnnpack(emptyMap())
                return SessionBundle(
                    env.createSession(modelBytes, options),
                    "XNNPACK"
                )
            } catch (_: Throwable) {
                val options = OrtSession.SessionOptions()
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                return SessionBundle(
                    env.createSession(modelBytes, options),
                    "CPU"
                )
            }
        }
    }

    @Synchronized
    fun detect(source: Bitmap): List<Detection> {
        val prepared = letterbox(source)
        val data = toTensorData(prepared.bitmap)
        prepared.bitmap.recycle()

        return try {
            OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(data),
                longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { result ->
                    val output = result.get(0) as? OnnxTensor ?: return emptyList()
                    val info = output.info as? TensorInfo ?: return emptyList()
                    val buffer = output.floatBuffer ?: return emptyList()

                    val detections = YoloParser.parse(
                        buffer,
                        info.shape,
                        labels,
                        source.width.toFloat(),
                        source.height.toFloat(),
                        prepared,
                        confidenceThreshold,
                        iouThreshold
                    )
                    lastError = ""
                    lastDetectionCount = detections.size
                    detections
                }
            }
        } catch (t: Throwable) {
            lastError = backend + ": " + t.javaClass.simpleName + ": " +
                (t.message ?: "inference failed")
            lastDetectionCount = 0

            if (backend != "CPU") {
                try {
                    session.close()
                } catch (_: Throwable) {
                }

                return try {
                    val fallback = createSession(
                        preferNnapi = false,
                        preferXnnpack = backend == "NNAPI"
                    )
                    session = fallback.session
                    backend = fallback.backend
                    lastError = "fallback -> " + backend
                    detect(source)
                } catch (fallbackError: Throwable) {
                    lastError = "fallback failed: " +
                        fallbackError.javaClass.simpleName + ": " +
                        (fallbackError.message ?: "unknown")
                    emptyList()
                }
            }

            emptyList()
        }
    }

    private fun toTensorData(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val plane = inputSize * inputSize
        val data = FloatArray(plane * 3)

        for (i in pixels.indices) {
            val c = pixels[i]
            data[i] = Color.red(c) / 255f
            data[plane + i] = Color.green(c) / 255f
            data[plane * 2 + i] = Color.blue(c) / 255f
        }

        return data
    }

    private fun letterbox(source: Bitmap): LetterboxResult {
        val scale = min(
            inputSize.toFloat() / source.width,
            inputSize.toFloat() / source.height
        )

        val newW = (source.width * scale).toInt().coerceAtLeast(1)
        val newH = (source.height * scale).toInt().coerceAtLeast(1)
        val padX = (inputSize - newW) / 2f
        val padY = (inputSize - newH) / 2f

        val out = Bitmap.createBitmap(
            inputSize,
            inputSize,
            Bitmap.Config.ARGB_8888
        )

        Canvas(out).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(
                source,
                null,
                RectF(padX, padY, padX + newW, padY + newH),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }

        return LetterboxResult(out, scale, padX, padY)
    }

    override fun close() {
        try {
            session.close()
        } catch (_: Throwable) {
        }
    }

    data class LetterboxResult(
        val bitmap: Bitmap,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )
}

private object YoloParser {
    fun parse(
        buffer: FloatBuffer,
        shape: LongArray,
        labels: Array<String>,
        sourceWidth: Float,
        sourceHeight: Float,
        meta: YoloOnnxDetector.LetterboxResult,
        confidenceThreshold: Float,
        iouThreshold: Float
    ): List<Detection> {
        if (shape.size < 3) return emptyList()

        val a = shape[1].toInt()
        val b = shape[2].toInt()
        val channelsFirst = a <= b
        val channels = if (channelsFirst) a else b
        val count = if (channelsFirst) b else a
        if (channels < 5 || count <= 0) return emptyList()

        val raw = FloatArray(buffer.remaining())
        buffer.get(raw)

        fun at(row: Int, col: Int): Float {
            return if (channelsFirst) raw[col * count + row]
            else raw[row * channels + col]
        }

        val candidates = ArrayList<Detection>(24)
        val classCount = min(labels.size, channels - 4)

        for (i in 0 until count) {
            val cx = at(i, 0)
            val cy = at(i, 1)
            val bw = at(i, 2)
            val bh = at(i, 3)

            var bestClass = -1
            var bestScore = 0f

            for (c in 0 until classCount) {
                var score = at(i, 4 + c)
                if (score < 0f || score > 1f) score = sigmoid(score)

                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            if (bestClass < 0 || bestScore < confidenceThreshold) continue

            val x1 = ((cx - bw / 2f) - meta.padX) / meta.scale
            val y1 = ((cy - bh / 2f) - meta.padY) / meta.scale
            val x2 = ((cx + bw / 2f) - meta.padX) / meta.scale
            val y2 = ((cy + bh / 2f) - meta.padY) / meta.scale

            val left = (x1 / sourceWidth).coerceIn(0f, 1f)
            val top = (y1 / sourceHeight).coerceIn(0f, 1f)
            val right = (x2 / sourceWidth).coerceIn(0f, 1f)
            val bottom = (y2 / sourceHeight).coerceIn(0f, 1f)

            candidates += Detection(
                labels[bestClass],
                bestScore,
                (left + right) / 2f,
                (top + bottom) / 2f,
                (right - left).coerceIn(0f, 1f),
                (bottom - top).coerceIn(0f, 1f)
            )
        }

        return nms(candidates, iouThreshold).take(10)
    }

    private fun nms(input: List<Detection>, threshold: Float): List<Detection> {
        val output = ArrayList<Detection>()
        val remaining = input.sortedByDescending { it.confidence }.toMutableList()

        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            output += best
            remaining.removeAll {
                it.label == best.label && iou(best, it) >= threshold
            }
        }
        return output
    }

    private fun iou(a: Detection, b: Detection): Float {
        val ax1 = a.cx - a.width / 2f
        val ay1 = a.cy - a.height / 2f
        val ax2 = a.cx + a.width / 2f
        val ay2 = a.cy + a.height / 2f
        val bx1 = b.cx - b.width / 2f
        val by1 = b.cy - b.height / 2f
        val bx2 = b.cx + b.width / 2f
        val by2 = b.cy + b.height / 2f
        val iw = max(0f, min(ax2, bx2) - max(ax1, bx1))
        val ih = max(0f, min(ay2, by2) - max(ay1, by1))
        val intersection = iw * ih
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun sigmoid(x: Float): Float {
        return if (x >= 0f) {
            1f / (1f + kotlin.math.exp(-x))
        } else {
            val e = kotlin.math.exp(x)
            e / (1f + e)
        }
    }
}
