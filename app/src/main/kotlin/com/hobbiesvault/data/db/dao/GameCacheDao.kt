package com.hobbiesvault.data.db.dao

import androidx.room.*
import com.hobbiesvault.data.db.entity.GameCacheEntity

@Dao
interface GameCacheDao {
    @Query("SELECT * FROM game_cache WHERE lower(name) LIKE '%' || lower(:query) || '%' ORDER BY release_date DESC LIMIT 100")
    suspend fun search(query: String): List<GameCacheEntity>

    @Query("SELECT * FROM game_cache WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun getByName(name: String): GameCacheEntity?

    @Query("SELECT * FROM game_cache WHERE gb_id = :gbId LIMIT 1")
    suspend fun getByGbId(gbId: Int): GameCacheEntity?

    @Query("SELECT * FROM game_cache WHERE igdb_id = :igdbId LIMIT 1")
    suspend fun getByIgdbId(igdbId: Int): GameCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: GameCacheEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveAll(entities: List<GameCacheEntity>)

    @Query("SELECT COUNT(*) FROM game_cache")
    suspend fun count(): Int

    @Query("""
        SELECT * FROM game_cache WHERE
        (igdb_id IS NOT NULL AND capa_ok = 0 AND updated_at < :sevenDaysAgo) OR
        (igdb_id IS NOT NULL AND capa_ok = 1 AND updated_at < :thirtyDaysAgo)
        LIMIT 50
    """)
    suspend fun pendingUpdate(thirtyDaysAgo: Long, sevenDaysAgo: Long): List<GameCacheEntity>
}
