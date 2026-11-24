package com.imsharman.liber.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.imsharman.liber.data.local.entity.BookCollectionCrossRef
import com.imsharman.liber.data.local.entity.BookEntity
import com.imsharman.liber.data.local.entity.BookWithCollections
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.BookmarkWithBook
import com.imsharman.liber.data.local.entity.CollectionEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.local.entity.HighlightWithBook
import com.imsharman.liber.data.local.entity.LinkedFolderEntity
import com.imsharman.liber.data.local.entity.ReadingSessionEntity
import com.imsharman.liber.data.local.entity.ReadingSessionWithBook
import com.imsharman.liber.domain.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    @Query("SELECT * FROM books ORDER BY COALESCE(lastReadAtEpochMs, addedAtEpochMs) DESC")
    fun observeAllBooksWithCollections(): Flow<List<BookWithCollections>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookWithCollections(bookId: Long): Flow<BookWithCollections?>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE uriString = :uriString LIMIT 1")
    suspend fun getBookByUri(uriString: String): BookEntity?

    @Query("SELECT * FROM books WHERE contentFingerprint = :fingerprint LIMIT 1")
    suspend fun getBookByFingerprint(fingerprint: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAllBooksSnapshot(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)

    @Query("DELETE FROM books WHERE linkedFolderUri = :folderUri")
    suspend fun deleteBooksFromLinkedFolder(folderUri: String)

    @Query(
        """
        UPDATE books SET
            currentChapterIndex = :chapterIndex,
            currentBlockIndex = :blockIndex,
            currentScrollOffset = :scrollOffset,
            currentPageIndex = :pageIndex,
            totalUnits = :totalUnits,
            progressFraction = :progressFraction,
            readingStatus = :status,
            lastReadAtEpochMs = :nowMs,
            finishedAtEpochMs = CASE WHEN :status = 'HAVE_READ' THEN COALESCE(finishedAtEpochMs, :nowMs) ELSE finishedAtEpochMs END,
            updatedAtEpochMs = :nowMs
        WHERE id = :bookId
        """
    )
    suspend fun updateReadingPosition(
        bookId: Long,
        chapterIndex: Int,
        blockIndex: Int,
        scrollOffset: Int,
        pageIndex: Int,
        totalUnits: Int,
        progressFraction: Float,
        status: ReadingStatus,
        nowMs: Long
    )

    @Query("UPDATE books SET isFavorite = :favorite, updatedAtEpochMs = :nowMs WHERE id = :bookId")
    suspend fun setFavorite(bookId: Long, favorite: Boolean, nowMs: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE books SET
            readingStatus = :status,
            finishedAtEpochMs = CASE WHEN :status = 'HAVE_READ' THEN COALESCE(finishedAtEpochMs, :nowMs) ELSE NULL END,
            progressFraction = CASE WHEN :status = 'HAVE_READ' THEN 1.0 ELSE progressFraction END,
            updatedAtEpochMs = :nowMs
        WHERE id = :bookId
        """
    )
    suspend fun setReadingStatus(bookId: Long, status: ReadingStatus, nowMs: Long = System.currentTimeMillis())

    @Query("UPDATE books SET personalRating = :rating, updatedAtEpochMs = :nowMs WHERE id = :bookId")
    suspend fun setRating(bookId: Long, rating: Int, nowMs: Long = System.currentTimeMillis())
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections")
    suspend fun getAllCollectionsSnapshot(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun getCollectionByName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToCollection(ref: BookCollectionCrossRef)

    @Query("DELETE FROM book_collection_cross_ref WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long)

    @Query("SELECT * FROM book_collection_cross_ref")
    suspend fun getAllCrossRefsSnapshot(): List<BookCollectionCrossRef>
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY chapterIndex ASC, blockIndex ASC, pageIndex ASC, createdAtEpochMs ASC")
    fun observeHighlightsForBook(bookId: Long): Flow<List<HighlightEntity>>

    @Transaction
    @Query("SELECT * FROM highlights ORDER BY createdAtEpochMs DESC")
    fun observeAllHighlightsWithBook(): Flow<List<HighlightWithBook>>

    @Query("SELECT * FROM highlights")
    suspend fun getAllHighlightsSnapshot(): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE uuid = :uuid LIMIT 1")
    suspend fun getHighlightByUuid(uuid: String): HighlightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHighlight(highlight: HighlightEntity): Long

    @Query("DELETE FROM highlights WHERE id = :highlightId")
    suspend fun deleteHighlightById(highlightId: Long)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex ASC, pageIndex ASC, blockIndex ASC")
    fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Transaction
    @Query("SELECT * FROM bookmarks ORDER BY createdAtEpochMs DESC")
    fun observeAllBookmarksWithBook(): Flow<List<BookmarkWithBook>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksSnapshot(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE uuid = :uuid LIMIT 1")
    suspend fun getBookmarkByUuid(uuid: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)
}

@Dao
interface ReadingSessionDao {
    @Transaction
    @Query("SELECT * FROM reading_sessions ORDER BY startedAtEpochMs DESC")
    fun observeAllSessionsWithBook(): Flow<List<ReadingSessionWithBook>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAtEpochMs DESC")
    fun observeSessionsForBook(bookId: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions")
    suspend fun getAllSessionsSnapshot(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE uuid = :uuid LIMIT 1")
    suspend fun getSessionByUuid(uuid: String): ReadingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: ReadingSessionEntity): Long
}

@Dao
interface LinkedFolderDao {
    @Query("SELECT * FROM linked_folders ORDER BY displayName COLLATE NOCASE ASC")
    fun observeLinkedFolders(): Flow<List<LinkedFolderEntity>>

    @Query("SELECT * FROM linked_folders")
    suspend fun getLinkedFoldersSnapshot(): List<LinkedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinkedFolder(folder: LinkedFolderEntity): Long

    @Query("DELETE FROM linked_folders WHERE treeUriString = :treeUri")
    suspend fun deleteLinkedFolderByUri(treeUri: String)
}
