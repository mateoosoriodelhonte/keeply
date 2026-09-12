package app.keeply.imaging

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.opencv_core.Mat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte

/**
 * Conversion between Java's images and OpenCV's.
 *
 * Written by hand rather than pulled from JavaCV, which would add a dependency
 * several times the size of this file for one conversion each way.
 *
 * Mats hold memory outside the JVM heap, so every one of them has to be closed.
 * The helpers here take care of that for callers.
 */
internal object Mats {

    /** Copies a [BufferedImage] into an OpenCV matrix in BGR order. */
    fun fromImage(image: BufferedImage): Mat {
        val bgr = if (image.type == BufferedImage.TYPE_3BYTE_BGR) {
            image
        } else {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_3BYTE_BGR).also { converted ->
                converted.createGraphics().apply {
                    drawImage(image, 0, 0, null)
                    dispose()
                }
            }
        }
        val pixels = (bgr.raster.dataBuffer as DataBufferByte).data
        val mat = Mat(bgr.height, bgr.width, opencv_core.CV_8UC3)
        mat.data().put(pixels, 0, pixels.size)
        return mat
    }

    /** Copies an OpenCV matrix back into an image, handling one and three channel input. */
    fun toImage(mat: Mat): BufferedImage {
        val channels = mat.channels()
        require(channels == 1 || channels == 3) { "Expected a grey or BGR image, got $channels channels" }

        val width = mat.cols()
        val height = mat.rows()
        val bytes = ByteArray(width * height * channels)
        readInto(mat, bytes)

        return if (channels == 1) {
            BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY).also { image ->
                (image.raster.dataBuffer as DataBufferByte).data.let { target ->
                    bytes.copyInto(target)
                }
            }
        } else {
            BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR).also { image ->
                (image.raster.dataBuffer as DataBufferByte).data.let { target ->
                    bytes.copyInto(target)
                }
            }
        }
    }

    /**
     * Reads a matrix row by row.
     *
     * OpenCV pads rows for alignment, so a matrix is not always a contiguous block
     * and copying `rows * cols * channels` bytes straight out would shear the image.
     */
    private fun readInto(mat: Mat, target: ByteArray) {
        val rowBytes = mat.cols() * mat.channels()
        val step = mat.step().toInt()
        val pointer: BytePointer = mat.data()
        if (step == rowBytes) {
            pointer.get(target, 0, target.size)
            return
        }
        val row = ByteArray(rowBytes)
        for (y in 0 until mat.rows()) {
            pointer.position(y.toLong() * step).get(row, 0, rowBytes)
            row.copyInto(target, y * rowBytes)
        }
        pointer.position(0)
    }

    /** Runs [block] with a matrix built from [image] and always releases it afterwards. */
    inline fun <T> withMat(image: BufferedImage, block: (Mat) -> T): T {
        val mat = fromImage(image)
        return try {
            block(mat)
        } finally {
            mat.close()
        }
    }
}

/** Closes every matrix in the list, even if one of them throws. */
internal fun List<Mat>.closeAll() {
    forEach { runCatching { it.close() } }
}
