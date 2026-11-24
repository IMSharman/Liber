package com.imsharman.liber.data.repository

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates a clean, valid public-domain EPUB 3 classic (*Meditations* — Selected Books by Marcus Aurelius)
 * strictly inside Liber's private `filesDir/samples/` directory ONLY when the user explicitly clicks
 * "Load Sample Classic" on an empty library.
 */
object SampleBookFactory {

    fun createMeditationsSampleEpub(samplesDir: File): File {
        val target = File(samplesDir, "Marcus_Aurelius_Meditations_Selections.epub")
        if (target.exists() && target.length() > 512) return target

        ZipOutputStream(FileOutputStream(target)).use { zos ->
            // 1. mimetype
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
            zos.closeEntry()

            // 2. META-INF/container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 3. OEBPS/content.opf
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">urn:isbn:9780140449334</dc:identifier>
                    <dc:title>Meditations (Selected Reflections)</dc:title>
                    <dc:creator>Marcus Aurelius</dc:creator>
                    <dc:language>en</dc:language>
                    <dc:publisher>Public Domain Stoic Classics</dc:publisher>
                    <dc:subject>Philosophy</dc:subject>
                    <dc:subject>Stoicism</dc:subject>
                    <dc:description>Personal philosophical reflections of the Roman Emperor Marcus Aurelius, translated by George Long (Public Domain). Included as an optional reader showcase for Liber.</dc:description>
                    <meta name="calibre:series" content="Stoic Foundations"/>
                    <meta name="calibre:series_index" content="1"/>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch3" href="ch3.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="ch1"/>
                    <itemref idref="ch2"/>
                    <itemref idref="ch3"/>
                  </spine>
                </package>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 4. OEBPS/nav.xhtml
            zos.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head><title>Contents</title></head>
                <body>
                  <nav epub:type="toc" id="toc">
                    <ol>
                      <li><a href="ch1.xhtml">Book II — On the River Gran</a></li>
                      <li><a href="ch2.xhtml">Book IV — The Inner Citadel</a></li>
                      <li><a href="ch3.xhtml">Book V — At Dawn</a></li>
                    </ol>
                  </nav>
                </body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 5. Chapters
            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Book II — On the River Gran</title></head>
                <body>
                  <h1>Book II — On the River Gran</h1>
                  <blockquote>Begin the morning by saying to thyself: I shall meet with the busybody, the ungrateful, arrogant, deceitful, envious, unsocial.</blockquote>
                  <p>All these things happen to them by reason of their ignorance of what is <strong>good and evil</strong>. But I who have seen the nature of the good that it is beautiful, and of the bad that it is ugly, and the nature of him who does wrong, that it is akin to me, cannot be injured by any of them.</p>
                  <p>Every moment think steadily as a Roman and a human being to do what thou hast in hand with <em>perfect and simple dignity</em>, and feeling of affection, and freedom, and justice; and to give thyself relief from all other thoughts.</p>
                  <hr/>
                  <p>Do not waste the remainder of thy life in thoughts about others, when thou dost not refer thy thoughts to some object of common utility. For thou losest the opportunity of doing something else when thou hast such thoughts as these: What is such a person doing, and why, and what is he saying, and what is he thinking of?</p>
                  <p>Though thou shouldst be going to live three thousand years, and as many times ten thousand years, still remember that no man loses any other life than this which he now lives, nor lives any other than this which he now loses.</p>
                </body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/ch2.xhtml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Book IV — The Inner Citadel</title></head>
                <body>
                  <h1>Book IV — The Inner Citadel</h1>
                  <p>Men seek retreats for themselves, houses in the country, sea-shores, and mountains; and thou too art wont to desire such things very much. But this is altogether a mark of the most common sort of men, for it is in thy power <strong>whenever thou shalt choose to retire into thyself</strong>.</p>
                  <blockquote>Nowhere either with more quiet or more freedom from trouble does a man retire than into his own soul, particularly when he has within him such thoughts that by looking into them he is immediately in perfect tranquility.</blockquote>
                  <p>Constantly then give to thyself this retreat, and renew thyself; and let thy principles be brief and fundamental, which, as soon as thou shalt recur to them, will be sufficient to wash away all trouble, and to send thee back free from all discontent with the things to which thou returnest.</p>
                  <p>Take away thy opinion, and then there is taken away the complaint, <em>“I have been harmed.”</em> Take away the complaint, “I have been harmed,” and the harm is taken away.</p>
                  <ul>
                    <li>Try how the life of the good man suits thee: pleased with his portion in the whole, and satisfied with his own just acts.</li>
                    <li>Look within. Let neither the peculiar quality of anything nor its value escape thee.</li>
                    <li>Be like the promontory against which the waves continually break, but it stands firm and tames the fury of the water around it.</li>
                  </ul>
                </body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/ch3.xhtml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Book V — At Dawn</title></head>
                <body>
                  <h1>Book V — At Dawn</h1>
                  <p>In the morning when thou risest unwillingly, let this thought be present—I am rising to the work of a human being. Why then am I dissatisfied if I am going to do the things for which I exist and for which I was brought into the world?</p>
                  <p>Or have I been made for this, to lie in the bed-clothes and keep myself warm?—But this is more pleasant.—Dost thou exist then to take thy pleasure, and not at all for action or exertion?</p>
                  <blockquote>Such as are thy habitual thoughts, such also will be the character of thy mind; for the soul is dyed by the thoughts.</blockquote>
                  <p>Reverence that which is best in the universe; and this is that which makes use of all things and directs all things. And in like manner also reverence that which is best in thyself; and this is of the same kind as that.</p>
                </body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }

        return target
    }
}
