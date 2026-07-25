package com.hobbiesvault.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

data class ItadDeal(
    val store: String,
    val price: Double,
    val originalPrice: Double,
    val discount: Int,
    val url: String,
)

data class ItadPricePoint(
    val timestampMs: Long,
    val store: String,
    val price: Double,
    val regularPrice: Double,
    val discount: Int,
)

class ItadService(private val apiKey: String) {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private val base   = "https://api.isthereanydeal.com"

    private fun get(url: String): Map<String, Any?> {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "{}" }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(resp, type) ?: emptyMap()
    }

    private fun getList(url: String): List<Map<String, Any?>> {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute().use { it.body?.string() ?: "[]" }
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        return gson.fromJson(resp, type) ?: emptyList()
    }

    fun getPrices(steamAppId: Int): List<ItadDeal> {
        val id  = "app/$steamAppId"
        val url = "$base/game/prices/v2?key=$apiKey&id=${java.net.URLEncoder.encode(id, "UTF-8")}&country=BR"
        return try {
            val deals = (get(url)["deals"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: return emptyList()
            deals.mapNotNull { deal ->
                val store = (deal["shop"] as? Map<*, *>)?.get("name") as? String ?: return@mapNotNull null
                val price = deal["price"] as? Map<*, *>
                val cur   = (price?.get("amount") as? Double) ?: return@mapNotNull null
                val orig  = (price["regular"] as? Map<*, *>)?.let { (it["amount"] as? Double) } ?: cur
                val cut   = (deal["cut"] as? Double)?.toInt() ?: 0
                ItadDeal(store = store, price = cur, originalPrice = orig, discount = cut, url = deal["url"] as? String ?: "")
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Resolve o UUID interno do ITAD a partir do app id da Steam — necessário pro endpoint de histórico. */
    fun lookupGameUuid(steamAppId: Int): String? {
        val url = "$base/games/lookup/v1?key=$apiKey&appid=$steamAppId"
        return try {
            val res = get(url)
            if (res["found"] != true) return null
            (res["game"] as? Map<*, *>)?.get("id") as? String
        } catch (_: Exception) { null }
    }

    /** Série temporal real de mudanças de preço (não apenas o preço atual). */
    fun getPriceHistory(gameUuid: String, sinceDaysAgo: Int = 730): List<ItadPricePoint> {
        val sinceIso = java.time.Instant.now().minusSeconds(sinceDaysAgo * 86_400L).toString()
        val url = "$base/games/history/v2?key=$apiKey&id=$gameUuid&country=BR&since=${java.net.URLEncoder.encode(sinceIso, "UTF-8")}"
        return try {
            getList(url).mapNotNull { event ->
                val timestamp = (event["timestamp"] as? String)?.let {
                    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                } ?: return@mapNotNull null
                val store = (event["shop"] as? Map<*, *>)?.get("name") as? String ?: return@mapNotNull null
                val deal  = event["deal"] as? Map<*, *>
                val price = (deal?.get("price") as? Map<*, *>)?.get("amount") as? Double ?: return@mapNotNull null
                val regular = ((deal["regular"] as? Map<*, *>)?.get("amount") as? Double) ?: price
                val cut = (deal["cut"] as? Double)?.toInt() ?: 0
                ItadPricePoint(timestampMs = timestamp, store = store, price = price, regularPrice = regular, discount = cut)
            }.sortedBy { it.timestampMs }
        } catch (_: Exception) { emptyList() }
    }
}
