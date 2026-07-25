package com.hobbiesvault.model

import java.util.Date

data class ApiSearchResult(
    val externalId: String,
    val title: String,
    val coverUrl: String? = null,
    val artworkUrl: String? = null,
    val synopsis: String? = null,
    val releaseDate: Date? = null,
    val genre: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val platforms: List<String>? = null,
    val apiSource: String = "",
    val popularity: Int = 0,
    val seasons: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val authors: List<String>? = null,
    val pages: Int? = null,
    val isbn: String? = null,
)
