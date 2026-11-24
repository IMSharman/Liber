package com.imsharman.liber.privacy

import com.imsharman.liber.data.storage.ScopedFolderScanner
import com.imsharman.liber.domain.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopedPrivacyFilterTest {

    @Test
    fun `rejects standalone images and system files so device photos are never touched`() {
        val forbiddenFiles = listOf(
            "cover.jpg",
            "cover.jpeg",
            "folder.png",
            "IMG_20261123_184012.webp",
            "screenshot.gif",
            "wallpaper.bmp",
            ".nomedia",
            ".hidden.epub",
            "DCIM_photo.heic",
            "audiobook.mp3",
            "movie.mp4"
        )
        for (name in forbiddenFiles) {
            assertNull(
                "Expected $name to be strictly ignored by ScopedFolderScanner",
                ScopedFolderScanner.shouldAcceptCandidateFile(name)
            )
        }
    }

    @Test
    fun `accepts all requested ebook and document formats`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromFileName("Dune.epub"))
        assertEquals(BookFormat.PDF, BookFormat.fromFileName("Algorithms_4e.PDF"))
        assertEquals(BookFormat.CBZ, BookFormat.fromFileName("Berserk_v01.cbz"))
        assertEquals(BookFormat.CBR, BookFormat.fromFileName("Saga_001.cbr"))
        assertEquals(BookFormat.MOBI, BookFormat.fromFileName("Foundation.mobi"))
        assertEquals(BookFormat.AZW3, BookFormat.fromFileName("Hyperion.azw3"))
        assertEquals(BookFormat.FB2, BookFormat.fromFileName("Solaris.fb2"))
        assertEquals(BookFormat.FB2, BookFormat.fromFileName("Roadside_Picnic.fb2.zip"))
        assertEquals(BookFormat.TXT, BookFormat.fromFileName("Notes.txt"))
        assertEquals(BookFormat.MARKDOWN, BookFormat.fromFileName("Journal.md"))
        assertEquals(BookFormat.DOCX, BookFormat.fromFileName("Manuscript.docx"))
    }
}
