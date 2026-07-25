package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

class SteamService(private val apiKey: String, private val steamId: String) {
    private val client  = OkHttpClient()
    private val gson    = Gson()
    private val baseUrl = "https://api.steampowered.com"

    private fun get(url: String): Map<String, Any?> {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(resp, type) ?: emptyMap()
    }

    fun getLibrary(includeUnplayed: Boolean = true): List<SteamGame> {
        val url = "$baseUrl/IPlayerService/GetOwnedGames/v1/?key=$apiKey&steamid=$steamId&include_appinfo=1&include_played_free_games=1"
        val games = ((get(url)["response"] as? Map<*, *>)?.get("games") as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: return emptyList()
        return games.map { SteamGame.fromJson(it) }
            .let { if (includeUnplayed) it else it.filter { g -> g.playedMinutes > 0 } }
    }

    fun getRecentGames(): List<SteamGame> {
        val url = "$baseUrl/IPlayerService/GetRecentlyPlayedGames/v1/?key=$apiKey&steamid=$steamId&count=0"
        val games = ((get(url)["response"] as? Map<*, *>)?.get("games") as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: return emptyList()
        return games.map { SteamGame.fromJson(it) }
    }

    fun getAchievements(appId: String): List<SteamAchievement> {
        val url  = "$baseUrl/ISteamUserStats/GetPlayerAchievements/v1/?key=$apiKey&steamid=$steamId&appid=$appId&l=english"
        val player = (get(url)["playerstats"] as? Map<*, *>) ?: return emptyList()
        return (player["achievements"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?.map { SteamAchievement.fromJson(it) } ?: emptyList()
    }

    fun getGameInfo(appId: Int): SteamAppDetails? {
        val url  = "https://store.steampowered.com/api/appdetails?appids=$appId"
        val data = ((get(url)["$appId"] as? Map<*, *>)?.get("data") as? Map<*, *>) ?: return null
        return SteamAppDetails.fromJson(data)
    }
}

data class SteamGame(
    val appId: Int,
    val name: String,
    val playedMinutes: Int,
    val iconUrl: String?,
) {
    companion object {
        fun fromJson(j: Map<String, Any?>) = SteamGame(
            appId         = (j["appid"] as? Double)?.toInt() ?: 0,
            name          = j["name"] as? String ?: "",
            playedMinutes = (j["playtime_forever"] as? Double)?.toInt() ?: 0,
            iconUrl       = (j["img_icon_url"] as? String)?.let {
                val id = (j["appid"] as? Double)?.toInt() ?: 0
                "https://media.steampowered.com/steamcommunity/public/images/apps/$id/$it.jpg"
            },
        )
    }
}

data class SteamAchievement(
    val apiName: String,
    val achieved: Boolean,
    val unlockTime: Long,
    val name: String?,
    val description: String?,
    val iconUrl: String?,
    val iconGrayUrl: String?,
) {
    companion object {
        fun fromJson(j: Map<String, Any?>) = SteamAchievement(
            apiName     = j["apiname"] as? String ?: "",
            achieved    = (j["achieved"] as? Double)?.toInt() == 1,
            unlockTime  = (j["unlocktime"] as? Double)?.toLong() ?: 0L,
            name        = j["name"] as? String,
            description = j["description"] as? String,
            iconUrl     = j["icon"] as? String,
            iconGrayUrl = j["icongray"] as? String,
        )
    }
}

data class SteamAppDetails(
    val name: String,
    val shortDescription: String?,
    val longDescription: String?,
    val headerImageUrl: String?,
    val developers: List<String>,
    val publishers: List<String>,
    val genres: List<String>,
) {
    companion object {
        fun fromJson(j: Map<*, *>) = SteamAppDetails(
            name             = j["name"] as? String ?: "",
            shortDescription = j["short_description"] as? String,
            longDescription  = j["detailed_description"] as? String,
            headerImageUrl   = j["header_image"] as? String,
            developers       = (j["developers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            publishers       = (j["publishers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            genres           = (j["genres"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                ?.map { it["description"] as? String ?: "" } ?: emptyList(),
        )
    }
}
