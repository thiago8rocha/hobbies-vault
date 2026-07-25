package com.hobbiesvault.service

import android.util.Log
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaCacheService {
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun load(item: MediaItem): Map<String, Any?>? {
        val id = item.id ?: return null
        return DB.cache.load(id)
    }

    fun doubleCheck(item: MediaItem, onUpdated: (suspend () -> Unit)? = null) {
        if (item.externalId == null) return
        scope.launch {
            runCatching { fetchAndStore(item) }
            onUpdated?.invoke()
        }
    }

    suspend fun fetchAndPersist(item: MediaItem) = withContext(Dispatchers.IO) {
        runCatching { fetchAndStore(item) }.getOrNull()
    }

    private suspend fun fetchAndStore(item: MediaItem) {
        val id      = item.id ?: return
        Log.d("MediaCache", "fetchAndStore: item=${item.title} id=$id type=${item.type}")
        val newData = fetchFromApi(item) ?: run {
            Log.w("MediaCache", "fetchAndStore: fetchFromApi returned null for ${item.title}")
            return
        }

        val current = DB.cache.load(id)
        if (hasCacheChanged(current, newData)) {
            Log.d("MediaCache", "fetchAndStore: saving cache for ${item.title}")
            DB.cache.save(id, newData)
        } else {
            Log.d("MediaCache", "fetchAndStore: cache unchanged for ${item.title}")
        }

        when (item.type) {
            MediaType.SERIES                   -> checkSeriesStatus(item, newData)
            MediaType.MANGA, MediaType.WEBTOON -> checkMangaSerializationStatus(item, newData)
            else                                -> checkReleaseStatus(item, newData)
        }

        if (item.type == MediaType.MANGA || item.type == MediaType.WEBTOON) {
            syncAniListProgress(item)
        }
    }

    // ── Hiato tracking (mangás/webtoons) ────────────────────────────────────────
    // AniList/MangaDex reportam status de publicação em PT-BR via 'serializationStatus'.
    // Ao entrar em hiato movemos para Em Hiato; ao sair, para Lido (se cancelado) ou
    // Lendo (se voltou a ser publicado) — nos dois casos avisando via notificação.
    private suspend fun checkMangaSerializationStatus(item: MediaItem, data: Map<String, Any?>) {
        val status = data["serializationStatus"] as? String ?: return
        val id = item.id ?: return
        Log.d("MediaCache", "checkMangaSerializationStatus: item=${item.title} localStatus=${item.status} remoteStatus=$status")
        when {
            status == "Em hiato" && item.status !in listOf(MediaStatus.ON_HOLD, MediaStatus.READ, MediaStatus.DROPPED) -> {
                DB.repo.update(item.copy(status = MediaStatus.ON_HOLD))
                NotificationHelper.notifyStatusChange(id, item.title, "Entrou em hiato — movido para Em Hiato")
            }
            status != "Em hiato" && item.status == MediaStatus.ON_HOLD -> when (status) {
                "Cancelado" -> {
                    DB.repo.update(item.copy(status = MediaStatus.READ, completionDate = item.completionDate ?: java.util.Date()))
                    NotificationHelper.notifyStatusChange(id, item.title, "Publicação cancelada — movido para Lido")
                }
                "Em andamento" -> {
                    DB.repo.update(item.copy(status = MediaStatus.READING))
                    NotificationHelper.notifyStatusChange(id, item.title, "Voltou a ser publicado — movido para Lendo")
                }
                else -> {}
            }
        }
    }

    // ── AniList progress sync ─────────────────────────────────────────────────
    // O usuário lê no Yokai, que atualiza o progresso na conta AniList em tempo
    // real. Se um username público estiver configurado, refletimos esse progresso
    // aqui — mas só avançamos (nunca regredimos edição manual mais recente).
    private suspend fun syncAniListProgress(item: MediaItem) {
        val username = ApiServices.aniListUsername ?: return
        if (item.apiSource != "anilist") return
        val mediaId = item.externalId?.toIntOrNull() ?: return
        val progress = withContext(Dispatchers.IO) {
            runCatching { ApiServices.anilist.getUserProgress(username, mediaId) }.getOrNull()
        } ?: return
        val merged = computeMergedProgress(item.currentProgress, progress) ?: return
        DB.repo.update(item.copy(currentProgress = merged))
    }

    // ── Release status check (games, filmes, mangás, livros) ───────────────────
    // Enquanto o status ficar em WAITING_RELEASE, a tela de detalhe mostra "Em Breve"/
    // "Aguardando Lançamento" indefinidamente, mesmo após o lançamento já ter ocorrido,
    // pois nada mais reavalia esse status. Assim que a data de lançamento (vinda da API)
    // já tiver passado, movemos o item para a fila (QUEUED).
    private suspend fun checkReleaseStatus(item: MediaItem, data: Map<String, Any?>) {
        if (item.status != MediaStatus.WAITING_RELEASE) return
        val releaseMs = (data["releaseDate"] as? Long) ?: (data["releaseDate"] as? Double)?.toLong() ?: return
        if (releaseMs <= System.currentTimeMillis()) {
            DB.repo.update(item.copy(status = MediaStatus.QUEUED))
        }
    }

    private suspend fun fetchFromApi(item: MediaItem): Map<String, Any?>? {
        val externalId = item.externalId ?: return null
        return when (item.type) {
            MediaType.MOVIE              -> fetchMovie(externalId)
            MediaType.SERIES             -> fetchSeries(externalId)
            MediaType.GAME               -> fetchGame(item)
            MediaType.MANGA, MediaType.WEBTOON -> fetchManga(item)
            MediaType.BOOK               -> fetchBook(item)
        }
    }

    // ── Fetchers by type ──────────────────────────────────────────────────────

    private suspend fun fetchMovie(externalId: String): Map<String, Any?>? {
        Log.d("MediaCache", "fetchMovie: externalId=$externalId tmdbAvailable=${ApiServices.tmdbAvailable}")
        if (!ApiServices.tmdbAvailable) return null
        val tmdbId = externalId.toIntOrNull() ?: externalId.toDoubleOrNull()?.toInt() ?: return null
        val d = withContext(Dispatchers.IO) {
            runCatching { ApiServices.tmdb.getMovieDetails(tmdbId) }
                .onFailure { Log.e("MediaCache", "fetchMovie error", it) }
                .getOrNull()
        } ?: return null
        Log.d("MediaCache", "fetchMovie: got data title=${d.title} cast=${d.cast.size} genres=${d.genres}")

        return buildMap {
            put("title",          d.title)
            put("synopsis",       d.synopsis)
            put("posterUrl",      d.posterUrl)
            put("backdropUrl",    d.backdropUrl)
            put("runtimeMinutes", d.runtimeMinutes)
            put("releaseDate",    d.releaseDate?.time)
            put("genres",         d.genres)
            put("tmdbStatus",     d.tmdbStatus)
            put("cast",  d.cast.map  { mapOf("name" to it.name, "character" to it.character, "photoUrl" to it.photoUrl) })
            put("crew",  d.crew.map  { mapOf("name" to it.name, "role" to it.role, "photoUrl" to it.photoUrl) })
            put("providers", d.uniqueProviders.map { mapOf("name" to it.name, "logoUrl" to it.logoUrl) })
            put("related", d.related.map { mapOf("id" to it.id, "title" to it.title, "posterUrl" to it.posterUrl) })
        }
    }

    private suspend fun fetchSeries(externalId: String): Map<String, Any?>? {
        if (!ApiServices.tmdbAvailable) return null
        val tmdbId = externalId.toIntOrNull() ?: externalId.toDoubleOrNull()?.toInt() ?: return null
        val d = withContext(Dispatchers.IO) {
            runCatching { ApiServices.tmdb.getSeriesDetails(tmdbId) }.getOrNull()
        } ?: return null

        return buildMap {
            put("title",         d.title)
            put("synopsis",      d.synopsis)
            put("posterUrl",     d.posterUrl)
            put("backdropUrl",   d.backdropUrl)
            put("totalEpisodes", d.totalEpisodes)
            put("firstAirDate",  d.firstAirDate?.time)
            put("lastAirDate",   d.lastAirDate?.time)
            put("genres",        d.genres)
            put("tmdbStatus",    d.tmdbStatus)
            put("network",       d.network)
            put("cast",  d.cast.map  { mapOf("name" to it.name, "character" to it.character, "photoUrl" to it.photoUrl) })
            put("crew",  d.crew.map  { mapOf("name" to it.name, "role" to it.role, "photoUrl" to it.photoUrl) })
            put("providers", d.uniqueProviders.map { mapOf("name" to it.name, "logoUrl" to it.logoUrl) })
            put("seasons", d.seasons.map { mapOf("number" to it.number, "name" to it.name, "episodes" to it.episodes, "posterUrl" to it.posterUrl, "airDate" to it.airDate?.time) })
            put("related", d.related.map { mapOf("id" to it.id, "title" to it.title, "posterUrl" to it.posterUrl) })
        }
    }

    private suspend fun fetchGame(item: MediaItem): Map<String, Any?>? {
        if (!ApiServices.igdbAvailable && ApiServices.gameCache == null) return null

        val result = mutableMapOf<String, Any?>()

        // 1. Get IGDB details by ID (works for apiSource="igdb" and legacy items)
        val externalIdInt = item.externalId?.toIntOrNull()
        val igdbResult = if (ApiServices.igdbAvailable && externalIdInt != null && item.apiSource != "giant_bomb") {
            withContext(Dispatchers.IO) {
                runCatching { ApiServices.igdb.getGameDetails(externalIdInt) }.getOrNull()
            }
        } else null

        if (igdbResult != null) {
            result["title"]       = igdbResult.title
            result["synopsis"]    = igdbResult.synopsis
            result["coverUrl"]    = igdbResult.coverUrl ?: item.coverUrl
            result["artworkUrl"]  = igdbResult.artworkUrl
            result["genre"]       = igdbResult.genre
            result["developer"]   = igdbResult.developer
            result["publisher"]   = igdbResult.publisher
            result["platforms"]   = igdbResult.platforms
            result["releaseDate"] = igdbResult.releaseDate?.time
            result["apiSource"]   = igdbResult.apiSource
        }

        // 2. Enrich with GameCacheService (deck from GB + full IGDB metadata by name)
        // This is the equivalent of Flutter's enriquecerComIgdb(titulo)
        val gameCacheSvc = ApiServices.gameCache
        if (gameCacheSvc != null) {
            val gc = withContext(Dispatchers.IO) {
                gameCacheSvc.enrichWithIgdb(igdbResult?.title ?: item.title)
            }
            if (gc != null) {
                // deck is stored for internal use; summary (IGDB) is shown as synopsis
                if (!gc.deck.isNullOrBlank())    result["deck"]     = gc.deck
                if (!gc.summary.isNullOrBlank()) result["synopsis"] = gc.summary
                if (!gc.coverUrl.isNullOrBlank() && result["coverUrl"] == null) result["coverUrl"] = gc.coverUrl
                if (gc.releaseDate != null && result["releaseDate"] == null) {
                    result["releaseDate"] = runCatching {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(gc.releaseDate!!)?.time
                    }.getOrNull()
                }
                // Platforms and genres from game_cache (JSON arrays)
                if (result["platforms"] == null && gc.platforms != null) {
                    runCatching {
                        val list = com.google.gson.Gson().fromJson(gc.platforms, List::class.java)?.map { it.toString() }
                        if (!list.isNullOrEmpty()) result["platforms"] = list
                    }
                }
                if (result["genre"] == null && gc.genres != null) {
                    runCatching {
                        val list = com.google.gson.Gson().fromJson(gc.genres, List::class.java)?.map { it.toString() }
                        result["genre"] = list?.joinToString(", ")
                    }
                }
            }
        }

        // 3. Publisher isn't persisted in game_cache — if still missing, resolve it directly by name
        if (result["publisher"] == null && ApiServices.igdbAvailable) {
            withContext(Dispatchers.IO) {
                runCatching { ApiServices.igdb.searchByName(igdbResult?.title ?: item.title) }.getOrNull()
            }?.let { byName ->
                result["publisher"] = byName.publisher
                if (result["genre"] == null) result["genre"] = byName.genre
            }
        }

        (result["genre"] as? String)?.let { result["genre"] = translateGameGenres(it) }

        // A sinopse da IGDB costuma trazer frases de anúncio ("launching in spring 2026")
        // que nunca são atualizadas pela própria API após o lançamento. Se já sabemos que a
        // data de lançamento já passou, removemos essas frases para não exibir texto obsoleto.
        val releaseMs = (result["releaseDate"] as? Long) ?: (result["releaseDate"] as? Double)?.toLong()
        if (releaseMs != null && releaseMs <= System.currentTimeMillis()) {
            (result["synopsis"] as? String)?.let { result["synopsis"] = stripStaleReleaseMentions(it) }
        }

        if (result.isEmpty()) return null

        // 3b. DLCs, expansões e recomendações (jogos similares)
        if (ApiServices.igdbAvailable && externalIdInt != null) {
            withContext(Dispatchers.IO) {
                runCatching { ApiServices.igdb.getRelatedGames(externalIdInt) }.getOrNull()
            }?.let { related ->
                fun toMaps(list: List<com.hobbiesvault.service.IgdbRelatedGame>) =
                    list.map { mapOf("igdbId" to it.igdbId, "title" to it.title, "coverUrl" to it.coverUrl) }
                if (related.dlcs.isNotEmpty())            result["dlcs"] = toMaps(related.dlcs)
                if (related.expansions.isNotEmpty())      result["expansions"] = toMaps(related.expansions)
                if (related.recommendations.isNotEmpty()) result["recommendations"] = toMaps(related.recommendations)
            }
        }

        // 4. Steam achievements
        if (ApiServices.steamAvailable && item.externalId != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val achievements = ApiServices.steam.getAchievements(item.externalId!!)
                    result["achievements"] = achievements.map {
                        mapOf("name" to it.name, "description" to it.description,
                            "achieved" to it.achieved, "icon" to it.iconUrl)
                    }
                    result["totalAchievements"]    = achievements.size
                    result["achievementsUnlocked"] = achievements.count { it.achieved }
                }
            }
        }

        Log.d("MediaCacheService", "fetchGame '${item.title}' synopsis=${(result["synopsis"] as? String)?.take(50)} platforms=${result["platforms"]} deck=${(result["deck"] as? String)?.take(40)}")
        return result
    }

    private suspend fun fetchManga(item: MediaItem): Map<String, Any?>? {
        val externalId = item.externalId ?: return null
        return if (item.apiSource == "mangadex") fetchMangaFromMangaDex(externalId)
        else fetchMangaFromAniList(item, externalId)
    }

    private suspend fun fetchMangaFromMangaDex(externalId: String): Map<String, Any?>? {
        val raw = withContext(Dispatchers.IO) {
            runCatching { ApiServices.mangadex.getDetailsById(externalId) }.getOrNull()
        } ?: return null
        val latestChapter = withContext(Dispatchers.IO) {
            runCatching { ApiServices.mangadex.getLatestChapterNumber(externalId) }.getOrNull()
        }
        return raw.toMutableMap().apply {
            if (latestChapter != null) put("chapters", latestChapter.toInt())
        }
    }

    private suspend fun fetchMangaFromAniList(item: MediaItem, externalId: String): Map<String, Any?>? {
        val id = externalId.toIntOrNull() ?: externalId.toDoubleOrNull()?.toInt() ?: return null
        val raw = withContext(Dispatchers.IO) {
            runCatching { ApiServices.anilist.getDetailsById(id) }.getOrNull()
        } ?: return null

        val statusPt = when (raw["status"] as? String) {
            "RELEASING"        -> "Em andamento"
            "FINISHED"         -> "Finalizado"
            "NOT_YET_RELEASED" -> "Em breve"
            "CANCELLED"        -> "Cancelado"
            "HIATUS"           -> "Em hiato"
            else               -> raw["status"] as? String
        }

        val formatPt = when (raw["format"] as? String) {
            "MANGA"    -> "Mangá"
            "MANHWA"   -> "Manhwa"
            "MANHUA"   -> "Manhua"
            "ONE_SHOT" -> "One-shot"
            "NOVEL"    -> "Novel"
            "OEL"      -> "OEL"
            else       -> raw["format"] as? String
        }

        val genreMap = mapOf(
            "Action"       to "Ação",       "Adventure"   to "Aventura",
            "Comedy"       to "Comédia",    "Drama"       to "Drama",
            "Fantasy"      to "Fantasia",   "Horror"      to "Terror",
            "Mystery"      to "Mistério",   "Psychological" to "Psicológico",
            "Romance"      to "Romance",    "Sci-Fi"      to "Ficção Científica",
            "Slice of Life" to "Slice of Life", "Sports"  to "Esportes",
            "Supernatural" to "Sobrenatural", "Thriller"  to "Thriller",
            "Ecchi"        to "Ecchi",      "Mecha"       to "Mecha",
            "Music"        to "Música",     "Mahou Shoujo" to "Mahou Shoujo",
            "Hentai"       to "Hentai",
        )
        val genresPt = (raw["genres"] as? List<*>)
            ?.filterIsInstance<String>()
            ?.map { genreMap[it] ?: it }

        val result = raw.toMutableMap().apply {
            remove("status")
            remove("format")
            put("serializationStatus", statusPt)
            put("format",   formatPt)
            put("genres",   genresPt)
        }

        // AniList só informa 'chapters' quando a série já terminou — para séries em
        // andamento, buscamos o capítulo mais recente conhecido no MangaDex (via título)
        // pra não deixar o total de capítulos vazio.
        if (result["chapters"] == null) {
            val cachedMangaDexId = item.id?.let { runCatching { DB.cache.load(it) }.getOrNull()?.get("mangaDexId") as? String }
            val mangaDexId = cachedMangaDexId ?: withContext(Dispatchers.IO) {
                val searchTitle = (result["title"] as? String) ?: item.title
                runCatching { ApiServices.mangadex.search(searchTitle).firstOrNull()?.externalId }.getOrNull()
            }
            if (mangaDexId != null) {
                result["mangaDexId"] = mangaDexId
                val latestChapter = withContext(Dispatchers.IO) {
                    runCatching { ApiServices.mangadex.getLatestChapterNumber(mangaDexId) }.getOrNull()
                }
                if (latestChapter != null) result["chapters"] = latestChapter.toInt()
            }
        }

        return result
    }

    private suspend fun fetchBook(item: MediaItem): Map<String, Any?>? {
        val externalId = item.externalId ?: return null

        if (item.apiSource == "open_library") {
            return withContext(Dispatchers.IO) {
                runCatching { ApiServices.openLibrary.getDetails(externalId) }.getOrNull()
            }
        }

        val raw = withContext(Dispatchers.IO) {
            runCatching { ApiServices.googleBooks.getDetails(externalId) }.getOrNull()
        } ?: return null

        // Extract fields from volumeInfo so the detail screen can read them directly
        val info = raw["volumeInfo"] as? Map<*, *> ?: return raw
        val imgLinks = info["imageLinks"] as? Map<*, *>
        val coverUrl = (imgLinks?.get("thumbnail") as? String
            ?: imgLinks?.get("smallThumbnail") as? String)
            ?.replace("http://", "https://")
            ?.replace("&zoom=1", "&zoom=2")
        val categories = (info["categories"] as? List<*>)?.filterIsInstance<String>()
        val genre = categories?.firstOrNull()?.let { translateBookGenre(it) }

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val fmt2 = java.text.SimpleDateFormat("yyyy", java.util.Locale.US)
        val rawDate = info["publishedDate"] as? String
        val releaseDate = rawDate?.let {
            runCatching { fmt.parse(it) }.getOrNull() ?: runCatching { fmt2.parse(it) }.getOrNull()
        }

        return buildMap {
            put("volumeInfo",   info)   // keep for backward compat
            put("title",        info["title"])
            put("synopsis",     info["description"])
            put("coverUrl",     coverUrl)
            put("author",       (info["authors"] as? List<*>)?.filterIsInstance<String>()?.firstOrNull())
            put("publisher",    info["publisher"])
            put("genre",        genre)
            put("pages",        (info["pageCount"] as? Double)?.toInt())
            put("releaseDate",  releaseDate?.time)
        }
    }

    private fun translateBookGenre(genre: String): String {
        val map = mapOf(
            "Fiction" to "Ficção", "Nonfiction" to "Não-ficção", "Non-fiction" to "Não-ficção",
            "Science Fiction" to "Ficção Científica", "Fantasy" to "Fantasia",
            "Mystery" to "Mistério", "Thriller" to "Thriller", "Horror" to "Terror",
            "Romance" to "Romance", "Historical Fiction" to "Ficção Histórica",
            "Biography" to "Biografia", "Autobiography" to "Autobiografia",
            "Self-help" to "Autoajuda", "Psychology" to "Psicologia",
            "Philosophy" to "Filosofia", "History" to "História",
            "Science" to "Ciência", "Technology" to "Tecnologia",
            "Business" to "Negócios", "Economics" to "Economia",
            "Politics" to "Política", "Religion" to "Religião",
            "Poetry" to "Poesia", "Drama" to "Drama",
            "Comics" to "Quadrinhos", "Graphic Novels" to "Novel Gráfica",
            "Children" to "Infantil", "Young Adult" to "Jovem Adulto",
            "Adventure" to "Aventura", "Action" to "Ação",
            "Crime" to "Crime", "Suspense" to "Suspense",
            "Cooking" to "Culinária", "Travel" to "Viagem",
            "Art" to "Arte", "Music" to "Música",
            "Sports" to "Esportes", "Health" to "Saúde",
        )
        if (genre.contains("/")) {
            return genre.split("/").joinToString(" / ") { translateBookGenre(it.trim()) }
        }
        return map.entries.firstOrNull { genre.contains(it.key, ignoreCase = true) }?.value ?: genre
    }

    private val staleReleaseMentionPattern = Regex(
        """(launching\s+on|launching\s+in|coming\s+to|comes\s+to|available\s+on|will\s+be\s+released|""" +
            """(spring|summer|fall|winter|autumn)\s+\d{4}|launches?\s+in|releases?\s+(on|in)|release\s+date)""",
        RegexOption.IGNORE_CASE,
    )

    private fun stripStaleReleaseMentions(text: String): String {
        val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
        val cleaned = sentences.filterNot { staleReleaseMentionPattern.containsMatchIn(it) }
        return cleaned.joinToString(" ").trim().ifEmpty { text }
    }

    private fun translateGameGenres(genres: String): String {
        val map = mapOf(
            "Platform" to "Plataforma", "Fighting" to "Luta",
            "Shooter" to "Tiro", "Music" to "Música",
            "Puzzle" to "Puzzle", "Racing" to "Corrida",
            "Real Time Strategy (RTS)" to "Estratégia em Tempo Real",
            "General" to "Geral", "Adventure" to "Aventura",
            "Indie" to "Indie", "Arcade" to "Arcade",
            "Visual Novel" to "Visual Novel", "Card & Board Game" to "Cartas e Tabuleiro",
            "MOBA" to "MOBA", "Point-and-click" to "Point-and-click",
            "Simulator" to "Simulação", "Sport" to "Esporte",
            "Strategy" to "Estratégia", "Turn-based strategy (TBS)" to "Estratégia por Turnos",
            "Tactical" to "Tático", "Quiz/Trivia" to "Quiz",
            "Hack and slash/Beat 'em up" to "Hack and Slash",
            "Pinball" to "Pinball", "Role-playing (RPG)" to "RPG",
        )
        return genres.split(", ").joinToString(", ") { map[it.trim()] ?: it.trim() }
    }

    // ── Daily update of all items ─────────────────────────────────────────────

    suspend fun updateAll() = withContext(Dispatchers.IO) {
        DB.repo.getAll()
            .filter { it.externalId != null }
            .forEach { item -> runCatching { fetchAndStore(item) } }
    }

    // ── Series status check ───────────────────────────────────────────────────

    private suspend fun checkSeriesStatus(item: MediaItem, data: Map<String, Any?>) {
        val tmdbStatus = data["tmdbStatus"] as? String ?: return
        val newStatus = determineSeriesStatusTransition(item.status, tmdbStatus) ?: return
        DB.repo.update(item.copy(status = newStatus))
    }

    /**
     * Decide se uma série deve mudar de status dado o status atual (TMDB "Ended"/"Canceled"/
     * "Cancelled" → Histórico; "Returning Series" a partir de Histórico → Aguardando Lançamento).
     * Retorna null quando não há transição (mantém o status atual). Função pura — sem I/O —
     * para ser testável sem depender do banco.
     */
    internal fun determineSeriesStatusTransition(currentStatus: MediaStatus, tmdbStatus: String): MediaStatus? = when {
        tmdbStatus in listOf("Ended", "Canceled", "Cancelled") &&
                currentStatus !in listOf(MediaStatus.HISTORY, MediaStatus.QUEUED, MediaStatus.DROPPED) ->
            MediaStatus.HISTORY
        tmdbStatus == "Returning Series" && currentStatus == MediaStatus.HISTORY ->
            MediaStatus.WAITING_RELEASE
        else -> null
    }

    /** Compara o cache local com os dados recém-buscados. Função pura, extraída para teste. */
    internal fun hasCacheChanged(current: Map<String, Any?>?, newData: Map<String, Any?>): Boolean =
        current != newData

    /**
     * Decide o novo progresso de leitura a partir do progresso remoto do AniList, nunca
     * regredindo uma edição manual mais recente. Retorna null quando nenhuma atualização é
     * necessária (progresso remoto <= local).
     */
    internal fun computeMergedProgress(localProgress: Int?, remoteProgress: Int): Int? =
        if (remoteProgress > (localProgress ?: 0)) remoteProgress else null
}
