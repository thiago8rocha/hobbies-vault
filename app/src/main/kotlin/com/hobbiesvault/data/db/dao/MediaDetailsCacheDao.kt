package com.hobbiesvault.data.db.dao

import androidx.room.*
import com.hobbiesvault.data.db.entity.MediaDetailsCacheEntity

@Dao
interface MediaDetailsCacheDao {
    @Query("SELECT * FROM media_details_cache WHERE media_item_id = :mediaItemId")
    suspend fun getById(mediaItemId: Int): MediaDetailsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: MediaDetailsCacheEntity)

    @Query("DELETE FROM media_details_cache WHERE media_item_id = :mediaItemId")
    suspend fun delete(mediaItemId: Int)

    @Query("SELECT * FROM media_details_cache")
    suspend fun getAll(): List<MediaDetailsCacheEntity>

    @Query("DELETE FROM media_details_cache")
    suspend fun deleteAll()
}
