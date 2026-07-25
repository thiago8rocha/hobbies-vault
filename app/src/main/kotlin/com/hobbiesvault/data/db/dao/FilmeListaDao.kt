package com.hobbiesvault.data.db.dao

import androidx.room.*
import com.hobbiesvault.data.db.entity.MovieListEntity
import com.hobbiesvault.data.db.entity.MovieListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieListDao {
    @Query("SELECT * FROM filme_listas ORDER BY nome ASC")
    fun watchLists(): Flow<List<MovieListEntity>>

    @Query("SELECT * FROM filme_listas ORDER BY nome ASC")
    suspend fun getAll(): List<MovieListEntity>

    @Insert
    suspend fun create(entity: MovieListEntity): Long

    @Query("UPDATE filme_listas SET nome = :newName WHERE id = :id")
    suspend fun rename(id: Int, newName: String)

    @Query("UPDATE filme_listas SET nome = :name, descricao = :description WHERE id = :id")
    suspend fun update(id: Int, name: String, description: String?)

    @Query("DELETE FROM filme_lista_itens WHERE lista_id = :id")
    suspend fun deleteItems(id: Int)

    @Query("DELETE FROM filme_listas WHERE id = :id")
    suspend fun deleteList(id: Int)

    @Transaction
    suspend fun delete(id: Int) {
        deleteItems(id)
        deleteList(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(entity: MovieListItemEntity)

    @Query("DELETE FROM filme_lista_itens WHERE lista_id = :listId AND media_item_id = :mediaItemId")
    suspend fun removeItem(listId: Int, mediaItemId: Int)

    @Query("DELETE FROM filme_lista_itens WHERE media_item_id = :mediaItemId")
    suspend fun removeItemFromAll(mediaItemId: Int)

    @Query("SELECT media_item_id FROM filme_lista_itens WHERE lista_id = :listId")
    suspend fun mediaItemIdsOfList(listId: Int): List<Int>

    @Query("SELECT lista_id FROM filme_lista_itens WHERE media_item_id = :mediaItemId")
    suspend fun listIdsOfItem(mediaItemId: Int): List<Int>

    @Query("SELECT * FROM filme_lista_itens WHERE lista_id = :listId")
    fun watchListItems(listId: Int): Flow<List<MovieListItemEntity>>

    @Query("SELECT * FROM filme_lista_itens")
    fun watchAllListItems(): Flow<List<MovieListItemEntity>>

    @Transaction
    suspend fun deleteAllLists() {
        deleteAllListItems()
        deleteAllMovieLists()
    }

    @Query("DELETE FROM filme_lista_itens")
    suspend fun deleteAllListItems()

    @Query("DELETE FROM filme_listas")
    suspend fun deleteAllMovieLists()
}
