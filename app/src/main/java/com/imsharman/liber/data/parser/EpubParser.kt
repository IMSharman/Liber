package com.imsharman.liber.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/**
 * Pure-Kotlin EPUB 2 & EPUB 3 parser using ZipInputStream + Jsoup XML/HTML DOM.
 * Supports:
 * - OPF metadata (Title, Author, Description, Publisher, ISBN, Language, Tags,
 *   Calibre series & EPUB3 belongs-to-collection series)
 * - Cover image extraction strictly from inside the EPUB archive
 * - EPUB 2 NCX (`toc.ncx`) and EPUB 3 Navigation (`nav`) Table of Contents
 * - Spine chapter extraction into rich ContentBlocks with inline styles & internal illustrations
 */
object EpubParser {

    fun parseMetadataOnly(inputStream: InputStream, fallbackTitle: String): ParsedBookMetadata {
        val entries = readZipEntries(inputStream)
        return extractMetadataAndSpine(entries, fallbackTitle, parseFullChapters = false, assetSaver = null).metadata
    }

    fun parseFullBook(
        inputStream: InputStream,
        fallbackTitle: String,
        assetSaver: ((entryName: String, bytes: ByteArray) -> String?)? = null
    ): LoadedBookContent.Reflowable {
        val entries = readZipEntries(inputStream)
        return extractMetadataAndSpine(entries, fallbackTitle, parseFullChapters = true, assetSaver = assetSaver)
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normalized = normalizeZipPath(entry.name)
                    map[normalized] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
    }

    private fun extractMetadataAndSpine(
        entries: Map<String, ByteArray>,
        fallbackTitle: String,
        parseFullChapters: Boolean,
        assetSaver: ((entryName: String, bytes: ByteArray) -> String?)?
    ): LoadedBookContent.Reflowable {
        // 1. Locate OPF package path from META-INF/container.xml or fallback scan
        val containerXml = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8)
        val opfPath = containerXml?.let { xml ->
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            doc.selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() }
        }?.let(::normalizeZipPath)
            ?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }

        if (opfPath == null || !entries.containsKey(opfPath)) {
            val emptyMeta = ParsedBookMetadata(title = fallbackTitle)
            return LoadedBookContent.Reflowable(emptyMeta, emptyList(), emptyList())
        }

        val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val opfDoc = Jsoup.parse(entries.getValue(opfPath).toString(Charsets.UTF_8), "", Parser.xmlParser())

        // 2. Extract Dublin Core & EPUB2/EPUB3/Calibre metadata
        val title = opfDoc.select("metadata > title, metadata > dc|title, dc|title, title")
            .firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle

        val authors = opfDoc.select("metadata > creator, metadata > dc|creator, dc|creator, creator")
            .mapNotNull { it.text().trim().takeIf(String::isNotEmpty) }
        val author = if (authors.isEmpty()) "Unknown Author" else authors.joinToString(", ")

        val rawDescription = opfDoc.select("metadata > description, metadata > dc|description, dc|description, description")
            .firstOrNull()?.text()?.trim().orEmpty()
        val description = if (rawDescription.contains('<')) {
            Jsoup.parse(rawDescription).text().trim()
        } else {
            rawDescription
        }

        val publisher = opfDoc.select("metadata > publisher, metadata > dc|publisher, dc|publisher")
            .firstOrNull()?.text()?.trim().orEmpty()

        val language = opfDoc.select("metadata > language, metadata > dc|language, dc|language")
            .firstOrNull()?.text()?.trim().orEmpty()

        val isbn = opfDoc.select("metadata > identifier, metadata > dc|identifier, dc|identifier")
            .map { it.text().trim() }
            .firstOrNull { id ->
                val digits = id.filter { it.isDigit() || it.equals('X', ignoreCase = true) }
                id.contains("isbn", ignoreCase = true) || digits.length == 10 || digits.length == 13
            }?.replace(Regex("(?i)^urn:isbn:"), "")?.trim().orEmpty()

        val tags = opfDoc.select("metadata > subject, metadata > dc|subject, dc|subject")
            .mapNotNull { it.text().trim().takeIf(String::isNotEmpty) }
            .distinct()

        // Series metadata (Calibre EPUB2 or EPUB3 belongs-to-collection)
        var seriesName: String? = opfDoc.selectFirst("meta[name=calibre:series]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
        var seriesIndex: Float? = opfDoc.selectFirst("meta[name=calibre:series_index]")?.attr("content")
            ?.toFloatOrNull()

        if (seriesName == null) {
            val collectionMeta = opfDoc.selectFirst("meta[property=belongs-to-collection]")
            if (collectionMeta != null) {
                seriesName = collectionMeta.text().trim().takeIf { it.isNotEmpty() }
                val collectionId = collectionMeta.attr("id")
                if (collectionId.isNotEmpty()) {
                    seriesIndex = opfDoc.selectFirst("meta[refines=#$collectionId][property=group-position]")
                        ?.text()?.trim()?.toFloatOrNull()
                }
            }
        }

        // 3. Manifest map: id -> (resolvedZipPath, mediaType, properties)
        data class ManifestItem(val id: String, val zipPath: String, val mediaType: String, val properties: String)

        val manifestById = LinkedHashMap<String, ManifestItem>()
        for (item in opfDoc.select("manifest > item")) {
            val id = item.attr("id")
            val href = decodeHref(item.attr("href"))
            if (id.isNotEmpty() && href.isNotEmpty()) {
                val resolved = resolveRelativePath(opfDir, href)
                manifestById[id] = ManifestItem(
                    id = id,
                    zipPath = resolved,
                    mediaType = item.attr("media-type"),
                    properties = item.attr("properties")
                )
            }
        }

        // 4. Locate embedded cover bytes strictly inside the EPUB archive
        val coverMetaId = opfDoc.selectFirst("meta[name=cover]")?.attr("content")?.trim()
        val coverItem = when {
            !coverMetaId.isNullOrEmpty() && manifestById.containsKey(coverMetaId) ->
                manifestById[coverMetaId]
            else -> manifestById.values.firstOrNull {
                it.properties.split(' ').contains("cover-image")
            } ?: manifestById.values.firstOrNull {
                it.mediaType.startsWith("image/") &&
                    (it.id.contains("cover", ignoreCase = true) || it.zipPath.contains("cover", ignoreCase = true))
            }
        }
        val coverBytes = coverItem?.let { entries[it.zipPath] }

        // 5. Spine ordered itemrefs
        val spineItemIds = opfDoc.select("spine > itemref")
            .filterNot { it.attr("linear").equals("no", ignoreCase = true) }
            .map { it.attr("idref") }
            .filter { manifestById.containsKey(it) }

        val spineItems = if (spineItemIds.isNotEmpty()) {
            spineItemIds.mapNotNull { manifestById[it] }
        } else {
            manifestById.values.filter {
                it.mediaType.contains("html", ignoreCase = true) || it.zipPath.endsWith(".xhtml", true) || it.zipPath.endsWith(".html", true)
            }
        }

        if (!parseFullChapters) {
            val meta = ParsedBookMetadata(
                title = title,
                author = author,
                seriesName = seriesName,
                seriesIndex = seriesIndex,
                description = description,
                publisher = publisher,
                isbn = isbn,
                language = language,
                tags = tags,
                embeddedCoverBytes = coverBytes,
                totalUnits = spineItems.size.coerceAtLeast(1)
            )
            return LoadedBookContent.Reflowable(meta, emptyList(), emptyList())
        }

        // 6. Parse TOC map (href path -> title) from EPUB3 nav or EPUB2 toc.ncx
        val tocRawEntries = parseTocEntries(opfDoc, opfDir, manifestById.values.map {
            Triple(it.id, it.zipPath, it.properties to it.mediaType)
        }, entries)
        val pathToTocTitle = tocRawEntries.associate { it.first.substringBefore('#') to it.second }

        // 7. Parse each spine XHTML chapter into rich ContentBlocks
        val chapters = mutableListOf<BookChapter>()
        val zipPathToChapterIndex = HashMap<String, Int>()

        for (item in spineItems) {
            val rawBytes = entries[item.zipPath] ?: continue
            val chapterDir = item.zipPath.substringBeforeLast('/', missingDelimiterValue = "")
            val html = rawBytes.toString(Charsets.UTF_8)
            val doc = Jsoup.parse(html)
            val body = doc.body() ?: continue

            val blocks = HtmlBlockExtractor.extractBlocks(
                root = body,
                resolveImage = { src ->
                    if (assetSaver == null) return@extractBlocks null
                    val cleanSrc = decodeHref(src.substringBefore('#').substringBefore('?'))
                    val imgZipPath = resolveRelativePath(chapterDir, cleanSrc)
                    val imgBytes = entries[imgZipPath] ?: return@extractBlocks null
                    assetSaver(imgZipPath, imgBytes)
                }
            )

            if (blocks.isEmpty()) continue

            val chapterIdx = chapters.size
            zipPathToChapterIndex[item.zipPath] = chapterIdx

            val inferredHeading = blocks.filterIsInstance<ContentBlock.Heading>().firstOrNull()?.text
                ?: doc.title().trim().takeIf { it.isNotEmpty() }
            val chapterTitle = pathToTocTitle[item.zipPath]
                ?: inferredHeading
                ?: "Chapter ${chapterIdx + 1}"

            chapters += BookChapter(
                index = chapterIdx,
                title = chapterTitle,
                blocks = blocks
            )
        }

        if (chapters.isEmpty()) {
            chapters += BookChapter(
                index = 0,
                title = title,
                blocks = listOf(ContentBlock.Paragraph("No readable text chapters were found in this EPUB."))
            )
        }

        val resolvedToc = if (tocRawEntries.isNotEmpty()) {
            tocRawEntries.mapNotNull { (href, label, depth) ->
                val basePath = href.substringBefore('#')
                val chapIdx = zipPathToChapterIndex[basePath] ?: return@mapNotNull null
                TocEntry(title = label, chapterIndex = chapIdx, depth = depth)
            }.distinctBy { "${it.chapterIndex}:${it.title}" }
        } else {
            chapters.map { TocEntry(title = it.title, chapterIndex = it.index, depth = 0) }
        }

        val finalToc = resolvedToc.ifEmpty {
            chapters.map { TocEntry(title = it.title, chapterIndex = it.index, depth = 0) }
        }

        val totalWords = chapters.sumOf { it.wordCount }
        val meta = ParsedBookMetadata(
            title = title,
            author = author,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            description = description,
            publisher = publisher,
            isbn = isbn,
            language = language,
            tags = tags,
            embeddedCoverBytes = coverBytes,
            totalUnits = chapters.size,
            totalWords = totalWords
        )

        return LoadedBookContent.Reflowable(
            metadata = meta,
            chapters = chapters,
            toc = finalToc
        )
    }

    private fun parseTocEntries(
        opfDoc: org.jsoup.nodes.Document,
        opfDir: String,
        manifestSummary: List<Triple<String, String, Pair<String, String>>>,
        entries: Map<String, ByteArray>
    ): List<Triple<String, String, Int>> {
        // Prefer EPUB 3 nav document
        val navItem = manifestSummary.firstOrNull { (_, _, meta) ->
            meta.first.split(' ').contains("nav")
        }
        if (navItem != null && entries.containsKey(navItem.second)) {
            val navPath = navItem.second
            val navDir = navPath.substringBeforeLast('/', missingDelimiterValue = "")
            val navDoc = Jsoup.parse(entries.getValue(navPath).toString(Charsets.UTF_8))
            val tocNav = navDoc.selectFirst("nav[epub:type=toc], nav[*|type=toc], nav#toc, nav")
            if (tocNav != null) {
                val result = mutableListOf<Triple<String, String, Int>>()
                fun walkOl(ol: Element, depth: Int) {
                    for (li in ol.children().filter { it.tagName().equals("li", true) }) {
                        val a = li.children().firstOrNull { it.tagName().equals("a", true) }
                        if (a != null) {
                            val href = decodeHref(a.attr("href"))
                            val text = a.text().trim()
                            if (href.isNotEmpty() && text.isNotEmpty()) {
                                val base = resolveRelativePath(navDir, href.substringBefore('#'))
                                result += Triple(base, text, depth)
                            }
                        }
                        li.children().filter { it.tagName().equals("ol", true) }.forEach { walkOl(it, depth + 1) }
                    }
                }
                tocNav.children().filter { it.tagName().equals("ol", true) }.forEach { walkOl(it, 0) }
                if (result.isNotEmpty()) return result
            }
        }

        // Fallback to EPUB 2 NCX
        val spineTocId = opfDoc.selectFirst("spine")?.attr("toc")?.trim()
        val ncxItem = manifestSummary.firstOrNull { (id, path, meta) ->
            (!spineTocId.isNullOrEmpty() && id == spineTocId) ||
                meta.second.contains("dtbncx", ignoreCase = true) ||
                path.endsWith(".ncx", ignoreCase = true)
        }
        if (ncxItem != null && entries.containsKey(ncxItem.second)) {
            val ncxPath = ncxItem.second
            val ncxDir = ncxPath.substringBeforeLast('/', missingDelimiterValue = "")
            val ncxDoc = Jsoup.parse(entries.getValue(ncxPath).toString(Charsets.UTF_8), "", Parser.xmlParser())
            val navMap = ncxDoc.selectFirst("navMap")
            if (navMap != null) {
                val result = mutableListOf<Triple<String, String, Int>>()
                fun walkNavPoint(parent: Element, depth: Int) {
                    for (np in parent.children().filter { it.tagName().equals("navPoint", true) }) {
                        val label = np.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                        val src = decodeHref(np.selectFirst("content")?.attr("src").orEmpty())
                        if (label.isNotEmpty() && src.isNotEmpty()) {
                            val base = resolveRelativePath(ncxDir, src.substringBefore('#'))
                            result += Triple(base, label, depth)
                        }
                        walkNavPoint(np, depth + 1)
                    }
                }
                walkNavPoint(navMap, 0)
                if (result.isNotEmpty()) return result
            }
        }

        return emptyList()
    }

    private fun decodeHref(raw: String): String =
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).trim()

    internal fun normalizeZipPath(path: String): String {
        val parts = mutableListOf<String>()
        for (segment in path.replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }

    internal fun resolveRelativePath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.trimStart('/')
        return if (baseDir.isEmpty()) normalizeZipPath(cleanHref) else normalizeZipPath("$baseDir/$cleanHref")
    }
}

/**
 * Shared HTML/XHTML DOM -> ContentBlock converter used by EPUB, MOBI/AZW3, and HTML fragments.
 */
object HtmlBlockExtractor {

    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "main", "header", "footer", "aside",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "pre", "ul", "ol", "hr", "figure", "table", "tr"
    )

    fun extractBlocks(
        root: Element,
        resolveImage: ((src: String) -> String?)? = null
    ): List<ContentBlock> {
        val out = mutableListOf<ContentBlock>()
        walkElement(root, out, resolveImage)
        return out
    }

    private fun walkElement(
        element: Element,
        sink: MutableList<ContentBlock>,
        resolveImage: ((String) -> String?)?
    ) {
        val tag = element.tagName().lowercase()
        when (tag) {
            "script", "style", "head", "svg", "noscript" -> return
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val txt = element.text().trim()
                if (txt.isNotEmpty()) {
                    val lvl = tag.drop(1).toIntOrNull()?.coerceIn(1, 6) ?: 2
                    sink += ContentBlock.Heading(level = lvl, text = txt)
                }
            }
            "hr" -> sink += ContentBlock.Divider
            "pre" -> {
                val code = element.wholeText().trimEnd()
                if (code.isNotBlank()) {
                    sink += ContentBlock.CodeBlock(code)
                }
            }
            "blockquote" -> {
                val txt = element.text().trim()
                if (txt.isNotEmpty()) {
                    sink += ContentBlock.Quote(text = txt)
                }
            }
            "ul", "ol" -> {
                val items = element.children()
                    .filter { it.tagName().equals("li", ignoreCase = true) }
                    .mapNotNull { it.text().trim().takeIf(String::isNotEmpty) }
                if (items.isNotEmpty()) {
                    sink += ContentBlock.ListItems(ordered = tag == "ol", items = items)
                }
            }
            "img", "image" -> {
                val src = element.attr("src").ifEmpty { element.attr("xlink:href") }.ifEmpty { element.attr("href") }
                if (src.isNotEmpty() && resolveImage != null) {
                    val savedPath = resolveImage(src)
                    if (savedPath != null) {
                        sink += ContentBlock.EmbeddedImage(
                            localCachePath = savedPath,
                            altText = element.attr("alt").trim()
                        )
                    }
                }
            }
            else -> {
                val hasBlockChildren = element.children().any { it.tagName().lowercase() in BLOCK_TAGS }
                if (!hasBlockChildren) {
                    // Check embedded images first
                    if (resolveImage != null) {
                        for (img in element.select("img, image")) {
                            val src = img.attr("src").ifEmpty { img.attr("xlink:href") }.ifEmpty { img.attr("href") }
                            if (src.isNotEmpty()) {
                                val savedPath = resolveImage(src)
                                if (savedPath != null) {
                                    sink += ContentBlock.EmbeddedImage(savedPath, img.attr("alt").trim())
                                }
                            }
                        }
                    }
                    val (text, spans) = buildStyledParagraph(element)
                    if (text.isNotBlank()) {
                        sink += ContentBlock.Paragraph(text = text, spans = spans)
                    }
                } else {
                    for (child in element.childNodes()) {
                        when (child) {
                            is TextNode -> {
                                val t = child.text().trim()
                                if (t.isNotEmpty()) {
                                    sink += ContentBlock.Paragraph(t)
                                }
                            }
                            is Element -> walkElement(child, sink, resolveImage)
                        }
                    }
                }
            }
        }
    }

    private fun buildStyledParagraph(root: Element): Pair<String, List<InlineSpan>> {
        val sb = StringBuilder()
        val spans = mutableListOf<InlineSpan>()

        fun appendNode(node: Node, activeStyles: Set<InlineStyle>) {
            when (node) {
                is TextNode -> {
                    val raw = node.wholeText.replace(Regex("[ \\t\\r\\n]+"), " ")
                    if (raw.isEmpty()) return
                    val toAppend = if (sb.isEmpty()) raw.trimStart() else raw
                    if (toAppend.isEmpty()) return
                    val start = sb.length
                    sb.append(toAppend)
                    val end = sb.length
                    for (st in activeStyles) {
                        spans += InlineSpan(start, end, st)
                    }
                }
                is Element -> {
                    val name = node.tagName().lowercase()
                    if (name == "br") {
                        sb.append('\n')
                        return
                    }
                    val nextStyles = activeStyles.toMutableSet()
                    when (name) {
                        "b", "strong" -> nextStyles += InlineStyle.BOLD
                        "i", "em", "cite", "dfn" -> nextStyles += InlineStyle.ITALIC
                        "code", "kbd", "samp", "tt" -> nextStyles += InlineStyle.CODE
                        "s", "strike", "del" -> nextStyles += InlineStyle.STRIKETHROUGH
                    }
                    for (child in node.childNodes()) {
                        appendNode(child, nextStyles)
                    }
                }
            }
        }

        for (child in root.childNodes()) {
            appendNode(child, emptySet())
        }

        val trimmedEnd = sb.toString().trimEnd()
        val maxLen = trimmedEnd.length
        val clampedSpans = spans.mapNotNull { sp ->
            val s = sp.start.coerceIn(0, maxLen)
            val e = sp.end.coerceIn(0, maxLen)
            if (s < e) InlineSpan(s, e, sp.style) else null
        }
        return trimmedEnd to clampedSpans
    }
}
