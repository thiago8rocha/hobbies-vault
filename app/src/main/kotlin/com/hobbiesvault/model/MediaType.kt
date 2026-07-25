package com.hobbiesvault.model

import androidx.compose.ui.graphics.Color

enum class MediaType(val label: String, val dbValue: String) {
    GAME("Game",       "jogo"),
    MANGA("Manga",     "manga"),
    WEBTOON("Webtoon", "webtoon"),
    SERIES("Series",   "serie"),
    MOVIE("Movie",     "filme"),
    BOOK("Book",       "livro");

    val labelPt: String get() = when (this) {
        GAME    -> "Jogo"
        MANGA   -> "Mangá"
        WEBTOON -> "Webtoon"
        SERIES  -> "Série"
        MOVIE   -> "Filme"
        BOOK    -> "Livro"
    }

    val labelPtPlural: String get() = when (this) {
        GAME    -> "Jogos"
        MANGA   -> "Mangás"
        WEBTOON -> "Webtoons"
        SERIES  -> "Séries"
        MOVIE   -> "Filmes"
        BOOK    -> "Livros"
    }

    val color: Color get() = when (this) {
        GAME              -> Color(0xFF7B1FA2)
        MANGA, WEBTOON    -> Color(0xFFE91E63)
        SERIES            -> Color(0xFF1976D2)
        MOVIE             -> Color(0xFFFF6F00)
        BOOK              -> Color(0xFF388E3C)
    }

    companion object {
        fun fromDb(value: String) = entries.firstOrNull { it.dbValue == value } ?: GAME
    }
}
