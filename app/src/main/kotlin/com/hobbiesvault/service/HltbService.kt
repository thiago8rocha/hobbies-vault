package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class HltbResult(
    val gameId: Int,
    val gameName: String,
    val mainStorySeconds: Int?,
    val mainExtraSeconds: Int?,
    val completionistSeconds: Int?,
    val imageUrl: String?,
) {
    val mainStoryHours get() = mainStorySeconds?.let { it / 3600.0 }
    val completionistHours get() = completionistSeconds?.let { it / 3600.0 }
}

class HltbService {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://howlongtobeat.com/api"

    fun search(query: String): List<HltbResult> {
        if (query.isBlank()) return emptyList()
        val payload = """{"searchType":"games","searchTerms":["${query.replace("\"","")}"],"searchPage":1,"size":5,"searchOptions":{"games":{"userId":0,"platform":"","sortCategory":"popular","rangeCategory":"main","rangeTime":{"min":null,"max":null},"gameplay":{"perspective":"","flow":"","genre":"","subgenre":""},"rangeYear":{"min":"","max":""},"modifier":""},"users":{"sortCategory":"postcount"},"lists":{"sortCategory":"follows"},"filter":"","sort":0,"randomizer":0}}"""
        return try {
            val req = Request.Builder()
                .url("$base/search")
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://howlongtobeat.com")
                .addHeader("Referer", "https://howlongtobeat.com/")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(resp, type) ?: return emptyList()
            (json["data"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { mapResult(it) } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun mapResult(r: Map<String, Any?>) = HltbResult(
        gameId               = (r["game_id"] as? Double)?.toInt() ?: 0,
        gameName             = r["game_name"] as? String ?: "",
        mainStorySeconds     = (r["comp_main"] as? Double)?.toInt()?.takeIf { it > 0 },
        mainExtraSeconds     = (r["comp_plus"] as? Double)?.toInt()?.takeIf { it > 0 },
        completionistSeconds = (r["comp_100"] as? Double)?.toInt()?.takeIf { it > 0 },
        imageUrl             = (r["game_image"] as? String)?.let { "https://howlongtobeat.com/games/$it" },
    )
}
