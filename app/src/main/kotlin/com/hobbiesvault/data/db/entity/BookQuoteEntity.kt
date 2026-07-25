package com.hobbiesvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hobbiesvault.model.BookQuote
import java.util.Date

@Entity(tableName = "book_quotes")
data class BookQuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "media_item_id") val mediaItemId: Int,
    @ColumnInfo(name = "citacao")       val quote: String,
    @ColumnInfo(name = "comentario")    val comment: String?,
    @ColumnInfo(name = "criado_em_ms")  val createdAtMs: Long,
) {
    fun toDomain() = BookQuote(
        id        = id,
        quote     = quote,
        comment   = comment,
        createdAt = Date(createdAtMs),
    )
}
