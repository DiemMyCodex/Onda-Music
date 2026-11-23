package com.onda.music.core.database

import androidx.room.TypeConverter
import com.onda.music.core.model.DownloadStatus
import com.onda.music.core.model.LyricsSource
import com.onda.music.core.model.SourceKind

class DatabaseConverters {
    @TypeConverter
    fun fromSourceKind(value: SourceKind): String = value.name

    @TypeConverter
    fun toSourceKind(raw: String): SourceKind {
        return runCatching { SourceKind.valueOf(raw) }.getOrDefault(SourceKind.YOUTUBE)
    }

    @TypeConverter
    fun fromLyricsSource(value: LyricsSource): String = value.name

    @TypeConverter
    fun toLyricsSource(raw: String): LyricsSource {
        return runCatching { LyricsSource.valueOf(raw) }.getOrDefault(LyricsSource.LRCLIB)
    }

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(raw: String): DownloadStatus {
        return runCatching { DownloadStatus.valueOf(raw) }.getOrDefault(DownloadStatus.QUEUED)
    }
}
