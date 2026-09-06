package com.muxiao.timart.utils.format

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * 图片解码统一出口（信笺图片与纪念海报共用）：
 * BitmapFactory 不读 EXIF——手机拍摄/部分截图带旋转标记的字节直接解码会横竖颠倒，
 * 此处按 EXIF Orientation 补矩阵旋转，保证显示与海报中的图片方向与相册一致。
 * 解码失败返回 null（不抛出）。
 */
object ImageDecode {

    /** 解码字节并按 EXIF 方向摆正（长边按 [maxDimension] 降采样，0 = 不限制） */
    fun decodeOriented(bytes: ByteArray, maxDimension: Int = 0): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = downsampleScale(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
        applyExifRotation(raw, bytes)
    }.getOrNull()

    /** 降采样倍数：目标为长边不超过 maxDimension（0/负值返回 1） */
    private fun downsampleScale(width: Int, height: Int, maxDimension: Int): Int {
        if (maxDimension <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }

    /** 按 EXIF Orientation 旋转位图（无标记时原样返回） */
    private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ByteArrayInputStream(bytes).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> 0f
        }
        val flip = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        if (degrees == 0f && !flip) return bitmap

        val matrix = Matrix()
        matrix.postRotate(degrees)
        if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        ) {
            matrix.postScale(-1f, 1f)
        } else if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE
        ) {
            matrix.postScale(1f, -1f)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
