package com.imsharman.liber.data.parser

import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Pure-Kotlin Mobipocket / Kindle (`.mobi`, `.prc`, `.azw`, `.azw3` / KF8) parser.
 * Implements:
 * - PalmDB record directory reader
 * - PalmDOC header & LZ77 byte-pair decompression (`compression == 1` uncompressed, `compression == 2` PalmDOC LZ77)
 * - MOBI header (`MOBI` magic) text encoding (`1252` CP1252, `65001` UTF-8), full title, first image index
 * - EXTH metadata records (100 Author, 101 Publisher, 103 Description, 104 ISBN, 105 Subject, 201 CoverOffset, 503 Title)
 * - Embedded cover extraction from PalmDB image record (`firstImageIndex + coverOffset`)
 * - Chapter splitting across `<mbp:pagebreak>` and top-level headings
 */
object MobiParser {

    fun parse(
        inputStream: InputStream,
        fallbackTitle: String,
        metadataOnly: Boolean = false
    ): LoadedBookContent.Reflowable {
        val data = inputStream.readBytes()
        if (data.size < 78) {
            return emptyFallback(fallbackTitle, "File is too small to be a valid MOBI/AZW document.")
        }

        // Some AZW3 files on Calibre exports are EPUB-like or contain raw HTML; check ZIP magic just in case
        if (data[0] == 0x50.toByte() && data[1] == 0x4B.toByte()) {
            return EpubParser.parseFullBook(data.inputStream(), fallbackTitle)
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val rawDbName = String(data, 0, 32, Charsets.ISO_8859_1).substringBefore('\u0000').trim()
        val numRecords = buf.getShort(76).toInt() and 0xFFFF
        if (numRecords <= 0 || 78 + numRecords * 8 > data.size) {
            return emptyFallback(fallbackTitle, "Invalid PalmDB record table in MOBI/AZW file.")
        }

        val recordOffsets = IntArray(numRecords)
        for (i in 0 until numRecords) {
            recordOffsets[i] = buf.getInt(78 + i * 8)
        }

        fun recordSlice(index: Int): ByteArray {
            if (index !in 0 until numRecords) return ByteArray(0)
            val start = recordOffsets[index].coerceIn(0, data.size)
            val end = if (index + 1 < numRecords) {
                recordOffsets[index + 1].coerceIn(start, data.size)
            } else {
                data.size
            }
            return data.copyOfRange(start, end)
        }

        val rec0 = recordSlice(0)
        if (rec0.size < 16) {
            return emptyFallback(fallbackTitle, "Missing PalmDOC header record.")
        }
        val rec0Buf = ByteBuffer.wrap(rec0).order(ByteOrder.BIG_ENDIAN)
        val compression = rec0Buf.getShort(0).toInt() and 0xFFFF
        val textRecordCount = (rec0Buf.getShort(8).toInt() and 0xFFFF).coerceAtMost(numRecords - 1)
        val extraDataFlags = if (rec0.size >= 244) (rec0Buf.getShort(242).toInt() and 0xFFFF) else 0

        var charset: Charset = Charset.forName("windows-1252")
        var fullTitle = rawDbName.ifBlank { fallbackTitle }
        var firstImageRecordIndex = -1
        val authors = mutableListOf<String>()
        var publisher = ""
        var description = ""
        var isbn = ""
        val tags = mutableListOf<String>()
        var coverRecordOffset: Int? = null

        val hasMobiMagic = rec0.size >= 24 && String(rec0, 16, 4, Charsets.US_ASCII) == "MOBI"
        if (hasMobiMagic) {
            val mobiHeaderLength = rec0Buf.getInt(20)
            val textEncoding = rec0Buf.getInt(28)
            if (textEncoding == 65001) {
                charset = Charsets.UTF_8
            }
            if (rec0.size >= 92) {
                val fullNameOffset = rec0Buf.getInt(84)
                val fullNameLen = rec0Buf.getInt(88)
                if (fullNameOffset >= 0 && fullNameLen > 0 && fullNameOffset + fullNameLen <= rec0.size) {
                    val parsedName = String(rec0, fullNameOffset, fullNameLen, charset).trim()
                    if (parsedName.isNotEmpty()) fullTitle = parsedName
                }
            }
            if (rec0.size >= 112) {
                firstImageRecordIndex = rec0Buf.getInt(108)
            }
            val exthFlags = if (rec0.size >= 132) rec0Buf.getInt(128) else 0
            val exthStart = 16 + mobiHeaderLength
            if ((exthFlags and 0x40) != 0 && exthStart + 12 <= rec0.size &&
                String(rec0, exthStart, 4, Charsets.US_ASCII) == "EXTH"
            ) {
                val exthRecordCount = rec0Buf.getInt(exthStart + 8)
                var pos = exthStart + 12
                for (r in 0 until exthRecordCount) {
                    if (pos + 8 > rec0.size) break
                    val recType = rec0Buf.getInt(pos)
                    val recLen = rec0Buf.getInt(pos + 4)
                    if (recLen < 8 || pos + recLen > rec0.size) break
                    val payload = rec0.copyOfRange(pos + 8, pos + recLen)
                    when (recType) {
                        100 -> authors += String(payload, charset).trim()
                        101 -> publisher = String(payload, charset).trim()
                        103 -> description = Jsoup.parse(String(payload, charset)).text().trim()
                        104 -> isbn = String(payload, charset).trim()
                        105 -> tags += String(payload, charset).trim()
                        201 -> if (payload.size == 4) {
                            coverRecordOffset = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).int
                        }
                        503 -> {
                            val updatedTitle = String(payload, charset).trim()
                            if (updatedTitle.isNotEmpty()) fullTitle = updatedTitle
                        }
                    }
                    pos += recLen
                }
            }
        }

        // Extract embedded cover bytes if available inside PalmDB image records
        var coverBytes: ByteArray? = null
        if (firstImageRecordIndex in 1 until numRecords) {
            val targetIdx = if (coverRecordOffset != null && coverRecordOffset!! >= 0) {
                firstImageRecordIndex + coverRecordOffset!!
            } else {
                firstImageRecordIndex
            }
            if (targetIdx in 1 until numRecords) {
                val candidate = recordSlice(targetIdx)
                if (looksLikeImage(candidate)) {
                    coverBytes = candidate
                }
            }
        }

        val authorText = authors.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "Unknown Author" }

        if (metadataOnly) {
            return LoadedBookContent.Reflowable(
                metadata = ParsedBookMetadata(
                    title = fullTitle,
                    author = authorText,
                    description = description,
                    publisher = publisher,
                    isbn = isbn,
                    tags = tags.filter { it.isNotEmpty() }.distinct(),
                    embeddedCoverBytes = coverBytes,
                    totalUnits = textRecordCount.coerceAtLeast(1)
                ),
                chapters = emptyList(),
                toc = emptyList()
            )
        }

        // Decompress text records
        if (compression == 17480) {
            // HUFF/CDIC DRM or high-compression dictionary
            return LoadedBookContent.Reflowable(
                metadata = ParsedBookMetadata(
                    title = fullTitle,
                    author = authorText,
                    description = description,
                    publisher = publisher,
                    isbn = isbn,
                    tags = tags,
                    embeddedCoverBytes = coverBytes,
                    totalUnits = 1
                ),
                chapters = listOf(
                    BookChapter(
                        index = 0,
                        title = fullTitle,
                        blocks = listOf(
                            ContentBlock.Paragraph(
                                "This Kindle/MOBI file uses proprietary HUFF/CDIC or DRM encryption that cannot be decoded without DRM removal."
                            )
                        )
                    )
                ),
                toc = listOf(TocEntry(title = fullTitle, chapterIndex = 0))
            )
        }

        val rawTextOut = ByteArrayOutputStream()
        for (recIdx in 1..textRecordCount) {
            val rawSlice = trimTrailingExtraBytes(recordSlice(recIdx), extraDataFlags)
            val decoded = when (compression) {
                1 -> rawSlice
                2 -> decompressPalmDocLz77(rawSlice)
                else -> rawSlice
            }
            rawTextOut.write(decoded)
        }

        val fullMarkup = String(rawTextOut.toByteArray(), charset)
        val rawSegments = fullMarkup.split(
            Regex("(?i)<mbp:pagebreak\\s*/?>|<hr[^>]*class=[\"']?pagebreak[\"']?[^>]*>")
        ).map { it.trim() }.filter { it.isNotEmpty() }

        val chapters = mutableListOf<BookChapter>()
        val chunks = if (rawSegments.isNotEmpty()) rawSegments else listOf(fullMarkup)

        for (segment in chunks) {
            val doc = Jsoup.parse(segment)
            val blocks = HtmlBlockExtractor.extractBlocks(doc.body())
            if (blocks.isEmpty()) continue

            // If a single segment is huge and has multiple H1/H2 headings, split cleanly at headings
            val splitSubChapters = splitBlocksByMajorHeadings(blocks)
            for ((subHeading, subBlocks) in splitSubChapters) {
                val idx = chapters.size
                val chapTitle = subHeading ?: "Chapter ${idx + 1}"
                chapters += BookChapter(index = idx, title = chapTitle, blocks = subBlocks)
            }
        }

        if (chapters.isEmpty()) {
            chapters += BookChapter(
                index = 0,
                title = fullTitle,
                blocks = listOf(ContentBlock.Paragraph("No readable text content found in MOBI/AZW file."))
            )
        }

        val toc = chapters.map { TocEntry(title = it.title, chapterIndex = it.index) }
        val totalWords = chapters.sumOf { it.wordCount }
        return LoadedBookContent.Reflowable(
            metadata = ParsedBookMetadata(
                title = fullTitle,
                author = authorText,
                description = description,
                publisher = publisher,
                isbn = isbn,
                tags = tags.filter { it.isNotEmpty() }.distinct(),
                embeddedCoverBytes = coverBytes,
                totalUnits = chapters.size,
                totalWords = totalWords
            ),
            chapters = chapters,
            toc = toc
        )
    }

    /**
     * Standard PalmDOC LZ77 decompression algorithm (exposed internal for unit testing).
     */
    internal fun decompressPalmDocLz77(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size * 2)
        var i = 0
        while (i < input.size) {
            val c = input[i].toInt() and 0xFF
            i++
            when {
                c == 0x00 || c in 0x09..0x7F -> {
                    out.write(c)
                }
                c in 0x01..0x08 -> {
                    val count = minOf(c, input.size - i)
                    out.write(input, i, count)
                    i += count
                }
                c in 0xC0..0xFF -> {
                    out.write(' '.code)
                    out.write(c xor 0x80)
                }
                c in 0x80..0xBF -> {
                    if (i >= input.size) break
                    val next = input[i].toInt() and 0xFF
                    i++
                    val pair = (c shl 8) or next
                    val distance = (pair shr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    val currentBytes = out.toByteArray()
                    if (distance in 1..currentBytes.size) {
                        val buffer = ByteArrayOutputStream(currentBytes.size + length)
                        buffer.write(currentBytes)
                        val bufArr = currentBytes.copyOf(currentBytes.size + length)
                        var writePos = currentBytes.size
                        var readPos = currentBytes.size - distance
                        repeat(length) {
                            bufArr[writePos++] = bufArr[readPos++]
                        }
                        out.reset()
                        out.write(bufArr)
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun trimTrailingExtraBytes(record: ByteArray, extraDataFlags: Int): ByteArray {
        if (extraDataFlags == 0 || record.isEmpty()) return record
        var size = record.size
        var flags = extraDataFlags shr 1
        while (flags != 0 && size > 0) {
            if ((flags and 1) != 0) {
                var shift = 0
                var trailingLen = 0
                var pos = size - 1
                while (pos >= 0 && shift < 28) {
                    val b = record[pos].toInt() and 0xFF
                    trailingLen = trailingLen or ((b and 0x7F) shl shift)
                    pos--
                    shift += 7
                    if ((b and 0x80) != 0) break
                }
                if (trailingLen in 1..size) {
                    size -= trailingLen
                }
            }
            flags = flags ushr 1
        }
        if ((extraDataFlags and 1) != 0 && size > 0) {
            val multibyteExtra = (record[size - 1].toInt() and 0x03) + 1
            if (multibyteExtra <= size) size -= multibyteExtra
        }
        return record.copyOfRange(0, size.coerceAtLeast(0))
    }

    private fun splitBlocksByMajorHeadings(blocks: List<ContentBlock>): List<Pair<String?, List<ContentBlock>>> {
        if (blocks.size < 40) {
            val firstH = blocks.filterIsInstance<ContentBlock.Heading>().firstOrNull()?.text
            return listOf(firstH to blocks)
        }
        val result = mutableListOf<Pair<String?, List<ContentBlock>>>()
        var currentTitle: String? = null
        var currentList = mutableListOf<ContentBlock>()

        for (block in blocks) {
            if (block is ContentBlock.Heading && block.level <= 2 && currentList.size >= 8) {
                result += currentTitle to currentList
                currentTitle = block.text
                currentList = mutableListOf(block)
            } else {
                if (currentTitle == null && block is ContentBlock.Heading) {
                    currentTitle = block.text
                }
                currentList += block
            }
        }
        if (currentList.isNotEmpty()) {
            result += currentTitle to currentList
        }
        return result
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val isGif = bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()
        return isJpeg || isPng || isGif
    }

    private fun emptyFallback(title: String, msg: String) = LoadedBookContent.Reflowable(
        metadata = ParsedBookMetadata(title = title),
        chapters = listOf(BookChapter(0, title, listOf(ContentBlock.Paragraph(msg)))),
        toc = listOf(TocEntry(title, 0))
    )
}
