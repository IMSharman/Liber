# Liber

**Liber** is a modern, privacy-first native Android ebook, comic, and document reader built with **Kotlin** and **Jetpack Compose**. Inspired by the daily workflow of **ReadEra**—while upgrading the design, unlocking premium features for free, and enforcing strict file-access boundaries.

---

## Strict Privacy & Zero Unsolicited File Access

Unlike traditional readers that request broad `MANAGE_EXTERNAL_STORAGE` or media permissions, scan your entire device in the background, or litter public storage with external `Cover` folders containing unrelated device images:

- **Zero Broad Storage or Media Permissions**: Liber declares **no** `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, or `READ_MEDIA_IMAGES` permissions.
- **Explicit SAF Access Only**: Liber only touches files or folders you explicitly grant via Android's Storage Access Framework (`OpenMultipleDocuments` or `OpenDocumentTree`).
- **Book-Extension Filtering**: When scanning a linked book folder, Liber strictly reads recognized book formats and ignores standalone `.jpg`/`.png`/`.webp` photos or system files.
- **App-Private Cover Sandbox**: Book covers are extracted strictly from *inside* the book container itself (or explicitly selected by you in the Metadata Editor) and stored inside private app storage (`filesDir/covers/`), never creating external folders on `/sdcard`.
- **Offline-Only Switch**: Includes a one-tap **Strict Offline-Only Mode** in Settings that blocks all network requests (opt-in Open Library metadata lookup and WebDAV sync).

---

## Supported Formats

| Category | Formats | Capabilities |
| :--- | :--- | :--- |
| **Reflowable Ebooks** | `EPUB` (EPUB 2 & 3), `MOBI`, `AZW3` / `AZW`, `FB2` / `FB2.ZIP` | Full typography studio, paginated flip & continuous scroll, TOC, embedded covers, series detection, highlights, notes, search, TTS |
| **Documents** | `PDF`, `DOCX`, `TXT`, `MD` / `MARKDOWN` | Hardware-accelerated `PdfRenderer` zoom + `PdfBox` outline TOC, **PDF Text Reflow Mode**, night/AMOLED page invert, YAML frontmatter |
| **Comics & Manga** | `CBZ`, `CBR` | Natural alphanumeric page sorting, `ComicInfo.xml` series metadata, pinch-to-zoom, Western LTR & **Manga RTL** reading modes |

---

## Core Features & Improvements Over ReadEra

### 1. Library Shelves & Auto-Grouping
- **ReadEra Shelves**: *Reading Now*, *Books & Documents*, *Favorites*, *To Read*, *Have Read*, *Authors*, *Series* (sorted automatically by `#1, #2, ...`), *Collections* (unlimited custom shelves), and *Scoped Folders*.
- **Three View Modes**: ReadEra-style *Detailed Cards* (cover, clickable author & series badges, collection chips, progress bar, quick actions), *Cover Grid*, and *Compact List*.

### 2. Deep Reader Customization & Themes
- **Editorial & True AMOLED Themes**: *Warm Paper*, *Crisp Day*, *Classic Sepia*, *Dusk Slate*, *Console*, *AMOLED Pure (`#000000`)*, *AMOLED Night Amber*, plus a **Custom Hex Palette Studio**.
- **Typography Unlocked**: Custom `.ttf` / `.otf` font importing, font size, font weight (`300`–`700`), line spacing, side/vertical margins, paragraph spacing, text justification, automatic hyphenation, first-line indent, and **Bionic Reading** bold prefixes.
- **Touch Tap Zones & Gestures**: Configurable tap zone layouts (*ReadEra Classic Sides*, *Vertical Split*, *One-Handed Thumb*), **left-edge vertical swipe for screen brightness** (including extra-dim night overlay), hardware volume-key page turning, and a **Jump-Back Pill** after scrubbing or using TOC links.

### 3. Free Built-In Annotations, TTS & Obsidian Export
- **5-Color Highlights & Notes**: Long-press any paragraph to highlight in *Amber*, *Emerald*, *Sky*, *Violet*, or *Coral*, attach Markdown notes, or copy quotes.
- **Obsidian / Markdown Export**: Export single-book or library-wide highlights, notes, and bookmarks as clean `.md` files with YAML frontmatter and `> [!quote]` callout blocks.
- **Offline Text-to-Speech (TTS)**: Live paragraph highlighting, adjustable speech rate (`0.8x`–`2.0x`), chapter auto-advance, and a **Sleep Timer** (`15m`, `30m`, `45m`, or *End of Chapter*).

### 4. Metadata Editor, Reading Stats & Sync
- **In-App Metadata & Cover Editor**: Edit Title, Author, Series Name & `#`, Synopsis, ISBN, Publisher, and Tags—with one-tap **Open Library** automatic book info & cover lookup.
- **Reading Statistics & Streaks**: Daily reading streak, customizable daily goal ring, **12-week reading activity heatmap**, reading speed (**WPM**), and session logs.
- **Backup & Cross-Device Sync**: Portable local `.liberbackup.zip` archive backup/restore and **WebDAV** cross-device sync (matches books across devices via SHA-256 content fingerprints).

---

## Building Locally

### Prerequisites
- **JDK 17+** (JDK 21 recommended)
- **Android SDK** (`compileSdk = 35`, `minSdk = 26`)

### Commands
```bash
# Run unit tests (privacy filter, EPUB/FB2/MOBI/DOCX/Markdown parsers, streaks & Markdown export)
./gradlew testDebugUnitTest

# Build installable debug APK
./gradlew assembleDebug
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`. Every GitHub Actions run also uploads `Liber-debug-apk` as a downloadable build artifact.
