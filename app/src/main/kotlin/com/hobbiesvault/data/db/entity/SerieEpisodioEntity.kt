package com.hobbiesvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "serie_episodios_assistidos",
    primaryKeys = ["media_item_id", "temporada", "episodio"],
)
data class SeriesEpisodeEntity(
    @ColumnInfo(name = "media_item_id")   val mediaItemId: Int,
    @ColumnInfo(name = "temporada")       val season: Int,
    @ColumnInfo(name = "episodio")        val episode: Int,
    @ColumnInfo(name = "assistido_em_ms") val watchedAtMs: Long,
    @ColumnInfo(name = "nome_episodio")   val episodeName: String? = null,
    @ColumnInfo(name = "nome_serie")      val seriesName: String? = null,
    @ColumnInfo(name = "capa_url")        val coverUrl: String? = null,
)
