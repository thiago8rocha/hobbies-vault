package com.hobbiesvault.debug

import com.hobbiesvault.data.repository.MediaRepository
import com.hobbiesvault.model.GameConsole
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import java.util.Date

/**
 * Popula a biblioteca com itens fictícios para testes de usabilidade em builds de debug.
 * Só é chamado (ver HobbiesVaultApp) quando BuildConfig.DEBUG é true, e só insere dados se a
 * biblioteca estiver vazia — não duplica em execuções seguintes do app.
 *
 * Nenhum item tem idExterno/fonteApi: o CacheUpdateWorker e o MediaCacheService só processam
 * itens com idExterno, então esses dados fake nunca disparam chamadas reais às APIs externas.
 */
object DebugSeeder {

    suspend fun seedIfEmpty(repo: MediaRepository) {
        if (repo.getAll().isNotEmpty()) return
        (games() + movies() + series() + mangas() + books()).forEach { repo.save(it) }
    }

    /** offset positivo = passado, negativo = futuro (para status "Aguardando Lançamento"). */
    private fun offsetDays(days: Int): Date = Date(System.currentTimeMillis() - days * 86_400_000L)
    private fun cover(seed: String) = "https://picsum.photos/seed/$seed/400/600"

    private fun games(): List<MediaItem> = listOf(
        MediaItem(type = MediaType.GAME, title = "Elden Ring", status = MediaStatus.FINISHED,
            console = GameConsole.STEAM, rating = 5.0, favorite = true, playedMinutes = 5820,
            genre = "RPG, Ação", developer = "FromSoftware", coverUrl = cover("elden-ring"),
            addedDate = offsetDays(200), completionDate = offsetDays(120), releaseDate = offsetDays(1400)),
        MediaItem(type = MediaType.GAME, title = "Hollow Knight", status = MediaStatus.COMPLETED,
            console = GameConsole.STEAM, rating = 4.5, achievementsUnlocked = 32, totalAchievements = 41,
            playedMinutes = 3120, genre = "Metroidvania", developer = "Team Cherry",
            coverUrl = cover("hollow-knight"), addedDate = offsetDays(300), completionDate = offsetDays(250)),
        MediaItem(type = MediaType.GAME, title = "God of War Ragnarök", status = MediaStatus.PLATINUM,
            console = GameConsole.PS5, rating = 5.0, favorite = true,
            goldTrophies = 8, silverTrophies = 14, bronzeTrophies = 22, platinumTrophy = true,
            playedMinutes = 2640, genre = "Ação, Aventura", developer = "Santa Monica Studio",
            coverUrl = cover("gow-ragnarok"), addedDate = offsetDays(150), completionDate = offsetDays(80),
            platinumCompletionDate = offsetDays(60)),
        MediaItem(type = MediaType.GAME, title = "The Last of Us Part II", status = MediaStatus.FINISHED,
            console = GameConsole.PS4, rating = 4.0,
            goldTrophies = 3, silverTrophies = 9, bronzeTrophies = 18, platinumTrophy = false,
            playedMinutes = 1560, genre = "Ação, Survival", developer = "Naughty Dog",
            coverUrl = cover("tlou2"), addedDate = offsetDays(400), completionDate = offsetDays(370)),
        MediaItem(type = MediaType.GAME, title = "Animal Crossing: New Horizons", status = MediaStatus.PLAYING,
            console = GameConsole.NS, rating = 4.5, playedMinutes = 9000, genre = "Simulação",
            developer = "Nintendo", coverUrl = cover("acnh"), addedDate = offsetDays(600)),
        MediaItem(type = MediaType.GAME, title = "The Legend of Zelda: TOTK", status = MediaStatus.REPLAYING,
            console = GameConsole.NS, rating = 5.0, favorite = true, playedMinutes = 4200, genre = "Aventura",
            developer = "Nintendo", coverUrl = cover("totk"), addedDate = offsetDays(500)),
        MediaItem(type = MediaType.GAME, title = "Baldur's Gate 3", status = MediaStatus.PLAYING,
            console = GameConsole.STEAM, rating = 5.0, playedMinutes = 6600, genre = "RPG",
            developer = "Larian Studios", coverUrl = cover("bg3"), addedDate = offsetDays(90)),
        MediaItem(type = MediaType.GAME, title = "Stardew Valley", status = MediaStatus.COMPLETED,
            console = GameConsole.PC, rating = 4.5, playedMinutes = 12000, genre = "Simulação, Indie",
            developer = "ConcernedApe", coverUrl = cover("stardew"), addedDate = offsetDays(800), completionDate = offsetDays(700)),
        MediaItem(type = MediaType.GAME, title = "Hades", status = MediaStatus.FINISHED,
            console = GameConsole.NS, rating = 5.0, playedMinutes = 1800, genre = "Roguelike",
            developer = "Supergiant Games", coverUrl = cover("hades"), addedDate = offsetDays(250), completionDate = offsetDays(200)),
        MediaItem(type = MediaType.GAME, title = "Cyberpunk 2077", status = MediaStatus.QUEUED,
            console = GameConsole.STEAM, genre = "RPG, Ação", developer = "CD Projekt Red",
            coverUrl = cover("cyberpunk"), addedDate = offsetDays(10)),
        MediaItem(type = MediaType.GAME, title = "Persona 5 Royal", status = MediaStatus.QUEUED,
            console = GameConsole.PS4, genre = "RPG", developer = "Atlus",
            coverUrl = cover("p5r"), addedDate = offsetDays(5)),
    )

    private fun movies(): List<MediaItem> = listOf(
        MediaItem(type = MediaType.MOVIE, title = "Duna: Parte Dois", status = MediaStatus.WATCHED, rating = 5.0,
            favorite = true, streamingPlatform = "Max", genre = "Ficção Científica", coverUrl = cover("dune2"),
            addedDate = offsetDays(60), completionDate = offsetDays(58), releaseDate = offsetDays(150)),
        MediaItem(type = MediaType.MOVIE, title = "Oppenheimer", status = MediaStatus.WATCHED, rating = 4.5,
            streamingPlatform = "Prime Video", genre = "Drama, Biografia", coverUrl = cover("oppenheimer"),
            addedDate = offsetDays(200), completionDate = offsetDays(195)),
        MediaItem(type = MediaType.MOVIE, title = "Pobres Criaturas", status = MediaStatus.WATCHED, rating = 4.0,
            streamingPlatform = "Star+", genre = "Comédia, Drama", coverUrl = cover("poor-things"),
            addedDate = offsetDays(100), completionDate = offsetDays(95)),
        MediaItem(type = MediaType.MOVIE, title = "Interestelar", status = MediaStatus.REWATCHING, rating = 5.0,
            favorite = true, streamingPlatform = "Netflix", genre = "Ficção Científica",
            coverUrl = cover("interstellar"), addedDate = offsetDays(900)),
        MediaItem(type = MediaType.MOVIE, title = "Divertida Mente 2", status = MediaStatus.WATCHED, rating = 4.0,
            streamingPlatform = "Disney+", genre = "Animação", coverUrl = cover("inside-out-2"),
            addedDate = offsetDays(20), completionDate = offsetDays(18)),
        MediaItem(type = MediaType.MOVIE, title = "Coringa: Delírio a Dois", status = MediaStatus.QUEUED,
            genre = "Drama, Crime", coverUrl = cover("joker2"), addedDate = offsetDays(3)),
        MediaItem(type = MediaType.MOVIE, title = "Deadpool & Wolverine", status = MediaStatus.QUEUED,
            genre = "Ação, Comédia", coverUrl = cover("deadpool3"), addedDate = offsetDays(7)),
        MediaItem(type = MediaType.MOVIE, title = "Beetlejuice Beetlejuice", status = MediaStatus.WAITING_RELEASE,
            genre = "Comédia, Fantasia", coverUrl = cover("beetlejuice2"), addedDate = offsetDays(15),
            releaseDate = offsetDays(-30)),
        MediaItem(type = MediaType.MOVIE, title = "A Substância", status = MediaStatus.WATCHED, rating = 4.5,
            streamingPlatform = "Mubi", genre = "Terror", coverUrl = cover("the-substance"),
            addedDate = offsetDays(40), completionDate = offsetDays(38)),
        MediaItem(type = MediaType.MOVIE, title = "Vidas Passadas", status = MediaStatus.WATCHED, rating = 4.5,
            streamingPlatform = "Netflix", genre = "Romance, Drama", coverUrl = cover("past-lives"),
            addedDate = offsetDays(300), completionDate = offsetDays(295)),
    )

    private fun series(): List<MediaItem> = listOf(
        MediaItem(type = MediaType.SERIES, title = "The Bear", status = MediaStatus.WATCHING, rating = 5.0,
            favorite = true, streamingPlatform = "Disney+", genre = "Drama, Comédia",
            currentProgress = 24, totalProgress = 38, coverUrl = cover("the-bear"), addedDate = offsetDays(120)),
        MediaItem(type = MediaType.SERIES, title = "Arcane", status = MediaStatus.HISTORY, rating = 5.0,
            favorite = true, streamingPlatform = "Netflix", genre = "Animação, Fantasia",
            currentProgress = 9, totalProgress = 9, coverUrl = cover("arcane"), addedDate = offsetDays(500), completionDate = offsetDays(480)),
        MediaItem(type = MediaType.SERIES, title = "Round 6", status = MediaStatus.WAITING_EPISODES, rating = 4.0,
            streamingPlatform = "Netflix", genre = "Drama, Suspense",
            currentProgress = 9, totalProgress = 9, coverUrl = cover("squid-game"), addedDate = offsetDays(700)),
        MediaItem(type = MediaType.SERIES, title = "Shogun", status = MediaStatus.HISTORY, rating = 5.0,
            streamingPlatform = "Star+", genre = "Drama, Histórico",
            currentProgress = 10, totalProgress = 10, coverUrl = cover("shogun"), addedDate = offsetDays(90), completionDate = offsetDays(70)),
        MediaItem(type = MediaType.SERIES, title = "Fallout", status = MediaStatus.WATCHING, rating = 4.0,
            streamingPlatform = "Prime Video", genre = "Ficção Científica",
            currentProgress = 5, totalProgress = 8, coverUrl = cover("fallout"), addedDate = offsetDays(50)),
        MediaItem(type = MediaType.SERIES, title = "House of the Dragon", status = MediaStatus.WATCHING, rating = 4.5,
            streamingPlatform = "Max", genre = "Fantasia",
            currentProgress = 4, totalProgress = 8, coverUrl = cover("hotd"), addedDate = offsetDays(30)),
        MediaItem(type = MediaType.SERIES, title = "Breaking Bad", status = MediaStatus.REWATCHING, rating = 5.0,
            favorite = true, streamingPlatform = "Netflix", genre = "Drama, Crime",
            currentProgress = 30, totalProgress = 62, coverUrl = cover("breaking-bad"), addedDate = offsetDays(1200)),
        MediaItem(type = MediaType.SERIES, title = "Wandinha", status = MediaStatus.QUEUED,
            genre = "Comédia, Terror", coverUrl = cover("wednesday"), addedDate = offsetDays(4)),
        MediaItem(type = MediaType.SERIES, title = "Slow Horses", status = MediaStatus.QUEUED,
            genre = "Espionagem, Drama", coverUrl = cover("slow-horses"), addedDate = offsetDays(2)),
        MediaItem(type = MediaType.SERIES, title = "Loki", status = MediaStatus.HISTORY, rating = 4.0,
            streamingPlatform = "Disney+", genre = "Fantasia, Ficção Científica",
            currentProgress = 12, totalProgress = 12, coverUrl = cover("loki"), addedDate = offsetDays(600), completionDate = offsetDays(560)),
    )

    private fun mangas(): List<MediaItem> = listOf(
        MediaItem(type = MediaType.MANGA, title = "Jujutsu Kaisen", status = MediaStatus.READING, rating = 5.0,
            favorite = true, genre = "Ação, Sobrenatural", currentProgress = 245, totalProgress = 271,
            coverUrl = cover("jjk"), addedDate = offsetDays(400)),
        MediaItem(type = MediaType.MANGA, title = "Chainsaw Man", status = MediaStatus.READING, rating = 4.5,
            genre = "Ação, Horror", currentProgress = 170, totalProgress = 180,
            coverUrl = cover("csm"), addedDate = offsetDays(200)),
        MediaItem(type = MediaType.MANGA, title = "Vagabond", status = MediaStatus.ON_HOLD, rating = 5.0,
            genre = "Ação, Histórico", currentProgress = 180, totalProgress = 327,
            coverUrl = cover("vagabond"), addedDate = offsetDays(900)),
        MediaItem(type = MediaType.MANGA, title = "Vinland Saga", status = MediaStatus.READ, rating = 5.0,
            favorite = true, genre = "Ação, Histórico", currentProgress = 210, totalProgress = 210,
            coverUrl = cover("vinland-saga"), addedDate = offsetDays(600), completionDate = offsetDays(550)),
        MediaItem(type = MediaType.MANGA, title = "Berserk", status = MediaStatus.REREADING, rating = 5.0,
            favorite = true, genre = "Ação, Fantasia Sombria", currentProgress = 60, totalProgress = 374,
            coverUrl = cover("berserk"), addedDate = offsetDays(1500)),
        MediaItem(type = MediaType.MANGA, title = "One Piece", status = MediaStatus.READING, rating = 5.0,
            genre = "Aventura, Ação", currentProgress = 1100, totalProgress = 1120,
            coverUrl = cover("one-piece"), addedDate = offsetDays(2000)),
        MediaItem(type = MediaType.MANGA, title = "Solo Leveling", status = MediaStatus.READ, rating = 4.5,
            genre = "Ação, Fantasia", currentProgress = 179, totalProgress = 179,
            coverUrl = cover("solo-leveling"), addedDate = offsetDays(300), completionDate = offsetDays(260)),
        MediaItem(type = MediaType.WEBTOON, title = "Omniscient Reader", status = MediaStatus.READING, rating = 4.5,
            genre = "Fantasia, Ação", currentProgress = 120, totalProgress = 195,
            coverUrl = cover("orv"), addedDate = offsetDays(150)),
        MediaItem(type = MediaType.WEBTOON, title = "Tower of God", status = MediaStatus.QUEUED,
            genre = "Fantasia, Aventura", coverUrl = cover("tower-of-god"), addedDate = offsetDays(8)),
        MediaItem(type = MediaType.MANGA, title = "Frieren", status = MediaStatus.QUEUED,
            genre = "Fantasia, Aventura", coverUrl = cover("frieren"), addedDate = offsetDays(6)),
    )

    private fun books(): List<MediaItem> = listOf(
        MediaItem(type = MediaType.BOOK, title = "Duna", status = MediaStatus.READ, rating = 5.0,
            favorite = true, genre = "Ficção Científica", currentProgress = 688, totalProgress = 688,
            coverUrl = cover("dune-book"), addedDate = offsetDays(500), completionDate = offsetDays(460)),
        MediaItem(type = MediaType.BOOK, title = "O Nome do Vento", status = MediaStatus.READING, rating = 5.0,
            genre = "Fantasia", currentProgress = 320, totalProgress = 662,
            coverUrl = cover("name-of-the-wind"), addedDate = offsetDays(60)),
        MediaItem(type = MediaType.BOOK, title = "Sapiens", status = MediaStatus.READ, rating = 4.5,
            genre = "Não-ficção, História", currentProgress = 464, totalProgress = 464,
            coverUrl = cover("sapiens"), addedDate = offsetDays(700), completionDate = offsetDays(650)),
        MediaItem(type = MediaType.BOOK, title = "Mistborn: O Império Final", status = MediaStatus.REREADING, rating = 5.0,
            favorite = true, genre = "Fantasia", currentProgress = 150, totalProgress = 541,
            coverUrl = cover("mistborn"), addedDate = offsetDays(1000)),
        MediaItem(type = MediaType.BOOK, title = "1984", status = MediaStatus.READ, rating = 5.0,
            genre = "Ficção Distópica", currentProgress = 328, totalProgress = 328,
            coverUrl = cover("1984"), addedDate = offsetDays(1200), completionDate = offsetDays(1150)),
        MediaItem(type = MediaType.BOOK, title = "Projeto Hail Mary", status = MediaStatus.READING, rating = 4.5,
            genre = "Ficção Científica", currentProgress = 200, totalProgress = 476,
            coverUrl = cover("hail-mary"), addedDate = offsetDays(20)),
        MediaItem(type = MediaType.BOOK, title = "O Problema dos Três Corpos", status = MediaStatus.QUEUED,
            genre = "Ficção Científica", coverUrl = cover("three-body"), addedDate = offsetDays(5)),
        MediaItem(type = MediaType.BOOK, title = "Fourth Wing", status = MediaStatus.QUEUED,
            genre = "Fantasia, Romance", coverUrl = cover("fourth-wing"), addedDate = offsetDays(9)),
        MediaItem(type = MediaType.BOOK, title = "A Metamorfose", status = MediaStatus.DROPPED, rating = 2.5,
            genre = "Ficção", currentProgress = 40, totalProgress = 96,
            coverUrl = cover("metamorphosis"), addedDate = offsetDays(300)),
        MediaItem(type = MediaType.BOOK, title = "Educated", status = MediaStatus.READ, rating = 4.0,
            genre = "Biografia", currentProgress = 352, totalProgress = 352,
            coverUrl = cover("educated"), addedDate = offsetDays(400), completionDate = offsetDays(370)),
    )
}
