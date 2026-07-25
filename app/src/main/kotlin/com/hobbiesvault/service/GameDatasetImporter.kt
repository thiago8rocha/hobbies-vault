package com.hobbiesvault.service

import android.content.Context
import android.util.Log
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.data.db.entity.GameCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * Imports the bundled Giant Bomb game dataset into the local game_cache table.
 *
 * Dataset format per entry:
 * [gb_id, name, release_date, deck, igdb_id, capa_url, plataformas_pipe,
 *  category, game_modes_pipe, themes_pipe, rating, rating_count, hypes, parent_igdb_id]
 *
 * Only runs once (checked via row count). Skips mobile-only and erotic content.
 */
object GameDatasetImporter {

    private const val TAG = "GameDatasetImporter"
    private const val ASSET_PATH = "data/gb_games.bin"

    // IGDB platform IDs considered "main" (non-mobile)
    private val mainPlatforms = setOf(
        3, 5, 6, 8, 9, 11, 12, 14, 18, 19, 20, 21, 22, 23, 24, 25, 26,
        32, 33, 36, 37, 38, 41, 45, 46, 47, 48, 49, 56, 58, 67, 68, 78,
        92, 130, 136, 159, 160, 162, 163, 167, 169, 390,
    )

    suspend fun importIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val count = DB.games.count()
        if (count > 0) {
            Log.d(TAG, "Dataset already imported ($count rows) — skipping")
            return@withContext
        }

        Log.d(TAG, "Starting dataset import…")
        val start = System.currentTimeMillis()

        val json = readAssetGzip(context, ASSET_PATH)
        val array = JSONArray(json)

        val batch = mutableListOf<GameCacheEntity>()
        var imported = 0
        var skipped  = 0

        for (i in 0 until array.length()) {
            val arr = array.getJSONArray(i)

            val gbId        = arr.optInt(0)
            val name        = arr.optString(1).takeIf { it.isNotBlank() } ?: continue
            val releaseDate = arr.optString(2).takeIf { it.isNotBlank() }
            val deck        = arr.optString(3).takeIf { it.isNotBlank() }
            val igdbId      = arr.optString(4).takeIf { it.isNotBlank() }?.toIntOrNull()
            val capaUrl     = arr.optString(5).takeIf { it.isNotBlank() }
            val plataformas = parsePipe(arr.optString(6))
            val category    = arr.optString(7).toIntOrNull() ?: 0
            val gameModes   = parsePipe(arr.optString(8))
            val themes      = parsePipe(arr.optString(9))
            val parentIgdbId = arr.optString(13).toIntOrNull()

            // Skip DLC, expansions, bundles (categories 1,3), ports that are variants (parentIgdbId set),
            // mobile-only games, and erotic content (theme 42)
            val isMobileOnly = plataformas.isNotEmpty() && plataformas.none { it in mainPlatforms }
            val isErotic     = 42 in themes
            val isMMO        = 6 in gameModes

            if (isErotic || isMobileOnly || isMMO || category in setOf(1, 3)) {
                skipped++
                continue
            }

            batch.add(
                GameCacheEntity(
                    name        = name,
                    gbId        = gbId,
                    igdbId      = igdbId,
                    releaseDate = releaseDate,
                    deck        = deck,
                    coverUrl    = capaUrl,
                    coverOk     = capaUrl != null,
                    updatedAt   = System.currentTimeMillis(),
                )
            )

            // Insert in batches of 1000 to avoid large transactions
            if (batch.size >= 1000) {
                DB.games.saveAll(batch)
                imported += batch.size
                batch.clear()
            }
        }

        if (batch.isNotEmpty()) {
            DB.games.saveAll(batch)
            imported += batch.size
        }

        val elapsed = System.currentTimeMillis() - start
        Log.d(TAG, "Import complete: $imported rows in ${elapsed}ms (skipped $skipped)")
    }

    private fun parsePipe(raw: String?): Set<Int> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split("|").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    private fun readAssetGzip(context: Context, path: String): String {
        val compressed = context.assets.open(path)
        val gzip = GZIPInputStream(compressed)
        val out  = ByteArrayOutputStream()
        val buf  = ByteArray(8192)
        var n: Int
        while (gzip.read(buf).also { n = it } != -1) out.write(buf, 0, n)
        gzip.close()
        return out.toString("UTF-8")
    }
}
