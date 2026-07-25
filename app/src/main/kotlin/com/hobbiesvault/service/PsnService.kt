package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

class PsnService(private val accessToken: String) {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://m.np.playstation.com/api/trophy/v1"

    private fun get(url: String): Map<String, Any?> {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(resp, type) ?: emptyMap()
    }

    fun getGamesWithTrophies(): List<PsnGameTrophies> {
        val url = "$base/users/me/trophyTitles?limit=200"
        return (get(url)["trophyTitles"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.map { PsnGameTrophies.fromJson(it) } ?: emptyList()
    }

    fun getTrophies(npCommunicationId: String, platform: String): List<PsnTrophy> {
        val url = "$base/npCommunicationIds/$npCommunicationId/trophyGroups/all/trophies?npServiceName=trophy&npLanguage=en"
        return (get(url)["trophies"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.map { PsnTrophy.fromJson(it) } ?: emptyList()
    }
}

data class PsnGameTrophies(
    val npCommunicationId: String,
    val trophyTitleName: String,
    val trophyTitleIconUrl: String?,
    val definedTrophies: PsnTrophyCount,
    val earnedTrophies: PsnTrophyCount,
    val progress: Int,
) {
    companion object {
        fun fromJson(j: Map<String, Any?>) = PsnGameTrophies(
            npCommunicationId  = j["npCommunicationId"] as? String ?: "",
            trophyTitleName    = j["trophyTitleName"] as? String ?: "",
            trophyTitleIconUrl = j["trophyTitleIconUrl"] as? String,
            definedTrophies    = PsnTrophyCount.fromJson(j["definedTrophies"] as? Map<*, *> ?: emptyMap<String, Any?>()),
            earnedTrophies     = PsnTrophyCount.fromJson(j["earnedTrophies"]  as? Map<*, *> ?: emptyMap<String, Any?>()),
            progress           = (j["progress"] as? Double)?.toInt() ?: 0,
        )
    }
}

data class PsnTrophyCount(val bronze: Int, val silver: Int, val gold: Int, val platinum: Int) {
    companion object {
        fun fromJson(j: Map<*, *>) = PsnTrophyCount(
            bronze   = (j["bronze"]   as? Double)?.toInt() ?: 0,
            silver   = (j["silver"]   as? Double)?.toInt() ?: 0,
            gold     = (j["gold"]     as? Double)?.toInt() ?: 0,
            platinum = (j["platinum"] as? Double)?.toInt() ?: 0,
        )
    }
}

data class PsnTrophy(
    val trophyId: Int, val trophyType: String, val trophyName: String?,
    val trophyDetail: String?, val trophyIconUrl: String?, val earned: Boolean,
) {
    companion object {
        fun fromJson(j: Map<String, Any?>) = PsnTrophy(
            trophyId      = (j["trophyId"] as? Double)?.toInt() ?: 0,
            trophyType    = j["trophyType"] as? String ?: "",
            trophyName    = j["trophyName"] as? String,
            trophyDetail  = j["trophyDetail"] as? String,
            trophyIconUrl = j["trophyIconUrl"] as? String,
            earned        = j["earned"] as? Boolean ?: false,
        )
    }
}
