package com.hobbiesvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hobbiesvault.model.GamePlaythrough
import java.util.Date

@Entity(tableName = "game_playthroughs")
data class GamePlaythroughEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "media_item_id")  val mediaItemId: Int,
    @ColumnInfo(name = "titulo")         val title: String,
    @ColumnInfo(name = "data_inicio_ms") val startDateMs: Long?,
    @ColumnInfo(name = "data_fim_ms")    val endDateMs: Long?,
    @ColumnInfo(name = "horas_jogadas")  val hoursPlayed: Int?,
    @ColumnInfo(name = "notas")          val notes: String?,
) {
    fun toDomain() = GamePlaythrough(
        id          = id,
        title       = title,
        startDate   = startDateMs?.let { Date(it) },
        endDate     = endDateMs?.let { Date(it) },
        hoursPlayed = hoursPlayed,
        notes       = notes,
    )
}
