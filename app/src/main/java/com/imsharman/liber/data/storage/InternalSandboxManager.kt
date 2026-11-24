package com.imsharman.liber.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SandboxAuditInfo(
    val coverCount: Int,
    val coverBytes: Long,
    val customFontCount: Int,
    val bookAssetCacheBytes: Long
)

/**
 * Manages Liber's strictly private internal sandbox directories (`context.filesDir` and `context.cacheDir`).
 *
 * PRIVACY GUARANTEE:
 * - Never creates folders on `/sdcard` or public external storage (unlike ReadEra's external `Covers` folder).
 * - Never scans or reads standalone image files from the user's device unless the user explicitly picks
 *   a single image in the Metadata Editor to set as a custom cover.
 */
class InternalSandboxManager(private val context: Context) {

    val coversDir: File
        get() = File(context.filesDir, "covers").apply { mkdirs() }

    val fontsDir: File
        get() = File(context.filesDir, "fonts").apply { mkdirs() }

    val samplesDir: File
        get() = File(context.filesDir, "samples").apply { mkdirs() }

    val bookAssetsCacheDir: File
        get() = File(context.cacheDir, "book_assets").apply { mkdirs() }

    val exportsCacheDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun saveCoverBytes(rawBytes: ByteArray, preferredKey: String = UUID.randomUUID().toString()): String? =
        withContext(Dispatchers.IO) {
            if (rawBytes.isEmpty()) return@withContext null
            runCatching {
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@withContext null
                saveCoverBitmapInternal(decoded, preferredKey)
            }.getOrNull()
        }

    suspend fun saveCoverBitmap(bitmap: Bitmap, preferredKey: String = UUID.randomUUID().toString()): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                saveCoverBitmapInternal(bitmap, preferredKey)
            }.getOrNull()
        }

    private fun saveCoverBitmapInternal(bitmap: Bitmap, preferredKey: String): String {
        val maxHeight = 720
        val scaled = if (bitmap.height > maxHeight && bitmap.height > 0) {
            val ratio = maxHeight.toFloat() / bitmap.height.toFloat()
            val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetWidth, maxHeight, true)
        } else {
            bitmap
        }

        val safeName = preferredKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
        val outFile = File(coversDir, "${safeName}_${System.currentTimeMillis()}.webp")
        FileOutputStream(outFile).use { out ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            scaled.compress(format, 86, out)
        }
        return outFile.absolutePath
    }

    /**
     * Used ONLY when the user explicitly selects a custom cover image in the Metadata Editor.
     */
    suspend fun importExplicitCustomCover(imageUri: Uri, bookId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: return@withContext null
            saveCoverBytes(bytes, "custom_book_$bookId")
        }.getOrNull()
    }

    /**
     * Used ONLY when the user explicitly imports a `.ttf` or `.otf` font file in Reader Settings.
     */
    suspend fun importExplicitFontFile(fontUri: Uri, displayName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "custom_font.ttf" }
            val dest = File(fontsDir, cleanName)
            context.contentResolver.openInputStream(fontUri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            dest
        }.getOrNull()
    }

    fun saveInternalBookAsset(bookKey: String, entryName: String, bytes: ByteArray): String? {
        return runCatching {
            val dir = File(bookAssetsCacheDir, bookKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")).apply { mkdirs() }
            val cleanEntry = entryName.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "img_${UUID.randomUUID()}.png" }
            val target = File(dir, cleanEntry)
            target.writeBytes(bytes)
            target.absolutePath
        }.getOrNull()
    }

    fun deleteInternalCover(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists() && file.canonicalPath.startsWith(coversDir.canonicalPath)) {
                file.delete()
            }
        }
    }

    suspend fun clearAllInternalCovers() = withContext(Dispatchers.IO) {
        coversDir.listFiles()?.forEach { it.delete() }
        bookAssetsCacheDir.deleteRecursively()
        bookAssetsCacheDir.mkdirs()
    }

    suspend fun getAuditInfo(): SandboxAuditInfo = withContext(Dispatchers.IO) {
        val coverFiles = coversDir.listFiles().orEmpty()
        val fontFiles = fontsDir.listFiles().orEmpty()
        val assetBytes = bookAssetsCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        SandboxAuditInfo(
            coverCount = coverFiles.size,
            coverBytes = coverFiles.sumOf { it.length() },
            customFontCount = fontFiles.size,
            bookAssetCacheBytes = assetBytes
        )
    }

    companion object {
        private val EDITORIAL_COVER_PALETTE = longArrayOf(
            0xFF233D4D,
            0xFF5C374C,
            0xFF2F5233,
            0xFF6B4226,
            0xFF2B3A67,
            0xFF4A314D,
            0xFF1F4E5F,
            0xFF5E4B3B
        )

        fun deterministicCoverColor(seed: String): Long {
            val hash = (seed.hashCode() and Int.MAX_VALUE)
            return EDITORIAL_COVER_PALETTE[hash % EDITORIAL_COVER_PALETTE.size]
        }
    }
}
