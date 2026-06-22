package com.heart.app.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Extracts the mean red-channel intensity over a centered ROI from each RGBA_8888 frame.
 * With the torch on and a fingertip covering the lens, the red channel carries the
 * strongest pulsatile (PPG) component.
 *
 * @param onSample (timestampMs, meanRed[0..255], fingerPresent)
 */
class RedChannelAnalyzer(
    private val onSample: (Long, Double, Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            // ImageAnalysis is configured for RGBA_8888 output, so plane[0] is packed RGBA.
            // Guard only against an unexpectedly empty plane list.
            if (image.planes.isEmpty()) return
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height

            // Center 50% ROI, subsampled by 2 for throughput.
            val x0 = width / 4
            val x1 = width * 3 / 4
            val y0 = height / 4
            val y1 = height * 3 / 4

            var sumR = 0L
            var count = 0L
            var y = y0
            while (y < y1) {
                val rowStart = y * rowStride
                var x = x0
                while (x < x1) {
                    val idx = rowStart + x * pixelStride
                    if (idx in 0 until buffer.limit()) {
                        val r = buffer.get(idx).toInt() and 0xFF
                        sumR += r
                        count++
                    }
                    x += 2
                }
                y += 2
            }

            val meanR = if (count > 0) sumR.toDouble() / count else 0.0
            // Finger present ⇒ bright red but not fully saturated white.
            val present = meanR in 90.0..252.0
            val tMs = image.imageInfo.timestamp / 1_000_000L
            onSample(tMs, meanR, present)
        } catch (_: Throwable) {
            // Never let a bad frame crash the analysis pipeline.
        } finally {
            image.close()
        }
    }
}
