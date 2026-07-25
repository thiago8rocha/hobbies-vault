package com.hobbiesvault.data.db

import android.content.Context
import androidx.room.Room
import com.hobbiesvault.data.repository.MediaCacheRepository
import com.hobbiesvault.data.repository.MediaRepository

object DB {
    private lateinit var _db: AppDatabase

    fun init(context: Context) {
        _db = Room.databaseBuilder(context, AppDatabase::class.java, "media_tracker.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
            )
            .build()
    }

    val database get() = _db
    val repo     get() = MediaRepository(_db.mediaItemDao(), _db.movieListDao(), _db.seriesEpisodeDao(), _db.mangaReviewDao())
    val cache    get() = MediaCacheRepository(_db.mediaDetailsCacheDao())
    val games    get() = _db.gameCacheDao()
}
