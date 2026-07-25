package com.hobbiesvault.model

import androidx.compose.ui.graphics.Color

enum class MediaStatus(val label: String, val dbValue: String) {
    // Games
    COMPLETED("Completado",    "completado"),
    FINISHED("Zerado",         "finalizado"),
    PLAYING("Jogando",         "jogando"),
    REPLAYING("Rejogando",     "rejogando"),
    PLATINUM("Platinado",      "platinado"),
    // Movies
    WATCHED("Assistido",       "assistido"),
    WATCHING("Assistindo",     "assistindo"),
    REWATCHING("Reassistindo", "reassistindo"),
    // Series
    CONCLUDED("Concluída",     "concluida"),
    HISTORY("Histórico",       "historico"),
    WAITING_EPISODES("Aguardando Episódios", "aguardandoEpisodios"),
    // Manga / Books
    READ("Lido",               "lido"),
    READING("Lendo",           "lendo"),
    REREADING("Relendo",       "relendo"),
    ON_HOLD("Pausado",         "pausado"),
    // All
    QUEUED("Na Fila",          "naFila"),
    DROPPED("Abandonado",      "abandonado"),
    WAITING_RELEASE("Aguardando Lançamento", "aguardandoLancamento");

    val color: Color get() = when (this) {
        COMPLETED, FINISHED, WATCHED, CONCLUDED, HISTORY, READ -> Color(0xFF4CAF50)
        PLAYING, WATCHING, READING                              -> Color(0xFF2196F3)
        REPLAYING, REWATCHING, REREADING                        -> Color(0xFF00BCD4)
        PLATINUM                                                -> Color(0xFF9C64FE)
        WAITING_EPISODES, QUEUED                                -> Color(0xFFFF9800)
        ON_HOLD, WAITING_RELEASE                                -> Color(0xFF9E9E9E)
        DROPPED                                                 -> Color(0xFFF44336)
    }

    companion object {
        fun fromDb(value: String): MediaStatus =
            entries.firstOrNull { it.dbValue == value } ?: QUEUED

        fun forSteam()      = listOf(PLAYING, REPLAYING, FINISHED, COMPLETED, QUEUED)
        fun forPlayStation()= listOf(PLAYING, REPLAYING, FINISHED, PLATINUM, QUEUED)
        fun forNintendo()   = listOf(PLAYING, REPLAYING, FINISHED, COMPLETED, QUEUED)
        fun forOtherGames() = listOf(PLAYING, REPLAYING, FINISHED, QUEUED)
        fun forMovie()      = listOf(WATCHED, REWATCHING, QUEUED, WAITING_RELEASE)
        fun forSeries()     = listOf(WATCHING, REWATCHING, QUEUED, HISTORY)
        fun forSeriesAdd()  = listOf(WATCHING, REWATCHING, QUEUED, HISTORY)
        fun forManga()      = listOf(READING, REREADING, ON_HOLD, READ, QUEUED)
        fun forMangaAdd()   = listOf(READING, REREADING, QUEUED)
        fun forBook()       = listOf(READING, REREADING, READ, QUEUED, DROPPED)
        fun forBookAdd()    = listOf(READING, REREADING, QUEUED)
    }
}
