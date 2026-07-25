package com.hobbiesvault.data.db.dao

import androidx.room.*
import com.hobbiesvault.data.db.entity.MangaReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaReviewDao {
    @Query("SELECT * FROM manga_reviews WHERE media_item_id = :mediaItemId ORDER BY concluido_em_ms DESC")
    suspend fun getByItem(mediaItemId: Int): List<MangaReviewEntity>

    @Query("SELECT * FROM manga_reviews WHERE media_item_id = :mediaItemId ORDER BY concluido_em_ms DESC")
    fun watchByItem(mediaItemId: Int): Flow<List<MangaReviewEntity>>

    @Insert
    suspend fun insert(entity: MangaReviewEntity)

    @Query("DELETE FROM manga_reviews WHERE media_item_id = :mediaItemId")
    suspend fun deleteByItem(mediaItemId: Int)
}
