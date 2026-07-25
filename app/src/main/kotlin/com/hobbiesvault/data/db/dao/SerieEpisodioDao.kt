package com.hobbiesvault.data.db.dao

import androidx.room.*
import com.hobbiesvault.data.db.entity.SeriesEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesEpisodeDao {
    @Query("SELECT * FROM serie_episodios_assistidos WHERE media_item_id = :mediaItemId ORDER BY temporada ASC, episodio ASC")
    suspend fun getBySeries(mediaItemId: Int): List<SeriesEpisodeEntity>

    @Query("SELECT * FROM serie_episodios_assistidos WHERE media_item_id = :mediaItemId ORDER BY temporada ASC, episodio ASC")
    fun watchBySeries(mediaItemId: Int): Flow<List<SeriesEpisodeEntity>>

    @Query("SELECT * FROM serie_episodios_assistidos ORDER BY assistido_em_ms DESC")
    suspend fun getAll(): List<SeriesEpisodeEntity>

    @Query("SELECT * FROM serie_episodios_assistidos ORDER BY assistido_em_ms DESC")
    fun watchAll(): Flow<List<SeriesEpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(entity: SeriesEpisodeEntity)

    @Query("DELETE FROM serie_episodios_assistidos WHERE media_item_id = :mediaItemId AND temporada = :season AND episodio = :episode")
    suspend fun unmark(mediaItemId: Int, season: Int, episode: Int)

    @Query("DELETE FROM serie_episodios_assistidos WHERE media_item_id = :mediaItemId")
    suspend fun deleteBySeries(mediaItemId: Int)

    @Query("DELETE FROM serie_episodios_assistidos")
    suspend fun deleteAll()
}
