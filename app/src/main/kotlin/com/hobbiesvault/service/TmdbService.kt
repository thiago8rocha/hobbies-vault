package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hobbiesvault.model.ApiSearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date

class TmdbService(private val bearerToken: String) {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://api.themoviedb.org/3"
    private val lang   = "pt-BR"
    private val region = "BR"

    private fun get(url: String): Map<String, Any?> {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .addHeader("Content-Type", "application/json")
            .build()
        val response = client.newCall(req).execute()
        val code = response.code
        val body = response.use { it.body?.string() ?: "{}" }
        when (code) {
            401  -> throw Exception("Token TMDB inválido ou expirado (HTTP 401) — verifique tmdb_bearer_token em secrets.json")
            429  -> throw Exception("Limite de requisições TMDB atingido (HTTP 429) — tente novamente em instantes")
            !in 200..299 -> throw Exception("Erro TMDB HTTP $code")
        }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(body, type) ?: emptyMap()
    }

    // ── Movies ────────────────────────────────────────────────────────────────

    fun searchMovies(query: String): List<ApiSearchResult> {
        if (query.isBlank()) return emptyList()
        val url = "$base/search/movie?query=${enc(query)}&language=$lang&page=1&region=$region"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            ?.map { mapMovie(it) } ?: emptyList()
    }

    fun searchMoviesByPerson(name: String): List<ApiSearchResult> {
        val personId = getPersonId(name) ?: return emptyList()
        val url = "$base/discover/movie?language=$lang&region=$region&with_people=$personId&sort_by=release_date.desc"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { mapMovie(it) } ?: emptyList()
    }

    fun searchMoviesByStudio(name: String): List<ApiSearchResult> {
        val companyId = getCompanyId(name) ?: return emptyList()
        val url = "$base/discover/movie?language=$lang&region=$region&with_companies=$companyId&sort_by=release_date.desc"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { mapMovie(it) } ?: emptyList()
    }

    fun searchMoviesByStreaming(name: String): List<ApiSearchResult> {
        val networkId = streamingProviderId(name) ?: return emptyList()
        val url = "$base/discover/movie?language=$lang&region=$region&with_watch_providers=$networkId&watch_region=$region&sort_by=release_date.desc"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { mapMovie(it) } ?: emptyList()
    }

    // ── Series ─────────────────────────────────────────────────────────────────

    fun searchSeries(query: String): List<ApiSearchResult> {
        if (query.isBlank()) return emptyList()
        val url = "$base/search/tv?query=${enc(query)}&language=$lang&page=1"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { mapSeries(it) } ?: emptyList()
    }

    fun searchSeriesByPerson(name: String): List<ApiSearchResult> {
        val personId = getPersonId(name) ?: return emptyList()
        val url = "$base/discover/tv?language=$lang&with_people=$personId&sort_by=first_air_date.desc"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { mapSeries(it) } ?: emptyList()
    }

    fun searchSeriesByStudio(name: String): List<ApiSearchResult> {
        val companyId = getCompanyId(name) ?: return emptyList()
        val url = "$base/discover/tv?language=$lang&with_companies=$companyId&sort_by=first_air_date.desc"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { mapSeries(it) } ?: emptyList()
    }

    fun searchSeriesByStreaming(name: String): List<ApiSearchResult> {
        val networkId = streamingProviderId(name) ?: return emptyList()
        val url = "$base/discover/tv?language=$lang&with_watch_providers=$networkId&watch_region=$region&sort_by=first_air_date.desc"
        return (get(url)["results"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { mapSeries(it) } ?: emptyList()
    }

    // ── Details ───────────────────────────────────────────────────────────────

    fun getMovieDetails(tmdbId: Int): TmdbMovieDetails {
        val url = "$base/movie/$tmdbId?language=$lang&append_to_response=credits,watch/providers,recommendations"
        return TmdbMovieDetails.fromJson(get(url))
    }

    fun getSeriesDetails(tmdbId: Int): TmdbSeriesDetails {
        val url = "$base/tv/$tmdbId?language=$lang&append_to_response=credits,watch/providers,recommendations"
        return TmdbSeriesDetails.fromJson(get(url))
    }

    fun getSeasonEpisodes(seriesId: Int, season: Int): List<TmdbEpisode> {
        val url = "$base/tv/$seriesId/season/$season?language=$lang"
        return (get(url)["episodes"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()?.map { TmdbEpisode.fromJson(it) } ?: emptyList()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getPersonId(name: String): String? {
        val url = "$base/search/person?query=${enc(name)}&language=$lang"
        val results = get(url)["results"] as? List<*> ?: return null
        val first = results.filterIsInstance<Map<String, Any?>>().firstOrNull() ?: return null
        return first["id"]?.toString()
    }

    private fun getCompanyId(name: String): String? {
        val url = "$base/search/company?query=${enc(name)}"
        val results = get(url)["results"] as? List<*> ?: return null
        val first = results.filterIsInstance<Map<String, Any?>>().firstOrNull() ?: return null
        return first["id"]?.toString()
    }

    private fun streamingProviderId(name: String): Int? = mapOf(
        "netflix" to 8, "prime video" to 119, "disney+" to 337, "max" to 1899,
        "hbo max" to 1899, "apple tv+" to 350, "globoplay" to 307,
        "paramount+" to 531, "crunchyroll" to 283, "mubi" to 11,
        "telecine" to 227, "star+" to 619,
    )[name.lowercase()]

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun mapMovie(r: Map<String, Any?>) = ApiSearchResult(
        externalId  = (r["id"] as? Double)?.toInt()?.toString() ?: r["id"]?.toString() ?: "",
        title       = r["title"] as? String ?: r["original_title"] as? String ?: "",
        coverUrl    = (r["poster_path"] as? String)?.let { "https://image.tmdb.org/t/p/w500$it" },
        synopsis    = r["overview"] as? String,
        releaseDate = parseDate(r["release_date"] as? String),
        apiSource   = "tmdb",
    )

    private fun mapSeries(r: Map<String, Any?>) = ApiSearchResult(
        externalId  = (r["id"] as? Double)?.toInt()?.toString() ?: r["id"]?.toString() ?: "",
        title       = r["name"] as? String ?: r["original_name"] as? String ?: "",
        coverUrl    = (r["poster_path"] as? String)?.let { "https://image.tmdb.org/t/p/w500$it" },
        synopsis    = r["overview"] as? String,
        releaseDate = parseDate(r["first_air_date"] as? String),
        seasons     = (r["number_of_seasons"] as? Double)?.toInt(),
        apiSource   = "tmdb",
    )

    private fun parseDate(raw: String?): Date? {
        if (raw.isNullOrEmpty()) return null
        return try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(raw) } catch (_: Exception) { null }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

// ─── TMDB Models ─────────────────────────────────────────────────────────────

data class TmdbPerson(
    val id: Int, val name: String,
    val character: String? = null, val role: String? = null, val photoUrl: String? = null,
) {
    companion object {
        fun cast(j: Map<String, Any?>) = TmdbPerson(
            id        = (j["id"] as? Double)?.toInt() ?: 0,
            name      = j["name"] as? String ?: "",
            character = j["character"] as? String,
            photoUrl  = (j["profile_path"] as? String)?.let { "https://image.tmdb.org/t/p/w185$it" },
        )
        fun crew(j: Map<String, Any?>) = TmdbPerson(
            id       = (j["id"] as? Double)?.toInt() ?: 0,
            name     = j["name"] as? String ?: "",
            role     = translateRole(j["job"] as? String ?: ""),
            photoUrl = (j["profile_path"] as? String)?.let { "https://image.tmdb.org/t/p/w185$it" },
        )
        private fun translateRole(job: String) = mapOf(
            "Director" to "Direção", "Screenplay" to "Roteiro", "Writer" to "Roteiro",
            "Producer" to "Produção", "Executive Producer" to "Produção Executiva",
            "Director of Photography" to "Fotografia", "Original Music Composer" to "Trilha Sonora",
            "Editor" to "Edição", "Creator" to "Criação", "Showrunner" to "Showrunner",
        )[job] ?: job

        // Ordem de prioridade dos cargos principais na Equipe Técnica.
        val jobPriority = listOf(
            "Director", "Screenplay", "Writer", "Creator", "Showrunner",
            "Director of Photography", "Original Music Composer",
            "Producer", "Executive Producer", "Editor",
        )
    }
}

data class TmdbProvider(val name: String, val logoUrl: String?) {
    val baseName: String get() {
        val lower = name.lowercase()
        return listOf("netflix","prime video","amazon prime","disney+","max","apple tv+",
            "globoplay","paramount+","crunchyroll","mubi","telecine","star+")
            .firstOrNull { lower.startsWith(it) || lower.contains(it) }
            ?.replaceFirstChar { it.uppercase() } ?: name
    }
    companion object {
        fun fromJson(j: Map<String, Any?>) = TmdbProvider(
            name    = j["provider_name"] as? String ?: "",
            logoUrl = (j["logo_path"] as? String)?.let { "https://image.tmdb.org/t/p/w92$it" },
        )
    }
}

data class TmdbSeason(
    val number: Int, val name: String, val episodes: Int,
    val airDate: Date? = null, val posterUrl: String? = null, val synopsis: String? = null,
) {
    companion object {
        fun fromJson(j: Map<String, Any?>): TmdbSeason {
            val date = try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(j["air_date"] as? String ?: "") } catch (_: Exception) { null }
            return TmdbSeason(
                number   = (j["season_number"] as? Double)?.toInt() ?: 0,
                name     = j["name"] as? String ?: "Season ${j["season_number"]}",
                episodes = (j["episode_count"] as? Double)?.toInt() ?: 0,
                airDate  = date,
                posterUrl = (j["poster_path"] as? String)?.let { "https://image.tmdb.org/t/p/w500$it" },
                synopsis  = j["overview"] as? String,
            )
        }
    }
}

data class TmdbRelatedMovie(val id: Int, val title: String, val posterUrl: String? = null, val year: Int? = null) {
    companion object {
        fun fromJson(j: Map<String, Any?>) = TmdbRelatedMovie(
            id        = (j["id"] as? Double)?.toInt() ?: 0,
            title     = j["title"] as? String ?: j["original_title"] as? String ?: "",
            posterUrl = (j["poster_path"] as? String)?.let { "https://image.tmdb.org/t/p/w500$it" },
            year      = (j["release_date"] as? String)?.take(4)?.toIntOrNull(),
        )
    }
}

data class TmdbRelatedSeries(val id: Int, val title: String, val posterUrl: String? = null, val year: Int? = null) {
    companion object {
        fun fromJson(j: Map<String, Any?>) = TmdbRelatedSeries(
            id        = (j["id"] as? Double)?.toInt() ?: 0,
            title     = j["name"] as? String ?: j["original_name"] as? String ?: "",
            posterUrl = (j["poster_path"] as? String)?.let { "https://image.tmdb.org/t/p/w500$it" },
            year      = (j["first_air_date"] as? String)?.take(4)?.toIntOrNull(),
        )
    }
}

data class TmdbEpisode(
    val number: Int, val name: String,
    val airDate: Date? = null, val stillUrl: String? = null, val synopsis: String? = null,
) {
    companion object {
        fun fromJson(j: Map<String, Any?>): TmdbEpisode {
            val date = try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(j["air_date"] as? String ?: "") } catch (_: Exception) { null }
            return TmdbEpisode(
                number   = (j["episode_number"] as? Double)?.toInt() ?: 0,
                name     = j["name"] as? String ?: "",
                airDate  = date,
                stillUrl = (j["still_path"] as? String)?.let { "https://image.tmdb.org/t/p/w300$it" },
                synopsis = j["overview"] as? String,
            )
        }
    }
}

data class TmdbMovieDetails(
    val id: Int, val title: String,
    val synopsis: String? = null, val posterPath: String? = null, val backdropPath: String? = null,
    val releaseDate: Date? = null, val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(), val tmdbStatus: String? = null,
    val cast: List<TmdbPerson> = emptyList(), val crew: List<TmdbPerson> = emptyList(),
    val providers: List<TmdbProvider> = emptyList(), val related: List<TmdbRelatedMovie> = emptyList(),
) {
    val posterUrl   get() = posterPath?.let   { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }

    val runtimeLabel get(): String {
        val min = runtimeMinutes ?: return ""
        val h = min / 60; val m = min % 60
        return when { h == 0 -> "${m}min"; m == 0 -> "${h}h"; else -> "${h}h${m}min" }
    }

    val uniqueProviders get(): List<TmdbProvider> {
        val seen = mutableSetOf<String>()
        return providers.filter { seen.add(it.baseName) }
    }

    companion object {
        fun fromJson(j: Map<String, Any?>): TmdbMovieDetails {
            val credits  = j["credits"] as? Map<*, *>
            val castRaw  = (credits?.get("cast") as? List<*>)?.take(15)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val crewJobs = setOf("Director","Screenplay","Writer","Producer","Executive Producer","Director of Photography","Original Music Composer")
            val crewRaw  = (credits?.get("crew") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                ?.filter { it["job"] in crewJobs }
                ?.sortedBy { TmdbPerson.jobPriority.indexOf(it["job"] as? String ?: "").let { i -> if (i < 0) Int.MAX_VALUE else i } }
                ?: emptyList()
            val watchBr  = (((j["watch/providers"] as? Map<*, *>)?.get("results") as? Map<*, *>)?.get("BR") as? Map<*, *>)?.get("flatrate") as? List<*> ?: emptyList<Any>()
            val recs     = (((j["recommendations"] as? Map<*, *>)?.get("results")) as? List<*>)?.take(10)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val date     = try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(j["release_date"] as? String ?: "") } catch (_: Exception) { null }
            return TmdbMovieDetails(
                id             = (j["id"] as? Double)?.toInt() ?: 0,
                title          = j["title"] as? String ?: j["original_title"] as? String ?: "",
                synopsis       = j["overview"] as? String,
                posterPath     = j["poster_path"] as? String,
                backdropPath   = j["backdrop_path"] as? String,
                releaseDate    = date,
                runtimeMinutes = (j["runtime"] as? Double)?.toInt(),
                genres         = (j["genres"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { it["name"] as? String ?: "" } ?: emptyList(),
                tmdbStatus     = j["status"] as? String,
                cast           = castRaw.map { TmdbPerson.cast(it) },
                crew           = crewRaw.map { TmdbPerson.crew(it) },
                providers      = watchBr.filterIsInstance<Map<String, Any?>>().map { TmdbProvider.fromJson(it) },
                related        = recs.map { TmdbRelatedMovie.fromJson(it) },
            )
        }
    }
}

data class TmdbSeriesDetails(
    val id: Int, val title: String,
    val synopsis: String? = null, val posterPath: String? = null, val backdropPath: String? = null,
    val firstAirDate: Date? = null, val lastAirDate: Date? = null,
    val totalEpisodes: Int? = null, val genres: List<String> = emptyList(),
    val tmdbStatus: String? = null, val network: String? = null,
    val seasons: List<TmdbSeason> = emptyList(),
    val cast: List<TmdbPerson> = emptyList(), val crew: List<TmdbPerson> = emptyList(),
    val providers: List<TmdbProvider> = emptyList(), val related: List<TmdbRelatedSeries> = emptyList(),
) {
    val posterUrl   get() = posterPath?.let   { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }

    val yearLabel get(): String {
        val start = firstAirDate?.let { java.util.Calendar.getInstance().also { c -> c.time = it }.get(java.util.Calendar.YEAR) } ?: return ""
        if (tmdbStatus == "Returning Series") return "$start – present"
        val end = lastAirDate?.let { java.util.Calendar.getInstance().also { c -> c.time = it }.get(java.util.Calendar.YEAR) }
        return if (end != null && end != start) "$start – $end" else "$start"
    }

    val uniqueProviders get(): List<TmdbProvider> {
        val seen = mutableSetOf<String>()
        return providers.filter { seen.add(it.baseName) }
    }

    companion object {
        fun fromJson(j: Map<String, Any?>): TmdbSeriesDetails {
            val fmt      = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val credits  = j["credits"] as? Map<*, *>
            val castRaw  = (credits?.get("cast") as? List<*>)?.take(15)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val crewJobs = setOf("Executive Producer","Showrunner","Director of Photography")
            val crewRaw  = (credits?.get("crew") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                ?.filter { it["job"] in crewJobs }
                ?.sortedBy { TmdbPerson.jobPriority.indexOf(it["job"] as? String ?: "").let { i -> if (i < 0) Int.MAX_VALUE else i } }
                ?: emptyList()
            val creators = (j["created_by"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map {
                TmdbPerson(id = (it["id"] as? Double)?.toInt() ?: 0, name = it["name"] as? String ?: "", role = "Criação",
                    photoUrl = (it["profile_path"] as? String)?.let { p -> "https://image.tmdb.org/t/p/w185$p" })
            } ?: emptyList()
            val watchBr  = (((j["watch/providers"] as? Map<*, *>)?.get("results") as? Map<*, *>)?.get("BR") as? Map<*, *>)?.get("flatrate") as? List<*> ?: emptyList<Any>()
            val recs     = ((j["recommendations"] as? Map<*, *>)?.get("results") as? List<*>)?.take(10)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val first    = try { fmt.parse(j["first_air_date"] as? String ?: "") } catch (_: Exception) { null }
            val last     = try { fmt.parse(j["last_air_date"]  as? String ?: "") } catch (_: Exception) { null }
            val seasons  = (j["seasons"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                ?.map { TmdbSeason.fromJson(it) }?.filter { it.number > 0 } ?: emptyList()
            val network  = (j["networks"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.firstOrNull()?.get("name") as? String
            return TmdbSeriesDetails(
                id           = (j["id"] as? Double)?.toInt() ?: 0,
                title        = j["name"] as? String ?: j["original_name"] as? String ?: "",
                synopsis     = j["overview"] as? String,
                posterPath   = j["poster_path"] as? String,
                backdropPath = j["backdrop_path"] as? String,
                firstAirDate = first,
                lastAirDate  = last,
                totalEpisodes = (j["number_of_episodes"] as? Double)?.toInt(),
                genres       = (j["genres"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { it["name"] as? String ?: "" } ?: emptyList(),
                tmdbStatus   = j["status"] as? String,
                network      = network,
                seasons      = seasons,
                cast         = castRaw.map { TmdbPerson.cast(it) },
                crew         = creators + crewRaw.map { TmdbPerson.crew(it) },
                providers    = watchBr.filterIsInstance<Map<String, Any?>>().map { TmdbProvider.fromJson(it) },
                related      = recs.map { TmdbRelatedSeries.fromJson(it) },
            )
        }
    }
}
