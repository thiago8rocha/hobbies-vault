package com.hobbiesvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hobbiesvault.data.db.entity.BookQuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookQuoteDao {
    @Query("SELECT * FROM book_quotes WHERE media_item_id = :mediaItemId ORDER BY criado_em_ms DESC")
    suspend fun getByItem(mediaItemId: Int): List<BookQuoteEntity>

    @Query("SELECT * FROM book_quotes WHERE media_item_id = :mediaItemId ORDER BY criado_em_ms DESC")
    fun watchByItem(mediaItemId: Int): Flow<List<BookQuoteEntity>>

    @Insert
    suspend fun insert(entity: BookQuoteEntity)

    @Query("DELETE FROM book_quotes WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM book_quotes WHERE media_item_id = :mediaItemId")
    suspend fun deleteByItem(mediaItemId: Int)
}
