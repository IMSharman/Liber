package com.imsharman.liber.data.local

import androidx.room.TypeConverter
import com.imsharman.liber.domain.model.BookFormat
import com.imsharman.liber.domain.model.HighlightColor
import com.imsharman.liber.domain.model.ReadingStatus

class Converters {
    @TypeConverter
    fun fromBookFormat(value: BookFormat): String = value.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat =
        runCatching { BookFormat.valueOf(value) }.getOrDefault(BookFormat.EPUB)

    @TypeConverter
    fun fromReadingStatus(value: ReadingStatus): String = value.name

    @TypeConverter
    fun toReadingStatus(value: String): ReadingStatus =
        runCatching { ReadingStatus.valueOf(value) }.getOrDefault(ReadingStatus.UNREAD)

    @TypeConverter
    fun fromHighlightColor(value: HighlightColor): String = value.name

    @TypeConverter
    fun toHighlightColor(value: String): HighlightColor =
        runCatching { HighlightColor.valueOf(value) }.getOrDefault(HighlightColor.AMBER)
}
