package com.hobbiesvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hobbiesvault.data.db.entity.GamePlaythroughEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GamePlaythroughDao {
    @Query("SELECT * FROM game_playthroughs WHERE media_item_id = :mediaItemId ORDER BY data_inicio_ms DESC")
    fun watchByItem(mediaItemId: Int): Flow<List<GamePlaythroughEntity>>

    @Insert
    suspend fun insert(entity: GamePlaythroughEntity)

    @Update
    suspend fun update(entity: GamePlaythroughEntity)

    @Query("DELETE FROM game_playthroughs WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM game_playthroughs WHERE media_item_id = :mediaItemId")
    suspend fun deleteByItem(mediaItemId: Int)
}
