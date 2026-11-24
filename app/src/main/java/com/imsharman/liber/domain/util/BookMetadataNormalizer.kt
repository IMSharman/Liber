package com.imsharman.liber.domain.util

import java.text.Normalizer

data class InferredSeries(
    val seriesName: String,
    val seriesIndex: Float?
)

/**
 * Normalizes messy real-world ebook author names and extracts/unifies series metadata
 * across OPF tags, titles, subtitles, filenames, descriptions, and Open Library lookups.
 */
object BookMetadataNormalizer {

    private val ROLE_TAG_REGEX = Regex(
        "\\s*(?:\\([^)]*(?:author|editor|illustrator|translator|narrator|edt|trl|ill)[^)]*\\)|\\[[^]]*(?:author|editor|illustrator|translator|edt|trl)[^]]*\\])",
        RegexOption.IGNORE_CASE
    )

    private val GENERATIONAL_SUFFIXES = setOf(
        "jr", "jr.", "sr", "sr.", "ii", "iii", "iv", "v",
        "phd", "ph.d.", "md", "m.d.", "esq", "esq."
    )

    private val SURNAME_PARTICLES = setOf(
        "le", "la", "de", "del", "della", "di", "da", "dos", "das",
        "van", "von", "der", "den", "ten", "ter",
        "st", "st.", "saint", "mac", "mc", "al", "bin", "ibn"
    )

    private val TRAILING_SERIES_WORDS_REGEX = Regex(
        "\\s+(?:the\\s+)?(?:book\\s+series|novel\\s+series|series|saga|trilogy|duology|quartet|tetralogy|quintet|chronicles|chronicle|cycle|sequence)$",
        RegexOption.IGNORE_CASE
    )

    private val WORD_TO_NUMBER = mapOf(
        "one" to 1f, "first" to 1f, "i" to 1f,
        "two" to 2f, "second" to 2f, "ii" to 2f,
        "three" to 3f, "third" to 3f, "iii" to 3f,
        "four" to 4f, "fourth" to 4f, "iv" to 4f,
        "five" to 5f, "fifth" to 5f, "v" to 5f,
        "six" to 6f, "sixth" to 6f, "vi" to 6f,
        "seven" to 7f, "seventh" to 7f, "vii" to 7f,
        "eight" to 8f, "eighth" to 8f, "viii" to 8f,
        "nine" to 9f, "ninth" to 9f, "ix" to 9f,
        "ten" to 10f, "tenth" to 10f, "x" to 10f,
        "eleven" to 11f, "eleventh" to 11f, "xi" to 11f,
        "twelve" to 12f, "twelfth" to 12f, "xii" to 12f
    )

    /**
     * Unifies author variants such as `'Brown, Pierce'`, `'Pierce Brown'`, `'Pierce Brown;'`,
     * `'BROWN, PIERCE'`, and `'Tolkien, J.R.R.'` into `'Pierce Brown'` and `'J. R. R. Tolkien'`.
     */
    fun normalizeAuthor(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown Author"

        val cleaned = raw
            .replace(ROLE_TAG_REGEX, " ")
            .replace(Regex("[\\u00A0\\s]+"), " ")
            .trim()
            .trim(';', ',', ':', '|', '/', '-', '—')
            .trim()

        if (cleaned.isEmpty() || cleaned.equals("unknown", ignoreCase = true) ||
            cleaned.equals("unknown author", ignoreCase = true)
        ) {
            return "Unknown Author"
        }

        // Split multi-author strings separated by ';', '&', ' and ', or ' | '
        val rawSegments = cleaned
            .split(Regex("\\s*(?:;|&|\\band\\b|\\|)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim().trim(';', ',', ':', '/') }
            .filter { it.isNotEmpty() }

        if (rawSegments.isEmpty()) return "Unknown Author"

        val normalizedParts = rawSegments.map { normalizeSingleAuthorSegment(it) }
            .filter { it.isNotEmpty() }
            .distinctBy { singleAuthorKey(it) }

        return if (normalizedParts.isEmpty()) "Unknown Author" else normalizedParts.joinToString(", ")
    }

    private fun normalizeSingleAuthorSegment(segment: String): String {
        var s = segment.trim().trim(';', ',', ':').replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return ""

        val commaParts = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (commaParts.size == 2) {
            val left = commaParts[0]
            val right = commaParts[1]
            val rightLower = right.lowercase()
            if (rightLower !in GENERATIONAL_SUFFIXES && shouldFlipLastFirst(left, right)) {
                s = "$right $left"
            }
        } else if (commaParts.size == 3) {
            // e.g. "King, Martin Luther, Jr." -> "Martin Luther King, Jr."
            val left = commaParts[0]
            val mid = commaParts[1]
            val suffix = commaParts[2]
            if (suffix.lowercase() in GENERATIONAL_SUFFIXES && shouldFlipLastFirst(left, mid)) {
                s = "$mid $left, $suffix"
            }
        }

        // Normalize initials spacing: "J.R.R. Tolkien" -> "J. R. R. Tolkien"
        s = s.replace(Regex("\\b([A-Za-z])\\.(?=[A-Za-z])"), "$1. ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Fix ALL-CAPS or all-lowercase names while preserving mixed case ("Le Guin", "McMaster")
        val letters = s.filter { it.isLetter() }
        if (letters.isNotEmpty() && (letters.all { it.isUpperCase() } || letters.all { it.isLowerCase() })) {
            s = toSmartTitleCase(s)
        }

        return s
    }

    private fun shouldFlipLastFirst(left: String, right: String): Boolean {
        val leftWords = left.split(' ').filter { it.isNotEmpty() }
        val rightWords = right.split(' ').filter { it.isNotEmpty() }
        if (leftWords.isEmpty() || rightWords.isEmpty()) return false
        if (rightWords.size > 4) return false

        // Standard 1-word surname: "Brown, Pierce" / "Tolkien, J. R. R."
        if (leftWords.size == 1) return true

        // 2-word surname starting with particle ("Le Guin, Ursula K.", "Van Vogt, A. E.")
        if (leftWords.size == 2 && leftWords[0].lowercase() in SURNAME_PARTICLES) return true

        // Or right side is a single given name ("McMaster Bujold, Lois")
        if (leftWords.size == 2 && rightWords.size == 1) return true

        return false
    }

    fun canonicalAuthorKey(author: String?): String {
        val norm = normalizeAuthor(author)
        return singleAuthorKey(norm)
    }

    private fun singleAuthorKey(name: String): String {
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return decomposed
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalizes series names so `'Red Rising Trilogy'`, `'Red Rising Saga'`, `'The Red Rising Series'`,
     * and `'Red Rising, 2'` all normalize cleanly to `'Red Rising'` (while single-word titles like
     * `'Saga'` stay `'Saga'`).
     */
    fun normalizeSeriesName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw
            .replace(Regex("[\\u00A0\\s]+"), " ")
            .trim()
            .trim('"', '\'', '“', '”', '[', ']', '(', ')', ';', ',', ':', '-', '—')
            .trim()

        // Strip trailing number indicators if embedded inside series string (e.g. "Red Rising Trilogy, 2" or "Red Rising -- #2")
        s = s.replace(
            Regex("(?:[,:\\-—]+\\s*|(?:\\s+))(?:book|vol\\.?|volume|part|no\\.?|#)\\s*(?:\\d+(?:\\.\\d+)?|[ivxlcdm]+)$", RegexOption.IGNORE_CASE),
            ""
        ).replace(
            Regex("[,\\-—]+\\s*\\d+(?:\\.\\d+)?$"),
            ""
        ).trim()

        // Strip leading "The " only when followed by multi-word series or trailing Saga/Trilogy/Series
        val withoutTrailingGeneric = s.replace(TRAILING_SERIES_WORDS_REGEX, "").trim()
        if (withoutTrailingGeneric.isNotEmpty() && withoutTrailingGeneric.any { it.isLetterOrDigit() }) {
            s = withoutTrailingGeneric
        }

        val strippedThe = s.replace(Regex("^the\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (strippedThe.split(' ').size >= 2 || withoutTrailingGeneric != raw.trim()) {
            if (strippedThe.isNotEmpty()) s = strippedThe
        }

        if (s.length < 2) return null
        val letters = s.filter { it.isLetter() }
        if (letters.isNotEmpty() && (letters.all { it.isUpperCase() } || letters.all { it.isLowerCase() })) {
            s = toSmartTitleCase(s)
        }
        return s
    }

    fun canonicalSeriesKey(raw: String?): String? {
        val norm = normalizeSeriesName(raw) ?: return null
        val decomposed = Normalizer.normalize(norm, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return decomposed
            .lowercase()
            .replace(Regex("^the\\s+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Parses series & volume number from an edition `series` string like:
     * `"Red Rising Trilogy, 2"`, `"The Red Rising trilogy -- book III"`, `"Red Rising #4"`.
     */
    fun parseSeriesAndNumberFromEditionString(raw: String): InferredSeries? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val numberMatch = Regex(
            "(?:[,:\\-—]+\\s*|(?:\\s+))(?:(?:book|vol\\.?|volume|part|tome|#)\\s*)?(\\d+(?:\\.\\d+)?|[ivxlcdm]+|[a-z]+)\\s*$",
            RegexOption.IGNORE_CASE
        ).find(trimmed)

        if (numberMatch != null) {
            val token = numberMatch.groupValues[1]
            val parsedNum = parseNumberToken(token)
            if (parsedNum != null && parsedNum in 0.1f..200f) {
                val prefix = trimmed.substring(0, numberMatch.range.first)
                val cleanName = normalizeSeriesName(prefix)
                if (cleanName != null) {
                    return InferredSeries(cleanName, parsedNum)
                }
            }
        }

        val nameOnly = normalizeSeriesName(trimmed) ?: return null
        return InferredSeries(nameOnly, null)
    }

    /**
     * Extracts series info offline from book title, subtitle, filename, or publisher synopsis/description!
     */
    fun inferSeriesFromBookSignals(
        title: String,
        subtitle: String? = null,
        fileName: String = "",
        description: String = ""
    ): InferredSeries? {
        // 1. Check Title & Subtitle parentheticals/brackets:
        // e.g. "Golden Son (Red Rising Saga, Book 2)", "Morning Star (The Red Rising Trilogy #3)", "Red Rising [Red Rising 01]"
        val candidates = listOfNotNull(title, subtitle, fileName.substringBeforeLast('.').replace('_', ' '))
        for (text in candidates) {
            // Pattern A: (Series Name, Book 2) / [Series Name #2] / (Series Name 02)
            val parenMatch = Regex(
                "[(\\[]([^)\\]]+?)(?:[,:\\-—]?\\s+(?:book|vol\\.?|volume|#)\\s*(\\d+(?:\\.\\d+)?|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten)|\\s+#?(\\d+(?:\\.\\d+)?))[)\\]]",
                RegexOption.IGNORE_CASE
            ).find(text)
            if (parenMatch != null) {
                val rawSeries = parenMatch.groupValues[1]
                val numStr = parenMatch.groupValues[2].ifEmpty { parenMatch.groupValues[3] }
                val num = parseNumberToken(numStr)
                val normSeries = normalizeSeriesName(rawSeries)
                if (normSeries != null && num != null) {
                    return InferredSeries(normSeries, num)
                }
            }

            // Pattern B: "Book Two of the Red Rising Trilogy" / "Volume 3 in the Red Rising Saga"
            val bookOfMatch = Regex(
                "\\b(?:book|volume|vol\\.?)\\s+(\\d+(?:\\.\\d+)?|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:of|in)\\s+(?:the\\s+)?([A-Z][A-Za-z0-9'’\\- ]{2,45})",
                RegexOption.IGNORE_CASE
            ).find(text)
            if (bookOfMatch != null) {
                val num = parseNumberToken(bookOfMatch.groupValues[1])
                val normSeries = normalizeSeriesName(bookOfMatch.groupValues[2])
                if (normSeries != null && num != null) {
                    return InferredSeries(normSeries, num)
                }
            }
        }

        // 2. Filename pattern: "Author - Series 02 - Title" or "Series 01 - Title"
        val baseFile = fileName.substringBeforeLast('.').replace('_', ' ').trim()
        val dashParts = baseFile.split(Regex("\\s+-\\s+")).map { it.trim() }
        if (dashParts.size >= 2) {
            for (part in dashParts.dropLast(1)) {
                val m = Regex("^([A-Za-z][A-Za-z0-9'’ ]+?)\\s+#?0*(\\d+(?:\\.\\d+)?)$").find(part)
                if (m != null) {
                    val normSeries = normalizeSeriesName(m.groupValues[1])
                    val num = m.groupValues[2].toFloatOrNull()
                    if (normSeries != null && num != null && num in 0.5f..99f) {
                        return InferredSeries(normSeries, num)
                    }
                }
            }
        }

        // 3. Publisher description blurb:
        // e.g. "Book Two of the New York Times bestselling Red Rising Trilogy"
        // or "the fourth novel in the Red Rising Saga"
        if (description.isNotBlank()) {
            val descSample = description.take(1500)
            val blurbBookOf = Regex(
                "\\b(?:book|volume|part)\\s+(\\d+|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:of|in)\\s+(?:the\\s+)?(?:(?:new york times|internationally|#1)\\s+(?:bestselling|best-selling)\\s+)?([A-Z][A-Za-z0-9'’\\- ]{1,35}?)\\s+(saga|trilogy|series|duology|quartet|tetralogy|chronicles|cycle|sequence)\\b",
                RegexOption.IGNORE_CASE
            ).find(descSample)
            if (blurbBookOf != null) {
                val num = parseNumberToken(blurbBookOf.groupValues[1])
                val normSeries = normalizeSeriesName("${blurbBookOf.groupValues[2]} ${blurbBookOf.groupValues[3]}")
                if (normSeries != null && num != null) {
                    return InferredSeries(normSeries, num)
                }
            }

            val blurbOrdinal = Regex(
                "\\bthe\\s+(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)\\s+(?:book|novel|volume|installment|entry)\\s+(?:of|in)\\s+(?:the\\s+)?(?:(?:new york times|internationally)\\s+(?:bestselling|best-selling)\\s+)?([A-Z][A-Za-z0-9'’\\- ]{1,35}?)\\s+(saga|trilogy|series|duology|quartet|tetralogy|chronicles|cycle|sequence)\\b",
                RegexOption.IGNORE_CASE
            ).find(descSample)
            if (blurbOrdinal != null) {
                val num = parseNumberToken(blurbOrdinal.groupValues[1])
                val normSeries = normalizeSeriesName("${blurbOrdinal.groupValues[2]} ${blurbOrdinal.groupValues[3]}")
                if (normSeries != null && num != null) {
                    return InferredSeries(normSeries, num)
                }
            }
        }

        return null
    }

    /**
     * Strips parenthetical series suffixes from display titles like
     * `"Golden Son (Red Rising Saga, Book 2)"` -> `"Golden Son"`.
     */
    fun cleanBookTitle(rawTitle: String): String {
        val stripped = rawTitle.replace(
            Regex("\\s*[(\\[][^)\\]]*?(?:series|saga|trilogy|book\\s*\\d+|#\\d+)[^)\\]]*?[)\\]]\\s*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        return stripped.ifEmpty { rawTitle.trim() }
    }

    fun parseNumberToken(token: String): Float? {
        val clean = token.trim().lowercase().removePrefix("#")
        clean.toFloatOrNull()?.let { return it }
        return WORD_TO_NUMBER[clean]
    }

    private fun toSmartTitleCase(input: String): String {
        return input.split(' ').joinToString(" ") { word ->
            if (word.isEmpty()) word
            else word.lowercase().replaceFirstChar { it.titlecase() }
        }
    }
}
