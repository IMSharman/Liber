package com.imsharman.liber.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.imsharman.liber.data.local.dao.AnnotationDao
import com.imsharman.liber.data.local.dao.BookDao
import com.imsharman.liber.data.local.dao.CollectionDao
import com.imsharman.liber.data.local.dao.LinkedFolderDao
import com.imsharman.liber.data.local.dao.ReadingSessionDao
import com.imsharman.liber.data.local.entity.BookCollectionCrossRef
import com.imsharman.liber.data.local.entity.BookEntity
import com.imsharman.liber.data.local.entity.BookmarkEntity
import com.imsharman.liber.data.local.entity.CollectionEntity
import com.imsharman.liber.data.local.entity.HighlightEntity
import com.imsharman.liber.data.local.entity.LinkedFolderEntity
import com.imsharman.liber.data.local.entity.ReadingSessionEntity

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        HighlightEntity::class,
        BookmarkEntity::class,
        ReadingSessionEntity::class,
        LinkedFolderEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LiberDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun linkedFolderDao(): LinkedFolderDao

    companion object {
        @Volatile
        private var INSTANCE: LiberDatabase? = null

        fun getInstance(context: Context): LiberDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiberDatabase::class.java,
                    "liber_library.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
