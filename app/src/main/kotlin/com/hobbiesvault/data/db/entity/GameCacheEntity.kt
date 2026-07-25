package com.hobbiesvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_cache")
data class GameCacheEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "gb_id")         val gbId: Int? = null,
    @ColumnInfo(name = "igdb_id")       val igdbId: Int? = null,
    @ColumnInfo(name = "release_date")  val releaseDate: String? = null,
    @ColumnInfo(name = "deck")          val deck: String? = null,
    @ColumnInfo(name = "capa_url")      val coverUrl: String? = null,
    @ColumnInfo(name = "summary")       val summary: String? = null,
    @ColumnInfo(name = "storyline")     val storyline: String? = null,
    @ColumnInfo(name = "generos")       val genres: String? = null,
    @ColumnInfo(name = "plataformas")   val platforms: String? = null,
    @ColumnInfo(name = "updated_at")    val updatedAt: Long? = null,
    @ColumnInfo(name = "capa_ok", defaultValue = "0") val coverOk: Boolean = false,
)
