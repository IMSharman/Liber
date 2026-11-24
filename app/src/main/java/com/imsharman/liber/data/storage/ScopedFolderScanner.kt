package com.imsharman.liber.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.imsharman.liber.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredBookFile(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val format: BookFormat,
    val relativeFolderPath: String
)

/**
 * Strictly scans ONLY folders explicitly granted by the user via Android SAF (`OpenDocumentTree`),
 * and strictly returns recognized book formats (`BookFormat.fromFileName != null`).
 *
 * Standalone images (`cover.jpg`, `.png`, `.webp`, photos, etc.) are unconditionally skipped.
 */
class ScopedFolderScanner(private val context: Context) {

    suspend fun scanGrantedTree(treeUri: Uri): Pair<String, List<DiscoveredBookFile>> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext ("Folder" to emptyList())
            val rootName = root.name?.takeIf { it.isNotBlank() } ?: "Books Folder"
            val results = mutableListOf<DiscoveredBookFile>()
            walkDirectory(dir = root, relativePrefix = "", sink = results, depth = 0)
            rootName to results
        }

    private fun walkDirectory(
        dir: DocumentFile,
        relativePrefix: String,
        sink: MutableList<DiscoveredBookFile>,
        depth: Int
    ) {
        if (depth > 8) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue // Skip hidden/system directories and .nomedia
            if (child.isDirectory) {
                val subPath = if (relativePrefix.isEmpty()) name else "$relativePrefix/$name"
                walkDirectory(child, subPath, sink, depth + 1)
            } else if (child.isFile) {
                val format = shouldAcceptCandidateFile(name) ?: continue
                sink += DiscoveredBookFile(
                    uri = child.uri,
                    fileName = name,
                    fileSizeBytes = child.length().coerceAtLeast(0L),
                    format = format,
                    relativeFolderPath = relativePrefix
                )
            }
        }
    }

    companion object {
        /**
         * Pure filter function (also unit-tested) verifying that images and unrelated files
         * are never ingested by Liber.
         */
        fun shouldAcceptCandidateFile(fileName: String): BookFormat? {
            return BookFormat.fromFileName(fileName)
        }
    }
}
