package com.imsharman.liber.metadata

import com.imsharman.liber.data.parser.EpubParser
import com.imsharman.liber.domain.util.BookMetadataNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookMetadataNormalizerTest {

    @Test
    fun `unifies Pierce Brown author variations from real test books`() {
        val rawVariants = listOf(
            "Brown, Pierce",
            "Pierce Brown",
            "Pierce Brown;",
            "BROWN, PIERCE;",
            "pierce  brown ",
            "Brown, Pierce (Author)"
        )

        for (raw in rawVariants) {
            assertEquals("Failed for '$raw'", "Pierce Brown", BookMetadataNormalizer.normalizeAuthor(raw))
            assertEquals("pierce brown", BookMetadataNormalizer.canonicalAuthorKey(raw))
        }
    }

    @Test
    fun `handles particle surnames, initials, suffixes, and multiple authors`() {
        assertEquals("Ursula K. Le Guin", BookMetadataNormalizer.normalizeAuthor("Le Guin, Ursula K."))
        assertEquals("J. R. R. Tolkien", BookMetadataNormalizer.normalizeAuthor("Tolkien, J.R.R."))
        assertEquals("Martin Luther King, Jr.", BookMetadataNormalizer.normalizeAuthor("King, Martin Luther, Jr."))
        assertEquals(
            "Arkady Strugatsky, Boris Strugatsky",
            BookMetadataNormalizer.normalizeAuthor("Strugatsky, Arkady; Strugatsky, Boris")
        )
    }

    @Test
    fun `unifies Red Rising Trilogy, Red Rising Saga, and The Red Rising Series while preserving single-word Saga`() {
        assertEquals("Red Rising", BookMetadataNormalizer.normalizeSeriesName("Red Rising Trilogy"))
        assertEquals("Red Rising", BookMetadataNormalizer.normalizeSeriesName("Red Rising Saga"))
        assertEquals("Red Rising", BookMetadataNormalizer.normalizeSeriesName("The Red Rising Series"))
        assertEquals("red rising", BookMetadataNormalizer.canonicalSeriesKey("Red Rising Trilogy"))
        assertEquals("red rising", BookMetadataNormalizer.canonicalSeriesKey("The Red Rising Saga"))

        // Single-word series "Saga" must NOT be stripped to empty
        assertEquals("Saga", BookMetadataNormalizer.normalizeSeriesName("Saga"))
    }

    @Test
    fun `parses series and volume numbers from Open Library edition strings and publisher blurbs`() {
        val ed2 = BookMetadataNormalizer.parseSeriesAndNumberFromEditionString("Red Rising Trilogy, 2")
        assertNotNull(ed2)
        assertEquals("Red Rising", ed2!!.seriesName)
        assertEquals(2.0f, ed2.seriesIndex ?: 0f, 0.01f)

        val ed3 = BookMetadataNormalizer.parseSeriesAndNumberFromEditionString("The Red Rising trilogy -- book III")
        assertNotNull(ed3)
        assertEquals("Red Rising", ed3!!.seriesName)
        assertEquals(3.0f, ed3.seriesIndex ?: 0f, 0.01f)

        val fromBlurb = BookMetadataNormalizer.inferSeriesFromBookSignals(
            title = "Golden Son",
            description = "Golden Son continues the stunning saga of Darrow. Book Two of the New York Times bestselling Red Rising Trilogy."
        )
        assertNotNull(fromBlurb)
        assertEquals("Red Rising", fromBlurb!!.seriesName)
        assertEquals(2.0f, fromBlurb.seriesIndex ?: 0f, 0.01f)
    }

    @Test
    fun `EpubParser extracts EPUB 3 belongs-to-collection with #id refines and opf namespace prefixes`() {
        val epubBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("META-INF/container.xml"))
                zos.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("OEBPS/package.opf"))
                zos.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <opf:package xmlns:opf="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
                      <opf:metadata>
                        <dc:title>Morning Star</dc:title>
                        <dc:creator>Brown, Pierce;</dc:creator>
                        <opf:meta property="belongs-to-collection" id="c01">The Red Rising Saga</opf:meta>
                        <opf:meta refines="#c01" property="collection-type">series</opf:meta>
                        <opf:meta refines="#c01" property="group-position">3</opf:meta>
                      </opf:metadata>
                      <opf:manifest>
                        <opf:item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                      </opf:manifest>
                      <opf:spine>
                        <opf:itemref idref="ch1"/>
                      </opf:spine>
                    </opf:package>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
                zos.write("<html><body><h1>Chapter 1</h1><p>I would have lived in peace.</p></body></html>".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        val meta = EpubParser.parseMetadataOnly(ByteArrayInputStream(epubBytes), fallbackTitle = "Fallback")
        assertEquals("Morning Star", meta.title)
        assertEquals("Pierce Brown", meta.author)
        assertEquals("Red Rising", meta.seriesName)
        assertEquals(3.0f, meta.seriesIndex ?: 0f, 0.01f)
    }
}
