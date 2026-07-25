package com.hobbiesvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filme_listas")
data class MovieListEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "nome")         val name: String,
    @ColumnInfo(name = "criada_em_ms") val createdAtMs: Long,
    @ColumnInfo(name = "descricao")    val description: String? = null,
)

@Entity(
    tableName = "filme_lista_itens",
    primaryKeys = ["lista_id", "media_item_id"],
)
data class MovieListItemEntity(
    @ColumnInfo(name = "lista_id")      val listId: Int,
    @ColumnInfo(name = "media_item_id") val mediaItemId: Int,
)
