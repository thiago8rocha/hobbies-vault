package com.hobbiesvault.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hobbiesvault.model.ApiSearchResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Date

class IgdbService(private val clientId: String, private val accessToken: String) {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://api.igdb.com/v4"
    private val coverSize = "cover_big"

    private val excludedPlatforms = setOf(34, 39, 55, 163, 165, 387, 390, 509, 510, 479)
    private val mainPlatforms     = setOf(6,3,14,48,167,9,8,38,46,49,169,12,11,130,137,390,508,41,5,20,37,21,4,19,18,24,33,22)

    // Maps IGDB full platform names to short friendly names (acts as display whitelist).
    // Mac e Linux são tratados como PC: sem suporte a lojas separadas (ex.: Epic Games) e sem
    // GameConsole dedicado para eles, contam como uma única plataforma "PC/Steam" no app.
    private val platformDisplayNames = mapOf(
        "PC (Microsoft Windows)" to "PC",
        "Mac"                    to "PC",
        "Linux"                  to "PC",
        "PlayStation"            to "PS1",
        "PlayStation 2"          to "PS2",
        "PlayStation 3"          to "PS3",
        "PlayStation 4"          to "PS4",
        "PlayStation 5"          to "PS5",
        "PlayStation Portable"   to "PSP",
        "PlayStation Vita"       to "PS Vita",
        "Xbox"                   to "Xbox",
        "Xbox 360"               to "Xbox 360",
        "Xbox One"               to "Xbox One",
        "Xbox Series X|S"        to "Series X/S",
        "Nintendo Switch"        to "Switch",
        "Nintendo Switch 2"      to "Switch 2",
        "Wii"                    to "Wii",
        "Wii U"                  to "Wii U",
        "Nintendo DS"            to "DS",
        "Nintendo 3DS"           to "3DS",
        "Nintendo GameCube"      to "GameCube",
        "Nintendo 64"            to "N64",
        "Super Nintendo Entertainment System" to "SNES",
        "Nintendo Entertainment System"       to "NES",
        "Game Boy Advance"       to "GBA",
        "Game Boy Color"         to "GBC",
        "Game Boy"               to "Game Boy",
    )
    private val officialCategories = setOf(0,1,2,3,4,6,7,8,9,10,11,13,14)
    private val invalidTitlePattern = Regex("""\b(fan[\s\-]?game|fangame|fan[\s\-]?made|unofficial|rom\s?hack|romhack|demo|tech\s+demo|open\s+beta|closed\s+beta|beta|trial|preview|network\s+test|kaizo|online\s+mode)\b|\d+\.\d+\.\d+""", RegexOption.IGNORE_CASE)

    private val abbreviations = mapOf(
        "gta" to "grand theft auto", "gow" to "god of war", "cod" to "call of duty",
        "tlou" to "the last of us", "botw" to "breath of the wild", "totk" to "tears of the kingdom",
        "rdr" to "red dead redemption", "nfs" to "need for speed", "mgs" to "metal gear solid",
        "dmc" to "devil may cry", "ds" to "dark souls", "mk" to "mortal kombat",
        "bf" to "battlefield", "ff" to "final fantasy", "mhw" to "monster hunter world",
        "mhr" to "monster hunter rise", "smb" to "super mario bros",
    )

    private val baseFields = "name, cover.url, first_release_date, genres.name, category, " +
        "involved_companies.company.name, involved_companies.developer, involved_companies.publisher, " +
        "platforms.name, platforms.id, aggregated_rating_count, aggregated_rating, rating_count, rating, follows, hypes"

    private fun post(endpoint: String, body: String): List<Map<String, Any?>> {
        val req = Request.Builder()
            .url("$base/$endpoint")
            .addHeader("Client-ID", clientId)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody("text/plain".toMediaType()))
            .build()
        val response = client.newCall(req).execute()
        val code = response.code
        val resp = response.use { it.body?.string() ?: "[]" }
        when (code) {
            401  -> throw Exception("Token IGDB inválido ou expirado (HTTP 401) — verifique as credenciais em secrets.json")
            429  -> throw Exception("Limite de requisições IGDB atingido (HTTP 429) — tente novamente em instantes")
            !in 200..299 -> throw Exception("Erro IGDB HTTP $code")
        }
        return try {
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            gson.fromJson(resp, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun expand(q: String): String {
        return q.lowercase().split(Regex("\\s+"))
            .joinToString(" ") { abbreviations[it] ?: it }
    }

    fun searchGames(query: String): List<ApiSearchResult> {
        if (query.isBlank()) return emptyList()
        val q   = expand(query.trim().replace("\"", ""))
        val now = System.currentTimeMillis() / 1000
        val filter = "(rating_count > 5 | aggregated_rating_count > 0 | hypes > 5)"
        val plats  = "(6,3,14,48,167,9,8,38,46,49,169,12,11,5,130,390,41,18,19,20,21,22,23,24,25,26,33,36,37,45,47,56,58,67,68,78,92)"

        val wildcardReleased = "fields $baseFields; where name ~ *\"$q\"* & cover != null & first_release_date != null & first_release_date <= $now & $filter & platforms = $plats & version_parent = null; sort first_release_date desc; limit 500;"
        val wildcardUpcoming = "fields $baseFields; where name ~ *\"$q\"* & cover != null & hypes > 5 & (first_release_date > $now | first_release_date = null); sort hypes desc; limit 100;"
        val ftsBody          = "fields $baseFields; search \"$q\"; where cover != null & $filter; limit 500;"

        val released = post("games", wildcardReleased)
        val upcoming = post("games", wildcardUpcoming)
        val fts      = post("games", ftsBody)

        return filterAndRank(upcoming + released + fts)
    }

    fun searchByTitles(titles: List<String>): List<ApiSearchResult> {
        if (titles.isEmpty()) return emptyList()
        val cleaned = titles.map { it.replace("\"","").replace(Regex("[®™©]"),"").trim() }
            .filter { it.isNotEmpty() }.toSet().toList()
        if (cleaned.isEmpty()) return emptyList()
        val where = cleaned.joinToString(" | ") { "name ~ \"$it\"" }
        val body = "fields $baseFields, summary; where ($where) & version_parent = null & category = (0,2,4,8,9,10,11) & cover != null; limit ${(cleaned.size * 2).coerceIn(1, 100)};"
        return filterAndRank(post("games", body))
    }

    fun searchByName(name: String): ApiSearchResult? {
        val q = name.replace("\"", "").trim()
        // aggregated_rating_count/parent_game sozinhos excluem jogos ainda não lançados
        // (que naturalmente não têm avaliação) — hypes/follows cobre esses casos, já que
        // a IGDB rastreia antecipação de lançamentos futuros por esses dois campos.
        for (where in listOf(
            "name ~ \"$q\" & (aggregated_rating_count > 0 | parent_game != null | hypes > 0 | follows > 0)",
            "name ~ *\"$q\"* & (aggregated_rating_count > 0 | parent_game != null | hypes > 0 | follows > 0)",
        )) {
            val body = "fields name, cover.url, first_release_date, genres.name, summary, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, platforms.name, platforms.id; where $where; sort first_release_date desc; limit 1;"
            val results = post("games", body)
            if (results.isNotEmpty()) return mapGame(results.first())
        }
        return null
    }

    fun getGameDetails(igdbId: Int): ApiSearchResult {
        val body = "where id = $igdbId; fields name, cover.url, first_release_date, genres.name, summary, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, platforms.name, platforms.id, artworks.url; limit 1;"
        val results = post("games", body)
        if (results.isEmpty()) throw Exception("Game not found: $igdbId")
        return mapGame(results.first()) ?: ApiSearchResult(externalId = igdbId.toString(), title = "")
    }

    fun getTimeToBeat(igdbId: Int): IgdbTimeToBeat? {
        val body = "where game = $igdbId; fields hastily, normally, completely; limit 1;"
        val results = post("game_time_to_beats", body)
        if (results.isEmpty()) return null
        val r = results.first()
        return IgdbTimeToBeat(
            hastily    = (r["hastily"]    as? Double)?.toInt(),
            normally   = (r["normally"]   as? Double)?.toInt(),
            completely = (r["completely"] as? Double)?.toInt(),
        )
    }

    fun getDetails(name: String): Map<String, Any?>? {
        val q = name.replace("\"", "")
        val body = "fields name, cover.url, first_release_date, summary, storyline, genres.name, platforms.name; where name = \"$q\" & cover != null; limit 5;"
        val results = post("games", body)
        if (results.isEmpty()) return null
        val r = results.first()
        val cover = r["cover"] as? Map<*, *>
        val coverUrl = (cover?.get("url") as? String)
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.replace(Regex("t_(thumb|cover_small|cover_med|screenshot_med|screenshot_big)"), "t_cover_big")
        val genres    = (r["genres"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { it["name"] as? String ?: "" } ?: emptyList()
        val platforms = (r["platforms"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { it["name"] as? String ?: "" } ?: emptyList()
        val releaseDate = (r["first_release_date"] as? Double)?.toLong()?.let {
            val dt  = Date(it * 1000)
            val cal = java.util.Calendar.getInstance().also { c -> c.time = dt }
            "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH)+1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        }
        return mapOf(
            "igdbId"      to (r["id"] as? Double)?.toInt(),
            "coverUrl"    to coverUrl,
            "summary"     to r["summary"],
            "storyline"   to r["storyline"],
            "genres"      to gson.toJson(genres),
            "platforms"   to gson.toJson(platforms),
            "releaseDate" to releaseDate,
        )
    }

    // ── Score and filters ─────────────────────────────────────────────────────

    private fun scoreGame(r: Map<String, Any?>): Int {
        val platforms = (r["platforms"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.map { (it["id"] as? Double)?.toInt() ?: 0 } ?: emptyList()
        val hasMainPlatform = platforms.any { it in mainPlatforms }
        val category     = (r["category"] as? Double)?.toInt() ?: 0
        val totalVotes   = ((r["rating_count"] as? Double)?.toInt() ?: 0) + ((r["aggregated_rating_count"] as? Double)?.toInt() ?: 0)
        val hypes        = (r["hypes"] as? Double)?.toInt() ?: 0
        val hasCompany   = (r["involved_companies"] as? List<*>)?.isNotEmpty() == true
        if (category == 5 || category == 12) return -1
        if (totalVotes == 0 && hypes == 0 && !hasCompany) return -1
        return if (hasMainPlatform) 2 else 1
    }

    private fun filterAndRank(raw: List<Map<String, Any?>>): List<ApiSearchResult> {
        val withScore = raw
            .filter { !invalidTitlePattern.containsMatchIn(it["name"] as? String ?: "") }
            .map { Pair(scoreGame(it), it) }

        val byTitle = mutableMapOf<String, Pair<Int, Map<String, Any?>>>()
        for (entry in withScore) {
            val key = (entry.second["name"] as? String ?: "").lowercase().trim()
            val existing = byTitle[key]
            if (existing == null || entry.first > existing.first) byTitle[key] = entry
        }

        val results = byTitle.values.mapNotNull { mapGame(it.second) }
        return results.sortedWith(Comparator { a, b ->
            val scoreA = byTitle[a.title.lowercase().trim()]?.first ?: 0
            val scoreB = byTitle[b.title.lowercase().trim()]?.first ?: 0
            if (scoreA != scoreB) return@Comparator scoreB - scoreA
            val aDate = a.releaseDate; val bDate = b.releaseDate
            val isMain = scoreA >= 2
            when {
                aDate == null && bDate == null -> 0
                aDate == null -> if (isMain) -1 else 1
                bDate == null -> if (isMain) 1 else -1
                else          -> bDate.compareTo(aDate)
            }
        })
    }

    private fun mapGame(r: Map<String, Any?>): ApiSearchResult? {
        val title = r["name"] as? String ?: return null
        if (invalidTitlePattern.containsMatchIn(title)) return null

        val coverUrl = (r["cover"] as? Map<*, *>)?.get("url") as? String
        val cover = coverUrl?.let {
            val url = if (it.startsWith("//")) "https:$it" else it
            url.replace(Regex("t_(thumb|cover_small|cover_med|screenshot_med|screenshot_big)"), "t_$coverSize")
        }

        val ts          = r["first_release_date"] as? Double
        val releaseDate = ts?.let { Date(it.toLong() * 1000) }

        val genres = (r["genres"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        val genre  = genres.mapNotNull { it["name"] as? String }.joinToString(", ").ifEmpty { null }

        val companies = (r["involved_companies"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        var dev: String? = null; var pub: String? = null
        for (c in companies) {
            if (dev == null && c["developer"] == true) dev = (c["company"] as? Map<*, *>)?.get("name") as? String
            if (pub == null && c["publisher"] == true) pub = (c["company"] as? Map<*, *>)?.get("name") as? String
            if (dev != null && pub != null) break
        }

        val rawPlatforms = (r["platforms"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        val platforms = rawPlatforms
            .mapNotNull { platformDisplayNames[it["name"] as? String ?: ""] }
            .distinct()

        val artworkUrl = (r["artworks"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.firstOrNull()
            ?.get("url")?.let {
                val url = if ((it as String).startsWith("//")) "https:$it" else it
                url.replace(Regex("t_(thumb|cover_small|cover_big|cover_med|screenshot_med)"), "t_screenshot_big")
            }

        val ratingCount     = (r["rating_count"] as? Double)?.toInt() ?: 0
        val aggregatedCount = (r["aggregated_rating_count"] as? Double)?.toInt() ?: 0

        return ApiSearchResult(
            externalId  = r["id"]?.toString() ?: "",
            title       = title,
            coverUrl    = cover,
            artworkUrl  = artworkUrl,
            synopsis    = r["summary"] as? String,
            releaseDate = releaseDate,
            genre       = genre,
            developer   = dev,
            publisher   = pub,
            platforms   = if (platforms.isNotEmpty()) platforms else null,
            apiSource   = "igdb",
            popularity  = aggregatedCount + ratingCount,
        )
    }
}

data class IgdbTimeToBeat(
    val hastily: Int? = null,
    val normally: Int? = null,
    val completely: Int? = null,
) {
    val hasData get() = hastily != null || normally != null || completely != null
}

object IgdbAuthService {
    private const val tokenUrl = "https://id.twitch.tv/oauth2/token"
    private val client = OkHttpClient()

    private fun tokenFile(context: Context) = File(context.filesDir, "igdb_token.json")

    fun loadCachedToken(context: Context, clientId: String? = null): IgdbToken? {
        return try {
            val file = tokenFile(context)
            if (!file.exists()) return null
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = Gson().fromJson(file.readText(), type) ?: return null
            if (clientId != null && json["client_id"] != null && json["client_id"] != clientId) {
                file.delete(); return null
            }
            val token = IgdbToken(
                accessToken = json["access_token"] as? String ?: return null,
                expiresIn   = (json["expires_in"] as? Double)?.toInt() ?: return null,
                obtainedAt  = Date((json["obtained_at"] as? Double)?.toLong() ?: return null),
            )
            if (token.isExpired) { file.delete(); null } else token
        } catch (_: Exception) { null }
    }

    fun getAccessToken(context: Context, clientId: String, clientSecret: String): IgdbToken {
        val body = "client_id=$clientId&client_secret=$clientSecret&grant_type=client_credentials"
        val req = Request.Builder().url(tokenUrl)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("IGDB auth error: ${resp.code}")
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val json: Map<String, Any?> = Gson().fromJson(resp.body?.string() ?: "{}", type)
        val token = IgdbToken(
            accessToken = json["access_token"] as? String ?: throw Exception("Missing access_token"),
            expiresIn   = (json["expires_in"] as? Double)?.toInt() ?: 3600,
            obtainedAt  = Date(),
        )
        saveToken(context, token, clientId)
        return token
    }

    private fun saveToken(context: Context, token: IgdbToken, clientId: String) {
        try {
            tokenFile(context).writeText(
                Gson().toJson(mapOf("access_token" to token.accessToken, "expires_in" to token.expiresIn, "obtained_at" to token.obtainedAt.time, "client_id" to clientId))
            )
        } catch (_: Exception) {}
    }
}

data class IgdbToken(val accessToken: String, val expiresIn: Int, val obtainedAt: Date) {
    val isExpired get() = Date().after(Date(obtainedAt.time + (expiresIn - 300) * 1000L))
}
