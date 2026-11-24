package com.imsharman.liber.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.HighlightColor
import com.imsharman.liber.domain.model.ReadingStatus

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["uriString"], unique = true),
        Index(value = ["contentFingerprint"]),
        Index(value = ["author"]),
        Index(value = ["seriesName"]),
        Index(value = ["linkedFolderUri"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val uriString: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val contentFingerprint: String,
    val format: BookFormat,
    val title: String,
    val author: String,
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val description: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val language: String = "",
    val tagsCsv: String = "",
    /**
     * Always points to a file inside context.filesDir/covers/ (app-private internal storage)
     * or null when using a generated typographic cover. Never points to public external storage.
     */
    val internalCoverPath: String? = null,
    val coverSeedColorArgb: Long = 0xFF2B4C59,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val isFavorite: Boolean = false,
    val personalRating: Int = 0,
    val currentChapterIndex: Int = 0,
    val currentBlockIndex: Int = 0,
    val currentScrollOffset: Int = 0,
    val currentPageIndex: Int = 0,
    val totalUnits: Int = 1,
    val totalWords: Int = 0,
    val progressFraction: Float = 0f,
    val linkedFolderUri: String? = null,
    val relativeFolderPath: String = "",
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val lastReadAtEpochMs: Long? = null,
    val finishedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_collection_cross_ref",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["collectionId"])
    ]
)
data class BookCollectionCrossRef(
    val bookId: Long,
    val collectionId: Long
)

data class BookWithCollections(
    @Embedded val book: BookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookCollectionCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "collectionId"
        )
    )
    val collections: List<CollectionEntity>
)

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val uuid: String,
    val bookId: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    val blockIndex: Int,
    val pageIndex: Int = 0,
    val quoteText: String,
    val noteText: String = "",
    val color: HighlightColor = HighlightColor.AMBER,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class HighlightWithBook(
    @Embedded val highlight: HighlightEntity,
    @Relation(
        parentColumn = "bookId",
        entityColumn = "id"
    )
    val book: BookEntity
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val uuid: String,
    val bookId: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    val blockIndex: Int,
    val pageIndex: Int,
    val progressFraction: Float,
    val label: String,
    val snippet: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class BookmarkWithBook(
    @Embedded val bookmark: BookmarkEntity,
    @Relation(
        parentColumn = "bookId",
        entityColumn = "id"
    )
    val book: BookEntity
)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["dateIso"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val uuid: String,
    val bookId: Long,
    val dateIso: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val pagesTurned: Int,
    val wordsRead: Int
)

data class ReadingSessionWithBook(
    @Embedded val session: ReadingSessionEntity,
    @Relation(
        parentColumn = "bookId",
        entityColumn = "id"
    )
    val book: BookEntity
)

@Entity(
    tableName = "linked_folders",
    indices = [Index(value = ["treeUriString"], unique = true)]
)
data class LinkedFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val treeUriString: String,
    val displayName: String,
    val bookCount: Int = 0,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val lastScannedEpochMs: Long = System.currentTimeMillis()
)
