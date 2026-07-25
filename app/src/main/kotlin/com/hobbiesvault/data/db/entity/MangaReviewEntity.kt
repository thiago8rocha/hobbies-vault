package com.hobbiesvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hobbiesvault.model.MangaReview
import java.util.Date

@Entity(tableName = "manga_reviews")
data class MangaReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "media_item_id")   val mediaItemId: Int,
    @ColumnInfo(name = "nota")            val rating: Double?,
    @ColumnInfo(name = "titulo_resenha")  val reviewTitle: String?,
    @ColumnInfo(name = "resenha")         val reviewText: String?,
    @ColumnInfo(name = "concluido_em_ms") val completedAtMs: Long,
) {
    fun toDomain() = MangaReview(
        rating      = rating,
        reviewTitle = reviewTitle,
        reviewText  = reviewText,
        completedAt = Date(completedAtMs),
    )
}
