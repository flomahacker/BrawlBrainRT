package com.brawlbrain.rt.m1

import java.nio.ByteBuffer

object NativeDetector {
    const val MAX_DETECTIONS = 64
    const val FLOATS_PER_DETECTION = 6
    const val OUTPUT_BYTES = MAX_DETECTIONS * FLOATS_PER_DETECTION * Float.SIZE_BYTES

    init {
        System.loadLibrary("brawlcapture")
    }

    @JvmStatic
    external fun analyze(
        rgba: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        output: ByteBuffer
    ): Int
}
