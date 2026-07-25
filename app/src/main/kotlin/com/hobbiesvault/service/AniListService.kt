package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hobbiesvault.model.ApiSearchResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Date

class AniListService(private val accessToken: String? = null) {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val apiUrl = "https://graphql.anilist.co"

    private fun query(gql: String, variables: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val body = gson.toJson(mapOf("query" to gql, "variables" to variables))
        val req  = Request.Builder().url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .apply { accessToken?.let { addHeader("Authorization", "Bearer $it") } }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val decoded: Map<String, Any?> = gson.fromJson(resp, type) ?: emptyMap()
        return decoded["data"] as? Map<String, Any?> ?: emptyMap()
    }

    fun search(q: String): List<ApiSearchResult> {
        if (q.isBlank()) return emptyList()
        val gql = """
            query (${'$'}search: String) {
              Page(perPage: 10) {
                media(search: ${'$'}search, type: MANGA) {
                  id title { romaji english native }
                  coverImage { large }
                  description(asHtml: false)
                  startDate { year month day }
                  genres chapters status
                }
              }
            }
        """
        val page  = (query(gql, mapOf("search" to q))["Page"] as? Map<*, *>) ?: return emptyList()
        val media = (page["media"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: return emptyList()
        return media.map { mapItem(it) }
    }

    fun getUserProgress(username: String, mediaId: Int): Int? {
        val gql = """
            query (${'$'}username: String, ${'$'}mediaId: Int) {
              MediaList(userName: ${'$'}username, mediaId: ${'$'}mediaId, type: MANGA) {
                progress
              }
            }
        """
        return runCatching {
            val list = query(gql, mapOf("username" to username, "mediaId" to mediaId))["MediaList"] as? Map<*, *>
            (list?.get("progress") as? Double)?.toInt()
        }.getOrNull()
    }

    fun getDetailsById(id: Int): Map<String, Any?>? {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: MANGA) {
                id title { romaji english native }
                coverImage { large extraLarge }
                description(asHtml: false)
                startDate { year month day }
                endDate { year month day }
                genres chapters volumes status format synonyms
                averageScore popularity
                staff { edges { role node { name { full } image { large } } } }
                characters(sort: ROLE, perPage: 10) { nodes { name { full } image { large } } }
              }
            }
        """
        return (query(gql, mapOf("id" to id))["Media"] as? Map<String, Any?>)?.let { m ->
            buildMap {
                val title = m["title"] as? Map<*, *>
                put("title",    title?.get("english") ?: title?.get("romaji") ?: title?.get("native"))
                put("coverUrl", (m["coverImage"] as? Map<*, *>)?.let { it["extraLarge"] ?: it["large"] })
                put("synopsis", (m["description"] as? String)?.replace(Regex("<[^>]*>"), ""))
                put("genres",   m["genres"])
                put("chapters", (m["chapters"] as? Double)?.toInt())
                put("volumes",  (m["volumes"] as? Double)?.toInt())
                put("status",   m["status"])
                put("format",   m["format"])
                put("synonyms", (m["synonyms"] as? List<*>)?.filterIsInstance<String>())
                put("popularity", (m["popularity"] as? Double)?.toInt())

                fun dateToMs(d: Map<*, *>?): Long? {
                    val y = (d?.get("year")  as? Double)?.toInt() ?: return null
                    val mo = (d.get("month") as? Double)?.toInt() ?: 1
                    val dy = (d.get("day")   as? Double)?.toInt() ?: 1
                    return runCatching { java.util.Date(y - 1900, mo - 1, dy).time }.getOrNull()
                }
                put("startDateMs", dateToMs(m["startDate"] as? Map<*, *>))
                put("endDateMs",   dateToMs(m["endDate"]   as? Map<*, *>))

                val staffEdges = (m["staff"] as? Map<*, *>)?.get("edges") as? List<*>
                val staffList  = staffEdges?.filterIsInstance<Map<String, Any?>>()
                put("authors", staffList
                    ?.filter { (it["role"] as? String)?.contains("Story", ignoreCase = true) == true }
                    ?.mapNotNull { (it["node"] as? Map<*, *>)?.let { n -> (n["name"] as? Map<*, *>)?.get("full") as? String } })
                put("staff", staffList?.mapNotNull { edge ->
                    val role = edge["role"] as? String ?: return@mapNotNull null
                    val node = edge["node"] as? Map<*, *> ?: return@mapNotNull null
                    val name = (node["name"] as? Map<*, *>)?.get("full") as? String ?: return@mapNotNull null
                    val photo = (node["image"] as? Map<*, *>)?.get("large") as? String
                    mapOf("name" to name, "role" to role, "photoUrl" to photo)
                })

                val chars = (m["characters"] as? Map<*, *>)?.get("nodes") as? List<*>
                put("characters", chars?.filterIsInstance<Map<String, Any?>>()?.mapNotNull { c ->
                    val name  = (c["name"]  as? Map<*, *>)?.get("full") as? String ?: return@mapNotNull null
                    val photo = (c["image"] as? Map<*, *>)?.get("large") as? String
                    mapOf("name" to name, "photoUrl" to photo)
                })
            }
        }
    }

    private fun mapItem(r: Map<String, Any?>, apiSource: String = "anilist"): ApiSearchResult {
        val title   = r["title"] as? Map<*, *>
        val name    = title?.get("english") as? String
            ?: title?.get("romaji") as? String
            ?: title?.get("native") as? String ?: ""
        val cover   = (r["coverImage"] as? Map<*, *>)?.get("large") as? String
        val startDate = r["startDate"] as? Map<*, *>
        val year  = (startDate?.get("year") as? Double)?.toInt()
        val month = (startDate?.get("month") as? Double)?.toInt() ?: 1
        val day   = (startDate?.get("day") as? Double)?.toInt() ?: 1
        val date  = year?.let { runCatching { Date(it - 1900, month - 1, day) }.getOrNull() }
        val genres = (r["genres"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return ApiSearchResult(
            externalId  = (r["id"] as? Double)?.toInt()?.toString() ?: r["id"]?.toString() ?: "",
            title       = name,
            coverUrl    = cover,
            synopsis    = (r["description"] as? String)?.replace(Regex("<[^>]*>"), ""),
            releaseDate = date,
            genre       = genres.firstOrNull(),
            chapters    = (r["chapters"] as? Double)?.toInt(),
            apiSource   = apiSource,
        )
    }
}
