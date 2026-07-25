package com.hobbiesvault.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hobbiesvault.data.db.dao.MediaDetailsCacheDao
import com.hobbiesvault.data.db.entity.MediaDetailsCacheEntity
import java.util.Date

class MediaCacheRepository(private val dao: MediaDetailsCacheDao) {
    private val gson = Gson()

    suspend fun load(mediaItemId: Int): Map<String, Any?>? {
        val entity = dao.getById(mediaItemId) ?: return null
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(entity.dataJson, type)
        } catch (_: Exception) { null }
    }

    suspend fun save(mediaItemId: Int, data: Map<String, Any?>) {
        dao.save(
            MediaDetailsCacheEntity(
                mediaItemId  = mediaItemId,
                dataJson     = gson.toJson(data),
                lastCheckedMs = Date().time,
            )
        )
    }

    suspend fun delete(mediaItemId: Int) = dao.delete(mediaItemId)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAll(): List<MediaDetailsCacheEntity> = dao.getAll()
}
