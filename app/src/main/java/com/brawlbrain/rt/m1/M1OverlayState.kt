package com.brawlbrain.rt.m1

import java.nio.ByteBuffer
import kotlin.math.min

class M1OverlayState(
    private val maxDetections: Int = NativeDetector.MAX_DETECTIONS
) {
    val types = IntArray(maxDetections)
    val confidence = FloatArray(maxDetections)
    val left = FloatArray(maxDetections)
    val top = FloatArray(maxDetections)
    val right = FloatArray(maxDetections)
    val bottom = FloatArray(maxDetections)

    var detectionCount: Int = 0
        private set
    var pipelineFps: Float = 0f
        private set
    var captureAgeMs: Float = 0f
        private set
    var processMs: Float = 0f
        private set
    var thermalStatus: Int = 0
        private set
    var sourceWidth: Int = 640
        private set
    var sourceHeight: Int = 288
        private set

    @Synchronized
    fun update(
        output: ByteBuffer,
        count: Int,
        fps: Float,
        captureAge: Float,
        processing: Float,
        thermal: Int,
        width: Int,
        height: Int
    ) {
        detectionCount = min(count.coerceAtLeast(0), maxDetections)
        pipelineFps = fps
        captureAgeMs = captureAge
        processMs = processing
        thermalStatus = thermal
        sourceWidth = width
        sourceHeight = height

        for (i in 0 until detectionCount) {
            val base = i * NativeDetector.FLOATS_PER_DETECTION * Float.SIZE_BYTES
            types[i] = output.getFloat(base).toInt()
            confidence[i] = output.getFloat(base + 4)
            left[i] = output.getFloat(base + 8)
            top[i] = output.getFloat(base + 12)
            right[i] = output.getFloat(base + 16)
            bottom[i] = output.getFloat(base + 20)
        }
    }
}
