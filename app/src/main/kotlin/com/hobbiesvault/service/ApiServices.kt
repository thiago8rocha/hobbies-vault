package com.hobbiesvault.service

import android.content.Context
import com.hobbiesvault.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApiServices {
    private var _tmdb: TmdbService?               = null
    private var _igdb: IgdbService?               = null
    private var _anilist: AniListService?         = null
    private var _mangadex: MangaDexService?       = null
    private var _googleBooks: GoogleBooksService? = null
    private var _openLibrary: OpenLibraryService? = null
    private var _mangaSearch: MangaSearchService? = null
    private var _bookSearch: BookSearchService?   = null
    private var _steam: SteamService?             = null
    private var _psn: PsnService?                 = null
    private var _hltb: HltbService?               = null
    private var _itad: ItadService?               = null
    private var _igdbToken: IgdbToken?            = null
    private var _gameSearch: GameSearchService?   = null
    private var _gameCache: GameCacheService?     = null
    private var _aniListUsername: String?         = null
    private var _initialized = false

    // ── Initialization ────────────────────────────────────────────────────────

    suspend fun init(context: Context) {
        if (_initialized) return
        val secrets = Secrets.load(context)

        withContext(Dispatchers.IO) {
            // TMDB
            runCatching {
                if (secrets.tmdbConfigurado) {
                    _tmdb = TmdbService(secrets.tmdbBearerToken!!)
                }
            }

            // IGDB
            if (secrets.igdbConfigurado) {
                runCatching {
                    _igdbToken = IgdbAuthService.loadCachedToken(context, secrets.igdbClientId)
                        ?: IgdbAuthService.getAccessToken(context, secrets.igdbClientId!!, secrets.igdbClientSecret!!)
                    _igdb = IgdbService(clientId = secrets.igdbClientId!!, accessToken = _igdbToken!!.accessToken)
                }
            }

            // Manga
            runCatching {
                _anilist     = AniListService()
                _mangadex    = MangaDexService()
                _mangaSearch = MangaSearchService(_anilist!!, _mangadex!!)
            }
            _aniListUsername = secrets.anilistUsername

            // Books
            runCatching {
                _openLibrary = OpenLibraryService()
                _googleBooks = GoogleBooksService(apiKey = secrets.googleBooksApiKey)
                _bookSearch  = BookSearchService(_googleBooks!!, _openLibrary!!)
            }

            // Steam
            if (secrets.steamConfigurado) {
                runCatching {
                    _steam = SteamService(apiKey = secrets.steamApiKey!!, steamId = secrets.steamId!!)
                }
            }

            // HLTB
            runCatching { _hltb = HltbService() }

            // GameCacheService + GameSearch
            runCatching {
                _gameCache = GameCacheService(
                    dao  = com.hobbiesvault.data.db.DB.games,
                    igdb = _igdb,
                )
                _gameSearch = GameSearchService(
                    igdb         = _igdb,
                    gameCache    = com.hobbiesvault.data.db.DB.games,
                    gameCacheSvc = _gameCache!!,
                )
            }

            // ITAD
            if (secrets.itadConfigurado) {
                runCatching { _itad = ItadService(apiKey = secrets.itadApiKey!!) }
            }
        }

        _initialized = true
    }

    // ── Runtime OAuth2 token updates ──────────────────────────────────────────

    fun setAniListToken(accessToken: String) {
        _anilist     = AniListService(accessToken = accessToken)
        _mangaSearch = MangaSearchService(_anilist!!, _mangadex!!)
    }

    fun setPsnToken(accessToken: String) { _psn = PsnService(accessToken) }
    fun clearPsnToken() { _psn = null }

    suspend fun renewIgdbIfNeeded(context: Context) {
        if (_igdbToken == null || !_igdbToken!!.isExpired) return
        val secrets = Secrets.load(context)
        if (!secrets.igdbConfigurado) return
        runCatching {
            _igdbToken = withContext(Dispatchers.IO) {
                IgdbAuthService.getAccessToken(context, secrets.igdbClientId!!, secrets.igdbClientSecret!!)
            }
            _igdb = IgdbService(clientId = secrets.igdbClientId!!, accessToken = _igdbToken!!.accessToken)
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    val tmdb         get() = _tmdb ?: error("TMDB not configured")
    val igdb         get() = _igdb ?: error("IGDB not configured")
    val anilist      get() = _anilist!!
    val mangadex     get() = _mangadex!!
    val googleBooks  get() = _googleBooks!!
    val openLibrary  get() = _openLibrary!!
    val mangaSearch  get() = _mangaSearch!!
    val bookSearch   get() = _bookSearch!!
    val gameSearch   get() = _gameSearch!!
    val gameCache    get() = _gameCache
    val steam        get() = _steam ?: error("Steam not configured")
    val psn          get() = _psn
    val hltb         get() = _hltb!!
    val itad         get() = _itad
    val aniListUsername get() = _aniListUsername

    val tmdbAvailable       get() = _tmdb != null
    val igdbAvailable       get() = _igdb != null
    val steamAvailable      get() = _steam != null
    val psnAvailable        get() = _psn != null
    val hltbAvailable       get() = _hltb != null
    val itadAvailable       get() = _itad != null
    val gameSearchAvailable get() = _gameSearch != null

    // ── Config validation ─────────────────────────────────────────────────────

    fun serviceUnavailableReason(type: MediaType): String? = when (type) {
        MediaType.MOVIE, MediaType.SERIES ->
            if (!tmdbAvailable) "TMDB não configurado — adicione tmdb_bearer_token ao secrets.json" else null
        MediaType.GAME ->
            if (!igdbAvailable) "IGDB não configurado — adicionando igdb_client_id e igdb_client_secret ao secrets.json habilita busca online (cache local ainda funciona)" else null
        MediaType.MANGA, MediaType.WEBTOON, MediaType.BOOK -> null
    }
}
