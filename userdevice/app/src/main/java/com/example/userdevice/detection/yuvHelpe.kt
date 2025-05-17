package com.example.userdevice

import org.webrtc.VideoFrame

object YuvHelper {
    fun i420ToNv21(i420Buffer: VideoFrame.I420Buffer): ByteArray {
        val width = i420Buffer.width
        val height = i420Buffer.height
        val ySize = width * height
        val uvSize = width / 2 * height / 2

        val nv21 = ByteArray(ySize + 2 * uvSize)

        val yBuffer = i420Buffer.dataY
        val uBuffer = i420Buffer.dataU
        val vBuffer = i420Buffer.dataV
        val yStride = i420Buffer.strideY
        val uStride = i420Buffer.strideU
        val vStride = i420Buffer.strideV

        var pos = 0

        // Copy Y
        for (i in 0 until height) {
            yBuffer.position(i * yStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }

        // Interleave V and U to NV21 format (VU)
        for (i in 0 until height / 2) {
            uBuffer.position(i * uStride)
            vBuffer.position(i * vStride)
            for (j in 0 until width / 2) {
                nv21[pos++] = vBuffer.get()
                nv21[pos++] = uBuffer.get()
            }
        }

        return nv21
    }
}
