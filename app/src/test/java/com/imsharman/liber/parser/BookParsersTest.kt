package com.imsharman.liber.parser

import com.imsharman.liber.data.parser.ContentBlock
import com.imsharman.liber.data.parser.DocxParser
import com.imsharman.liber.data.parser.EpubParser
import com.imsharman.liber.data.parser.Fb2Parser
import com.imsharman.liber.data.parser.MobiParser
import com.imsharman.liber.data.parser.NaturalSortComparator
import com.imsharman.liber.data.parser.PlainTextMarkdownParser
import com.imsharman.liber.data.repository.SampleBookFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookParsersTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `EPUB parser extracts metadata, Calibre series, TOC, and rich chapters`() {
        val sampleFile = SampleBookFactory.createMeditationsSampleEpub(tempFolder.newFolder("samples"))
        val parsed = sampleFile.inputStream().use {
            EpubParser.parseFullBook(it, fallbackTitle = "Fallback")
        }

        assertEquals("Meditations (Selected Reflections)", parsed.metadata.title)
        assertEquals("Marcus Aurelius", parsed.metadata.author)
        assertEquals("Stoic Foundations", parsed.metadata.seriesName)
        assertEquals(1.0f, parsed.metadata.seriesIndex ?: 0f, 0.01f)
        assertEquals("9780140449334", parsed.metadata.isbn)
        assertEquals(3, parsed.chapters.size)
        assertEquals(3, parsed.toc.size)
        assertTrue(parsed.metadata.totalWords > 120)
    }

    @Test
    fun `FB2 parser extracts sequence series, binary base64 cover, and sections`() {
        val tinyPngBase64 = Base64.getEncoder().encodeToString(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val fb2Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
              <description>
                <title-info>
                  <genre>sf</genre>
                  <author><first-name>Arkady</first-name><last-name>Strugatsky</last-name></author>
                  <book-title>Roadside Picnic</book-title>
                  <sequence name="Noon Universe" number="4"/>
                  <coverpage><image l:href="#cover.png"/></coverpage>
                  <lang>en</lang>
                </title-info>
              </description>
              <body>
                <section>
                  <title><p>Chapter 1. Redrick Schuhart</p></title>
                  <p>Happiness for <strong>everybody</strong>, free, and no one will go away unsatisfied!</p>
                </section>
              </body>
              <binary id="cover.png" content-type="image/png">$tinyPngBase64</binary>
            </FictionBook>
        """.trimIndent()

        val result = Fb2Parser.parse(
            ByteArrayInputStream(fb2Xml.toByteArray(Charsets.UTF_8)),
            fileName = "roadside.fb2",
            fallbackTitle = "Fallback"
        )

        assertEquals("Roadside Picnic", result.metadata.title)
        assertEquals("Arkady Strugatsky", result.metadata.author)
        assertEquals("Noon Universe", result.metadata.seriesName)
        assertEquals(4.0f, result.metadata.seriesIndex ?: 0f, 0.01f)
        assertNotNull(result.metadata.embeddedCoverBytes)
        assertEquals(1, result.chapters.size)
    }

    @Test
    fun `PalmDOC LZ77 decompressor decodes literals, space-pairs, and backreferences`() {
        // Construct compressed stream:
        // 'H', 'e', 'l', 'l', 'o', 0xE1 (' ' + 'a'), 'b', 'c', then LZ77 repeat distance=3, length=3 ("abc")
        // distance=3 -> (3 << 3) | (3 - 3) = 24 = 0x0018 -> with 0x8000 prefix = 0x8018
        val compressed = byteArrayOf(
            'H'.code.toByte(),
            'e'.code.toByte(),
            'l'.code.toByte(),
            'l'.code.toByte(),
            'o'.code.toByte(),
            0xE1.toByte(), // ' ' + ('a' ^ 0x80 -> 0x61 | 0x80 = 0xE1)
            'b'.code.toByte(),
            'c'.code.toByte(),
            0x80.toByte(),
            0x18.toByte()
        )
        val decoded = String(MobiParser.decompressPalmDocLz77(compressed), Charsets.US_ASCII)
        assertEquals("Hello abcabc", decoded)
    }

    @Test
    fun `MOBI parser reads PalmDB header, EXTH author, and HTML chapters`() {
        val htmlBytes = "<h1>Chapter One</h1><p>Call me Ishmael.</p><mbp:pagebreak/><h1>Chapter Two</h1><p>The Carpet-Bag.</p>"
            .toByteArray(Charsets.UTF_8)

        // Build minimal synthetic uncompressed MOBI with EXTH record 100 (Author)
        val authorBytes = "Herman Melville".toByteArray(Charsets.UTF_8)
        val exthRecLen = 8 + authorBytes.size
        val exthTotalLen = 12 + exthRecLen
        val mobiHeaderLen = 132
        val rec0Size = 16 + mobiHeaderLen + exthTotalLen
        val rec0 = ByteBuffer.allocate(rec0Size).order(ByteOrder.BIG_ENDIAN)
        rec0.putShort(0, 1) // uncompressed
        rec0.putInt(4, htmlBytes.size)
        rec0.putShort(8, 1) // 1 text record
        rec0.putShort(10, 4096)
        rec0.position(16)
        rec0.put("MOBI".toByteArray(Charsets.US_ASCII))
        rec0.putInt(20, mobiHeaderLen)
        rec0.putInt(28, 65001) // UTF-8
        rec0.putInt(128, 0x40) // EXTH flag
        val exthStart = 16 + mobiHeaderLen
        rec0.position(exthStart)
        rec0.put("EXTH".toByteArray(Charsets.US_ASCII))
        rec0.putInt(exthStart + 4, exthTotalLen)
        rec0.putInt(exthStart + 8, 1) // 1 EXTH record
        rec0.putInt(exthStart + 12, 100) // Author
        rec0.putInt(exthStart + 16, exthRecLen)
        rec0.position(exthStart + 20)
        rec0.put(authorBytes)

        val numRecords = 2
        val headerSize = 78 + numRecords * 8
        val fullMobi = ByteBuffer.allocate(headerSize + rec0Size + htmlBytes.size).order(ByteOrder.BIG_ENDIAN)
        val titleBytes = "Moby-Dick".toByteArray(Charsets.US_ASCII)
        fullMobi.put(titleBytes)
        fullMobi.putShort(76, numRecords.toShort())
        fullMobi.putInt(78, headerSize)
        fullMobi.putInt(86, headerSize + rec0Size)
        fullMobi.position(headerSize)
        fullMobi.put(rec0.array())
        fullMobi.put(htmlBytes)

        val parsed = MobiParser.parse(ByteArrayInputStream(fullMobi.array()), fallbackTitle = "Fallback")
        assertEquals("Moby-Dick", parsed.metadata.title)
        assertEquals("Herman Melville", parsed.metadata.author)
        assertEquals(2, parsed.chapters.size)
    }

    @Test
    fun `DOCX parser extracts core xml metadata and heading chapters`() {
        val docxBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("docProps/core.xml"))
                zos.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                                       xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>Research Notes</dc:title>
                      <dc:creator>Ada Lovelace</dc:creator>
                    </cp:coreProperties>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Analytical Engine</w:t></w:r></w:p>
                        <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Note G</w:t></w:r><w:r><w:t> computes Bernoulli numbers.</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()
            }
        }.toByteArray()

        val parsed = DocxParser.parse(ByteArrayInputStream(docxBytes), fallbackTitle = "Fallback")
        assertEquals("Research Notes", parsed.metadata.title)
        assertEquals("Ada Lovelace", parsed.metadata.author)
        assertEquals(1, parsed.chapters.size)
        assertEquals("Analytical Engine", parsed.chapters.first().title)
    }

    @Test
    fun `Markdown parser extracts frontmatter, headings TOC, quotes, and code blocks`() {
        val md = """
            ---
            title: "Field Guide"
            author: "Ursula K. Le Guin"
            series: "Hainish"
            series_index: 2
            ---
            # Chapter 1: Winter
            The **parade** moved slowly across *Karhide*.

            > Light is the left hand of darkness.

            # Chapter 2: The Ice
            ```
            temp = -40C
            ```
        """.trimIndent()

        val parsed = PlainTextMarkdownParser.parse(
            ByteArrayInputStream(md.toByteArray(Charsets.UTF_8)),
            fallbackTitle = "Fallback",
            isMarkdown = true
        )
        assertEquals("Field Guide", parsed.metadata.title)
        assertEquals("Ursula K. Le Guin", parsed.metadata.author)
        assertEquals("Hainish", parsed.metadata.seriesName)
        assertEquals(2, parsed.chapters.size)
        assertTrue(parsed.chapters[0].blocks.any { it is ContentBlock.Quote })
        assertTrue(parsed.chapters[1].blocks.any { it is ContentBlock.CodeBlock })
    }

    @Test
    fun `NaturalSortComparator orders comic pages numerically instead of lexicographically`() {
        val raw = listOf("Page_10.jpg", "Page_2.jpg", "Page_1.jpg", "Page_20.jpg")
        val sorted = raw.sortedWith(NaturalSortComparator)
        assertEquals(listOf("Page_1.jpg", "Page_2.jpg", "Page_10.jpg", "Page_20.jpg"), sorted)
    }
}
