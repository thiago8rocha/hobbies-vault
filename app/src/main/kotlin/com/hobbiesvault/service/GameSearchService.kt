package com.hobbiesvault.service

import com.hobbiesvault.data.db.dao.GameCacheDao
import com.hobbiesvault.data.db.entity.GameCacheEntity
import com.hobbiesvault.model.ApiSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameSearchService(
    private val igdb: IgdbService? = null,
    private val gameCache: GameCacheDao? = null,
    private val gameCacheSvc: GameCacheService? = null,
) {
    val available get() = igdb != null || gameCache != null

    // Maps IGDB platform IDs to friendly names (same as IgdbService.platformDisplayNames)
    private val igdbIdToName = mapOf(
        6   to "PC",
        7   to "PS1",
        8   to "PS2",
        9   to "PS3",
        48  to "PS4",
        167 to "PS5",
        38  to "PSP",
        46  to "PS Vita",
        18  to "NES",
        19  to "SNES",
        4   to "N64",
        21  to "GCN",
        5   to "Wii",
        41  to "Wii U",
        24  to "GBA",
        20  to "DS",
        37  to "3DS",
        130 to "Switch",
        137 to "Switch 2",
        11  to "Xbox",
        12  to "Xbox 360",
        49  to "Xbox One",
        169 to "Series X/S",
    )

    suspend fun search(query: String): List<ApiSearchResult> {
        if (query.isBlank()) return emptyList()

        val queryNorm = query.trim().lowercase()
        val now       = Date()

        return coroutineScope {
            val datasetJob = async {
                gameCache?.search(query)?.map { entityToResult(it) } ?: emptyList()
            }
            val igdbJob = async {
                igdb?.let { runCatching { it.searchGames(query) }.getOrElse { emptyList() } } ?: emptyList()
            }

            val datasetResults = datasetJob.await()
            val igdbResults    = igdbJob.await()

            // Dataset entries with cover take priority — same logic as Flutter
            val withCover    = datasetResults.filter { !it.coverUrl.isNullOrBlank() }
            val withoutCover = datasetResults.filter {  it.coverUrl.isNullOrBlank() }
            val coveredNames = withCover.map { it.title.lowercase() }.toSet()

            val merged = buildList {
                addAll(withCover)
                // Add IGDB results not already covered by dataset (except future releases)
                igdbResults.forEach { r ->
                    val name = r.title.lowercase()
                    if (!coveredNames.contains(name)) {
                        add(r)
                    } else {
                        // Keep IGDB entry only if it's a future release (different edition/date)
                        val future = r.releaseDate != null && r.releaseDate.after(now)
                        if (future) add(r)
                    }
                }
            }

            // Sort: relevance → future first → most recent
            merged.sortedWith(Comparator { a, b ->
                val relA = relevance(a.title, queryNorm)
                val relB = relevance(b.title, queryNorm)
                if (relA != relB) return@Comparator relB - relA

                val aDate   = a.releaseDate
                val bDate   = b.releaseDate
                val aFuture = aDate != null && aDate.after(now)
                val bFuture = bDate != null && bDate.after(now)

                when {
                    aFuture && bFuture -> aDate!!.compareTo(bDate!!)
                    aFuture            -> -1
                    bFuture            ->  1
                    aDate == null && bDate == null -> 0
                    aDate == null      ->  1
                    bDate == null      -> -1
                    else               -> bDate.compareTo(aDate)
                }
            })
        }
    }

    private fun relevance(title: String, queryNorm: String): Int {
        val t     = title.lowercase()
        val words = t.split(Regex("""[\s:,\.!\?]+"""))
        return when {
            words.any { it == queryNorm } -> 2
            t.startsWith(queryNorm)       -> 1
            else                          -> 0
        }
    }

    private fun entityToResult(entity: GameCacheEntity): ApiSearchResult {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val releaseDate = entity.releaseDate?.let { runCatching { dateFmt.parse(it) }.getOrNull() }

        // Parse pipe-separated platform IDs stored in game_cache
        val platforms = entity.platforms
            ?.split("|")
            ?.mapNotNull { it.trim().toIntOrNull()?.let { id -> igdbIdToName[id] } }
            ?.takeIf { it.isNotEmpty() }

        return ApiSearchResult(
            externalId  = entity.igdbId?.toString() ?: entity.gbId?.toString() ?: entity.name,
            title       = entity.name,
            coverUrl    = entity.coverUrl,
            synopsis    = entity.deck ?: entity.summary,
            releaseDate = releaseDate,
            platforms   = platforms,
            apiSource   = "giant_bomb",
        )
    }

    /** Called by the ViewModel when a game is added — enriches the local cache with IGDB data. */
    suspend fun enrichWithIgdb(name: String) {
        val igdbService = igdb ?: return
        val existing    = gameCache?.getByName(name) ?: return
        if (existing.coverOk && existing.summary != null) return // already enriched

        val igdbId = existing.igdbId ?: return
        runCatching {
            val details = igdbService.getGameDetails(igdbId)
            val updated = existing.copy(
                coverUrl    = details.coverUrl ?: existing.coverUrl,
                summary     = details.synopsis ?: existing.summary,
                coverOk     = details.coverUrl != null,
                updatedAt   = System.currentTimeMillis(),
            )
            gameCache?.save(updated)
        }
    }
}
