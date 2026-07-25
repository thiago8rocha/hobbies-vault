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
}
