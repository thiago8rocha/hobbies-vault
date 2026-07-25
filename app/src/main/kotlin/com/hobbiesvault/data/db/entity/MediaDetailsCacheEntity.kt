package com.hobbiesvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_details_cache")
data class MediaDetailsCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_item_id") val mediaItemId: Int,
    @ColumnInfo(name = "dados_json")            val dataJson: String,
    @ColumnInfo(name = "ultima_verificacao_ms") val lastCheckedMs: Long,
)
