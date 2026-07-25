package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hobbiesvault.model.ApiSearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Date

class MangaDexService {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://api.mangadex.org"

    private fun get(url: String): Map<String, Any?> {
        val req = Request.Builder().url(url)
            .addHeader("Accept", "application/json")
            .build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(resp, type) ?: emptyMap()
    }

    fun search(query: String): List<ApiSearchResult> {
        if (query.isBlank()) return emptyList()
        val enc = URLEncoder.encode(query, "UTF-8")
        val url = "$base/manga?title=$enc&limit=10&includes[]=cover_art&includes[]=author" +
            "&contentRating[]=safe&contentRating[]=suggestive"
        return (get(url)["data"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.mapNotNull { mapItem(it) } ?: emptyList()
    }

    fun getDetailsById(mangaDexId: String): Map<String, Any?>? {
        val url = "$base/manga/$mangaDexId?includes[]=cover_art&includes[]=author&includes[]=artist"
        val manga = (get(url)["data"] as? Map<*, *>) ?: return null
        val attrs = manga["attributes"] as? Map<*, *> ?: return null
        val relationships = (manga["relationships"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

        val titleMap = attrs["title"] as? Map<*, *>
        val altTitles = (attrs["altTitles"] as? List<*>)?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { it.values.firstOrNull() as? String }
            ?.distinct()

        val descMap = attrs["description"] as? Map<*, *>
        val synopsis = (descMap?.get("en") as? String) ?: (descMap?.values?.firstOrNull() as? String)

        val tags = (attrs["tags"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.filter { ((it["attributes"] as? Map<*, *>)?.get("group") as? String) == "genre" }
            ?.mapNotNull { ((it["attributes"] as? Map<*, *>)?.get("name") as? Map<*, *>)?.get("en") as? String }

        val authors = relationships
            .filter { it["type"] == "author" || it["type"] == "artist" }
            .mapNotNull { (it["attributes"] as? Map<*, *>)?.get("name") as? String }
            .distinct()

        val coverFileName = relationships
            .firstOrNull { it["type"] == "cover_art" }
            ?.let { (it["attributes"] as? Map<*, *>)?.get("fileName") as? String }
        val coverUrl = coverFileName?.let { "https://uploads.mangadex.org/covers/$mangaDexId/$it.512.jpg" }

        val serializationStatus = when (attrs["status"] as? String) {
            "ongoing"   -> "Em andamento"
            "completed" -> "Finalizado"
            "hiatus"    -> "Em hiato"
            "cancelled" -> "Cancelado"
            else        -> attrs["status"] as? String
        }

        val year = (attrs["year"] as? Double)?.toInt()
        val startDateMs = year?.let { runCatching { Date(it - 1900, 0, 1).time }.getOrNull() }

        return buildMap {
            put("title", titleMap?.get("en") ?: titleMap?.values?.firstOrNull())
            put("coverUrl", coverUrl)
            put("synopsis", synopsis)
            put("synonyms", altTitles)
            put("genres", tags)
            put("authors", authors.takeIf { it.isNotEmpty() })
            put("serializationStatus", serializationStatus)
            put("startDateMs", startDateMs)
        }
    }

    fun getLatestChapterNumber(mangaDexId: String): Double? {
        val langs = listOf("en", "pt-br")
        for (lang in langs) {
            val url = "$base/manga/$mangaDexId/aggregate?translatedLanguage[]=$lang"
            val volumes = (get(url)["volumes"] as? Map<*, *>) ?: continue
            val max = volumes.values.filterIsInstance<Map<*, *>>()
                .mapNotNull { it["chapters"] as? Map<*, *> }
                .flatMap { it.keys }
                .mapNotNull { (it as? String)?.toDoubleOrNull() }
                .maxOrNull()
            if (max != null) return max
        }
        return null
    }

    private fun mapItem(r: Map<String, Any?>): ApiSearchResult? {
        val id    = r["id"] as? String ?: return null
        val attrs = r["attributes"] as? Map<*, *> ?: return null
        val titleMap = attrs["title"] as? Map<*, *>
        val title = titleMap?.get("en") as? String
            ?: titleMap?.values?.firstOrNull() as? String ?: return null
        val descMap = attrs["description"] as? Map<*, *>
        val synopsis = (descMap?.get("en") as? String) ?: (descMap?.values?.firstOrNull() as? String)
        val year = (attrs["year"] as? Double)?.toInt()
        val releaseDate = year?.let { runCatching { Date(it - 1900, 0, 1) }.getOrNull() }

        val relationships = (r["relationships"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        val coverFileName = relationships
            .firstOrNull { it["type"] == "cover_art" }
            ?.let { (it["attributes"] as? Map<*, *>)?.get("fileName") as? String }
        val coverUrl = coverFileName?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }

        return ApiSearchResult(
            externalId  = id,
            title       = title,
            coverUrl    = coverUrl,
            synopsis    = synopsis,
            releaseDate = releaseDate,
            apiSource   = "mangadex",
        )
    }
}
