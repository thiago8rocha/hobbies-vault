package com.hobbiesvault.service

import android.util.Log
import com.google.gson.Gson
import com.hobbiesvault.data.db.dao.GameCacheDao
import com.hobbiesvault.data.db.entity.GameCacheEntity
import com.hobbiesvault.model.ApiSearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mirrors Flutter's GameCacheService.
 * Single source of truth for game metadata — GB dataset for deck,
 * IGDB for cover/summary/platforms/genres.
 */
class GameCacheService(
    private val dao: GameCacheDao,
    private val igdb: IgdbService? = null,
) {
    private val gson   = Gson()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Enriches the game_cache entry for [name] with IGDB data.
     * If no entry exists, creates one from IGDB.
     * Returns the updated entry (with deck, summary, platforms, cover).
     */
    suspend fun enrichWithIgdb(name: String): GameCacheEntity? {
        val igdbService = igdb ?: return dao.getByName(name)
        return try {
            val igdbResult = igdbService.searchByName(name) ?: return dao.getByName(name)
            val igdbId     = igdbResult.externalId.toIntOrNull()

            val existing   = dao.getByName(name)
            val genreNames = igdbResult.genre?.split(", ")
            val deck       = resolveDeck(
                existing?.deck,
                igdbResult.synopsis,
                genres = genreNames,
                title  = name,
            )

            val platformsJson = igdbResult.platforms?.let { gson.toJson(it) }
            val genresJson    = genreNames?.let { gson.toJson(it) }
            val releaseDateStr = existing?.releaseDate ?: igdbResult.releaseDate?.let {
                dateFmt.format(it)
            }

            val updated = GameCacheEntity(
                name        = name,
                gbId        = existing?.gbId,
                igdbId      = igdbId ?: existing?.igdbId,
                releaseDate = releaseDateStr,
                deck        = deck,
                coverUrl    = igdbResult.coverUrl ?: existing?.coverUrl,
                summary     = igdbResult.synopsis ?: existing?.summary,
                genres      = genresJson ?: existing?.genres,
                platforms   = platformsJson ?: existing?.platforms,
                coverOk     = igdbResult.coverUrl != null,
                updatedAt   = System.currentTimeMillis(),
            )
            dao.save(updated)
            Log.d("GameCacheService", "enriched '${name}' igdbId=$igdbId deck=${deck?.take(40)}")
            dao.getByName(name)
        } catch (e: Exception) {
            Log.w("GameCacheService", "enrich failed for '$name': ${e.message}")
            dao.getByName(name)
        }
    }

    /** Converts a GameCacheEntity to ApiSearchResult for use in search. */
    fun entityToResult(entity: GameCacheEntity): ApiSearchResult {
        val releaseDate = entity.releaseDate?.let { runCatching { dateFmt.parse(it) }.getOrNull() }
        val platforms   = entity.platforms?.let { runCatching { gson.fromJson(it, List::class.java)?.map { v -> v.toString() } }.getOrNull() }
        val genres      = entity.genres?.let   { runCatching { gson.fromJson(it, List::class.java)?.map { v -> v.toString() } }.getOrNull() }

        return ApiSearchResult(
            externalId  = entity.igdbId?.toString() ?: entity.gbId?.toString() ?: entity.name,
            title       = entity.name,
            coverUrl    = entity.coverUrl,
            synopsis    = entity.summary ?: entity.deck,
            releaseDate = releaseDate,
            genre       = genres?.joinToString(", "),
            platforms   = platforms,
            apiSource   = "game_cache",
        )
    }

    // ── Deck resolution (mirrors Flutter's _resolverDeck) ────────────────────

    private fun resolveDeck(existing: String?, summary: String?, genres: List<String>? = null, title: String? = null): String? {
        if (summary.isNullOrEmpty()) return existing
        if (existing != null) {
            val clean = existing.replace("...", "").trim()
            val isMarketing = summary.lowercase().startsWith(clean.lowercase())
            if (!isMarketing) return existing  // legitimate GB deck — preserve
        }
        return generateDeck(summary, genres = genres, title = title)
    }

    private val marketingPattern = Regex(
        """(launching\s+on|coming\s+to|available\s+on|(spring|summer|fall|winter|autumn)\s+\d{4}|launches?\s+in|release\s+date)""",
        RegexOption.IGNORE_CASE,
    )

    private fun generateDeck(summary: String?, genres: List<String>? = null, title: String? = null): String? {
        if (summary.isNullOrEmpty()) return null

        val paragraphs = summary.split(Regex("""\n\n+"""))
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.length > 20 }
        val descriptive = paragraphs.filter { !marketingPattern.containsMatchIn(it) }
        val cleanSummary = (if (descriptive.isNotEmpty()) descriptive else paragraphs).joinToString(" ")

        val genreLabel = genreToText(genres)
        val concept    = extractConcept(cleanSummary, title) ?: return null

        return if (genreLabel != null) {
            truncate("A $genreLabel $concept.")
        } else {
            truncate("${concept[0].uppercaseChar()}${concept.substring(1)}.")
        }
    }

    private fun extractConcept(summary: String, title: String?): String? {
        val setIn = Regex("""\bset\s+(?:in|during|within)\s+([^,.!?\n]{5,70})""", RegexOption.IGNORE_CASE)
        setIn.find(summary)?.groupValues?.get(1)?.let { cleanFragment(it).takeIf { f -> f.length >= 5 }?.let { return "set in $it" } }

        val takesPlace = Regex("""\btakes?\s+place\s+(?:in|within)\s+([^,.!?\n]{5,70})""", RegexOption.IGNORE_CASE)
        takesPlace.find(summary)?.groupValues?.get(1)?.let { cleanFragment(it).takeIf { f -> f.length >= 5 }?.let { return "set in $it" } }

        val wherePlayers = Regex("""\bwhere\s+players?\s+(?:can|must|will|are|may|have\s+to\s+)?(\w[\w\s]{5,55})""", RegexOption.IGNORE_CASE)
        wherePlayers.find(summary)?.groupValues?.get(1)?.let { cleanFragment(it).takeIf { f -> f.length >= 5 }?.let { return "where players $it" } }

        val about = Regex("""\babout\s+([^,.!?\n]{5,60})""", RegexOption.IGNORE_CASE)
        about.find(summary)?.groupValues?.get(1)?.let { cleanFragment(it).takeIf { f -> f.length >= 5 }?.let { return "about $it" } }

        return null
    }

    private fun cleanFragment(s: String): String {
        var r = s.replace(Regex("""[,;(].*$"""), "").trim()
        r = r.replace(Regex("""\s+(a|an|the|and|or|of|in|on|at|to|for|with|by|its|their)$""", RegexOption.IGNORE_CASE), "")
        if (r.length > 65) {
            val cut = r.lastIndexOf(' ', 65)
            r = if (cut > 15) r.substring(0, cut) else r.substring(0, 65)
        }
        return r.trim()
    }

    private fun genreToText(genres: List<String>?): String? {
        if (genres.isNullOrEmpty()) return null
        val map = mapOf(
            "Platform" to "platformer",
            "Role-playing (RPG)" to "RPG",
            "Adventure" to "adventure game",
            "Shooter" to "shooter",
            "Fighting" to "fighting game",
            "Puzzle" to "puzzle game",
            "Racing" to "racing game",
            "Strategy" to "strategy game",
            "Sport" to "sports game",
            "Hack and slash/Beat 'em up" to "action game",
            "Action" to "action game",
            "Simulation" to "simulation game",
            "Arcade" to "arcade game",
            "Music" to "music game",
            "Turn-based strategy (TBS)" to "turn-based strategy game",
            "Real Time Strategy (RTS)" to "real-time strategy game",
            "Tactical" to "tactical game",
            "Visual Novel" to "visual novel",
            "Point-and-click" to "point-and-click adventure",
        )
        return genres.firstNotNullOfOrNull { map[it] }
    }

    private fun truncate(s: String, max: Int = 180): String {
        if (s.length <= max) return s
        val cut = s.lastIndexOf(' ', max)
        return if (cut > max / 2) "${s.substring(0, cut)}..." else "${s.substring(0, max)}..."
    }
}
