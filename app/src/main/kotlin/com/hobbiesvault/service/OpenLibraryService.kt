package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hobbiesvault.model.ApiSearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class OpenLibraryService {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://openlibrary.org"

    private fun get(url: String): Map<String, Any?> {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(resp, type) ?: emptyMap()
    }

    fun searchBooks(query: String): List<ApiSearchResult> {
        if (query.isBlank()) return emptyList()
        val q   = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$base/search.json?q=$q&limit=10&fields=key,title,author_name,cover_i,first_publish_year,subject,number_of_pages_median,isbn"
        return (get(url)["docs"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.mapNotNull { mapBook(it) } ?: emptyList()
    }

    fun getDetails(workKey: String): Map<String, Any?>? {
        val id = workKey.removePrefix("/works/")
        val work = runCatching { get("$base/works/$id.json") }.getOrNull()?.ifEmpty { null } ?: return null

        val description = when (val d = work["description"]) {
            is String -> d
            is Map<*, *> -> d["value"] as? String
            else -> null
        }
        val subjects = (work["subjects"] as? List<*>)?.filterIsInstance<String>()

        val authorNames = (work["authors"] as? List<*>)?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { entry -> (entry["author"] as? Map<*, *>)?.get("key") as? String }
            ?.mapNotNull { authorKey ->
                runCatching { get("$base$authorKey.json") }.getOrNull()?.get("name") as? String
            }

        val editions = runCatching { get("$base/works/$id/editions.json") }.getOrNull()
        val firstEdition = (editions?.get("entries") as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull()
        val publisher = (firstEdition?.get("publishers") as? List<*>)?.filterIsInstance<String>()?.firstOrNull()
        val pages = (firstEdition?.get("number_of_pages") as? Double)?.toInt()
        val publishDate = firstEdition?.get("publish_date") as? String
        val releaseDate = publishDate?.let {
            runCatching { SimpleDateFormat("yyyy", Locale.US).parse(it.takeLast(4)) }.getOrNull()
        }

        return buildMap {
            put("synopsis", description)
            put("author", authorNames?.firstOrNull())
            put("publisher", publisher)
            put("genre", subjects?.firstOrNull())
            put("pages", pages)
            put("releaseDate", releaseDate?.time)
        }
    }

    private fun mapBook(doc: Map<String, Any?>): ApiSearchResult? {
        val title   = doc["title"] as? String ?: return null
        val coverId = (doc["cover_i"] as? Double)?.toInt()
        val coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
        val year    = (doc["first_publish_year"] as? Double)?.toInt()
        val date    = year?.let { runCatching { SimpleDateFormat("yyyy", Locale.US).parse("$it") }.getOrNull() }
        val authors = (doc["author_name"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val genres  = (doc["subject"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val isbns   = (doc["isbn"] as? List<*>)?.filterIsInstance<String>()
        val isbn    = isbns?.firstOrNull { it.length == 13 } ?: isbns?.firstOrNull()
        return ApiSearchResult(
            externalId  = (doc["key"] as? String)?.removePrefix("/works/") ?: "",
            title       = title,
            coverUrl    = coverUrl,
            releaseDate = date,
            genre       = genres.firstOrNull(),
            authors     = authors.ifEmpty { null },
            pages       = (doc["number_of_pages_median"] as? Double)?.toInt(),
            isbn        = isbn,
            apiSource   = "open_library",
        )
    }
}
