package com.hobbiesvault.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Secrets(
    val tmdbBearerToken: String? = null,
    val igdbClientId: String? = null,
    val igdbClientSecret: String? = null,
    val googleBooksApiKey: String? = null,
    val anilistClientId: String? = null,
    val anilistUsername: String? = null,
    val steamApiKey: String? = null,
    val steamId: String? = null,
    val itadApiKey: String? = null,
) {
    val tmdbConfigurado        get() = !tmdbBearerToken.isNullOrEmpty()
    val igdbConfigurado        get() = !igdbClientId.isNullOrEmpty() && !igdbClientSecret.isNullOrEmpty()
    val googleBooksConfigurado get() = !googleBooksApiKey.isNullOrEmpty()
    val steamConfigurado       get() = !steamApiKey.isNullOrEmpty() && !steamId.isNullOrEmpty()
    val itadConfigurado        get() = !itadApiKey.isNullOrEmpty()

    companion object {
        private var _instance: Secrets? = null

        fun load(context: Context): Secrets {
            _instance?.let { return it }
            return try {
                val raw = context.assets.open("secrets.json").bufferedReader().readText()
                val type = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String> = Gson().fromJson(raw, type)
                Secrets(
                    tmdbBearerToken   = map["tmdb_bearer_token"],
                    igdbClientId      = map["igdb_client_id"],
                    igdbClientSecret  = map["igdb_client_secret"],
                    googleBooksApiKey = map["google_books_api_key"],
                    anilistClientId   = map["anilist_client_id"],
                    anilistUsername   = map["anilist_username"],
                    steamApiKey       = map["steam_api_key"],
                    steamId           = map["steam_id"],
                    itadApiKey        = map["itad_api_key"],
                ).also { _instance = it }
            } catch (_: Exception) {
                Secrets().also { _instance = it }
            }
        }
    }
}
