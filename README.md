# Liber

**Liber** is a clean, offline-first ebook, comic, and document reader for Android, built with Kotlin and Jetpack Compose.

## Supported Formats

- **Ebooks**: `EPUB` (EPUB 2 & 3), `MOBI`, `AZW3` / `AZW`, `FB2` / `FB2.ZIP`
- **Documents**: `PDF`, `DOCX`, `TXT`, `MD` / `Markdown`
- **Comics & Manga**: `CBZ`, `CBR`

## Features

- **Library Organization**: Shelves for *Reading Now*, *Favorites*, *To Read*, and *Have Read*, plus automatic *Authors* and *Series* grouping, custom *Collections*, and linked local folders.
- **Customizable Reader**: Paginated page-flip and continuous scroll modes, color themes (*Warm Paper*, *Crisp Day*, *Sepia*, *Dusk Slate*, *Console*, *True AMOLED Black*, and custom hex palettes), custom `.ttf`/`.otf` fonts, adjustable margins and line spacing, left-edge swipe for brightness, and volume-key page turns.
- **PDF & Comic Support**: Pinch-to-zoom, dark/AMOLED page color filters, optional PDF text reflow mode, and Western (LTR) or Manga (RTL) reading directions.
- **Highlights, Notes & TTS**: Multi-color highlights, personal notes, bookmarks, in-book full-text search, Markdown/Obsidian notes export, and offline Text-to-Speech (TTS) with a sleep timer.
- **Stats, Metadata & Sync**: Reading streaks, a 12-week activity heatmap, reading speed (WPM), an in-app metadata/cover editor with Open Library lookup, `.liberbackup.zip` archives, and WebDAV cross-device sync.

## Installation

Download the latest **`Liber-vX.Y.Z.apk`** from the [Releases](https://github.com/IMSharman/Liber/releases) page and open it on your Android device (Android 8.0 / API 26+).

## Building from Source

Requires **JDK 17+** (JDK 21 recommended) and the **Android SDK** (`compileSdk = 35`).

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```
