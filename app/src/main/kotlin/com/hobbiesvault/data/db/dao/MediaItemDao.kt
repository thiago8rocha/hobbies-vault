package com.hobbiesvault.data.db.dao

import androidx.room.*
import com.hobbiesvault.data.db.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE tipo = :type")
    suspend fun getByType(type: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE tipo IN (:types)")
    suspend fun getByTypes(types: List<String>): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Int): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE tipo = :type")
    fun watchByType(type: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE tipo IN (:types)")
    fun watchByTypes(types: List<String>): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items")
    fun watchAll(): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MediaItemEntity): Long

    @Update
    suspend fun update(entity: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()
}
