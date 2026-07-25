package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hobbiesvault.model.ApiSearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class GoogleBooksService(private val apiKey: String? = null) {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://www.googleapis.com/books/v1"

    val available get() = true

    private fun get(url: String): Map<String, Any?> {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(resp, type) ?: emptyMap()
    }

    // Boxes, coletâneas e guias costumam aparecer misturados aos livros individuais
    // na busca (ex.: "Box Percy Jackson e os Olimpianos", "The Complete Series",
    // "Guia Definitivo") mesmo tendo autor e capa cadastrados, então a filtragem
    // por título é necessária além da checagem de autor/capa.
    private val lowQualityTitleRegex = Regex(
        "\\bbox\\b|\\bomnibus\\b|\\bbundle\\b|complete (series|collection)|cole[cç][aã]o completa|kit completo|guia definitivo|ultimate guide|official guide|\\(series\\)|\\bseries\\b[^a-z]{0,10}\\d+\\s*-\\s*\\d+",
        RegexOption.IGNORE_CASE
    )

    fun searchBooks(query: String, field: String = "intitle"): List<ApiSearchResult> {
        if (query.isBlank()) return emptyList()
        val q   = URLEncoder.encode("$field:${query.trim()}", "UTF-8")
        val key = if (!apiKey.isNullOrEmpty()) "&key=$apiKey" else ""
        val url = "$base/volumes?q=$q&maxResults=20&langRestrict=pt&orderBy=relevance$key"
        val raw = (get(url)["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.mapNotNull { mapBook(it) } ?: emptyList()

        // Descarta entradas de baixa qualidade do catálogo (box sets, guias de estudo, etc.
        // costumam não ter autor nem capa cadastrados) e agrupa reedições do mesmo livro,
        // mantendo a primeira ocorrência (já vem ordenada por relevância).
        return raw
            .filter { !it.authors.isNullOrEmpty() || it.coverUrl != null }
            .filterNot { lowQualityTitleRegex.containsMatchIn(it.title) }
            .distinctBy { "${it.title.trim().lowercase()}|${it.authors?.firstOrNull()?.trim()?.lowercase()}" }
            .sortedBy { if (it.coverUrl != null) 0 else 1 }
    }

    fun searchByAuthor(author: String)    = searchBooks(author, "inauthor")
    fun searchByPublisher(publisher: String) = searchBooks(publisher, "inpublisher")

    fun getDetails(volumeId: String): Map<String, Any?>? {
        val key = if (!apiKey.isNullOrEmpty()) "?key=$apiKey" else ""
        return runCatching { get("$base/volumes/$volumeId$key") }.getOrNull()?.ifEmpty { null }
    }

    private fun mapBook(item: Map<String, Any?>): ApiSearchResult? {
        val info   = item["volumeInfo"] as? Map<*, *> ?: return null
        val title  = info["title"] as? String ?: return null
        val imgLinks = info["imageLinks"] as? Map<*, *>
        val coverUrl = (imgLinks?.get("thumbnail") as? String)
            ?.replace("http://", "https://")
            ?.replace("&zoom=1", "&zoom=2")
        val fmt  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val fmt2 = SimpleDateFormat("yyyy", Locale.US)
        val rawDate = info["publishedDate"] as? String
        val date = rawDate?.let { runCatching { fmt.parse(it) }.getOrNull() ?: runCatching { fmt2.parse(it) }.getOrNull() }
        val authors = (info["authors"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val genres  = (info["categories"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val ids  = info["industryIdentifiers"] as? List<*>
        val isbn = (ids?.filterIsInstance<Map<String, Any?>>())
            ?.firstOrNull { it["type"] == "ISBN_13" }?.get("identifier") as? String
        return ApiSearchResult(
            externalId  = item["id"] as? String ?: "",
            title       = title,
            coverUrl    = coverUrl,
            synopsis    = info["description"] as? String,
            releaseDate = date,
            genre       = genres.firstOrNull(),
            authors     = authors.ifEmpty { null },
            publisher   = info["publisher"] as? String,
            pages       = (info["pageCount"] as? Double)?.toInt(),
            isbn        = isbn,
            apiSource   = "google_books",
        )
    }
}
