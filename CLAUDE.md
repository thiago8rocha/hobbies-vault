# HobbiesVault — CLAUDE.md

App Android nativo (Kotlin + Jetpack Compose) para rastreamento pessoal de jogos, mangás, webtoons, séries, filmes e livros. Sem servidor próprio, sem cadastro. Todos os dados ficam localmente no dispositivo.

> O projeto foi migrado de Flutter para Kotlin/Compose nativo. A versão antiga em Flutter está arquivada em `../Hobbies-Backup` (fora deste repositório) e serve apenas como referência histórica — não editar nem tratar como fonte de verdade.

---

## Stack e dependências principais

| Componente | Tecnologia |
|---|---|
| Linguagem | Kotlin |
| UI | Jetpack Compose |
| Banco local | Room (`androidx.room`) |
| Build | Gradle Kotlin DSL (`build.gradle.kts`), plugins via `libs.versions.toml`, KSP para codegen |
| Navegação | Compose Navigation (`MainNavGraph.kt`, `Routes.kt`) |
| Serialização | Gson |
| Background tasks | WorkManager (`CacheUpdateWorker.kt`) |
| HTTP | (verificar client usado nos `*Service.kt`, ex. OkHttp/Retrofit) |

---

## Estrutura de pastas

```
app/src/main/kotlin/com/hobbiesvault/
  MainActivity.kt
  HobbiesVaultApp.kt

  data/
    db/
      AppDatabase.kt              # @Database Room, migrations, schemaVersion
      DB.kt                       # Acesso singleton/DI ao banco
      dao/
        MediaItemDao.kt
        MediaDetailsCacheDao.kt
        FilmeListaDao.kt
        GameCacheDao.kt
        SerieEpisodioDao.kt
      entity/
        MediaItemEntity.kt
        MediaDetailsCacheEntity.kt
        FilmeListaEntity.kt
        GameCacheEntity.kt
        SerieEpisodioEntity.kt
    repository/
      MediaRepository.kt          # CRUD de MediaItem
      MediaCacheRepository.kt     # Leitura/escrita de cache JSON no banco

  model/
    MediaItem.kt
    MediaType.kt
    MediaStatus.kt
    GameConsole.kt
    ApiSearchResult.kt

  service/
    ApiServices.kt          # Registro central de todos os serviços de API
    Secrets.kt              # Carrega secrets.json de app/src/main/assets em runtime
    TmdbService.kt          # TMDB — filmes e séries
    IgdbService.kt          # IGDB — jogos
    HltbService.kt          # HowLongToBeat — tempo de jogo
    ItadService.kt          # IsThereAnyDeal — preços de jogos
    GameSearchService.kt
    GameCacheService.kt
    GameDatasetImporter.kt
    AniListService.kt       # AniList (GraphQL) — fonte principal de mangás/webtoons + sync de progresso
    MangaDexService.kt      # MangaDex — fallback de busca/detalhes e contagem de capítulos em andamento
    MangaSearchService.kt   # Orquestra AniList + MangaDex
    GoogleBooksService.kt   # Google Books — livros
    OpenLibraryService.kt   # Open Library — livros (fallback)
    BookSearchService.kt    # Orquestra Google Books + Open Library
    SteamService.kt         # Steam Web API — biblioteca e achievements
    PsnService.kt           # PSN — troféus (público, sem auth)
    MediaCacheService.kt    # Orquestra cache: fetch + comparação + persistência

  worker/
    CacheUpdateWorker.kt    # Tarefa diária de atualização de cache (WorkManager)

  ui/
    theme/
      AppTheme.kt
      Color.kt              # Cores por tipo de mídia, plataformas e temas do app
    navigation/
      Routes.kt
      MainNavGraph.kt
    components/
      SharedComponents.kt
    screens/
      HomeScreen.kt
      SearchScreen.kt
      HistoryScreen.kt
      StatsScreen.kt
      CalendarScreen.kt
      SettingsScreen.kt

      games/
        GamesScreen.kt
        AddGameScreen.kt
        GameDetailScreen.kt
      films/
        FilmsScreen.kt
        AddFilmScreen.kt
        FilmDetailScreen.kt
      series/
        SeriesScreen.kt
        AddSeriesScreen.kt
        SeriesDetailScreen.kt
      manga/
        MangaScreen.kt
        AddMangaScreen.kt
        MangaDetailScreen.kt
      books/
        BooksScreen.kt
        AddBookScreen.kt
        BookDetailScreen.kt

assets/
  data/                      # datasets estáticos (ex. importação de jogos)
  trofeus/                   # ícones/dados de troféus PSN
```

---

## Banco de dados (Room)

**Schema atual: v10** (ver `AppDatabase.kt` para o histórico completo de migrations)

Entidades principais:

### `MediaItemEntity` (tabela `media_items`)
Campos principais: `id`, `tipo`, `titulo`, `status`, `nota`, `comentario`, `capaUrl`, `dataAdicaoMs`, `dataConclusaoMs`, `favorito`, `idExterno`, `fonteApi`.

Campos específicos por tipo:
- **Jogo:** `console`, `horasJogadasMinutos`, `conquistasDesbloqueadas`, `conquistasTotal`, `trofeusOuro`, `trofeusPrata`, `trofeusBronze`, `trofeuPlatina`, `desenvolvedor`
- **Mangá/Livro/Série:** `progressoAtual`, `progressoTotal`
- **Filme/Série:** `streamingPlataforma`
- **Todos:** `dataLancamentoMs`, `genero`
- Campos adicionados nas migrations mais recentes (v7-v10): `dataInicioLeituraMs`, `dataReleituraMs`, `dataConclusaoHistoriaMs`, `dataConclusaoExtrasMs`, `dataConclusaoPlatinaMs`

### `MediaDetailsCacheEntity` (tabela `media_details_cache`)
Cache de detalhes completos da API por item. Campos: `mediaItemId`, `dadosJson` (JSON completo), `ultimaVerificacaoMs`.

### `FilmeListaEntity` / tabela de itens de lista (`filme_listas`, `filme_lista_itens`)
Listas customizadas de filmes.

### `GameCacheEntity` (tabela `game_cache`)
Cache de metadados de jogos (nome, ids GB/IGDB, capa, gêneros, plataformas etc.).

### `SerieEpisodioEntity` (tabela `serie_episodios_assistidos`)
Controle de episódios assistidos por série (temporada, episódio, data).

### Acesso via DB singleton

```kotlin
import com.hobbiesvault.data.db.DB

// Leitura/escrita de itens — via MediaRepository / DAOs
mediaRepository.salvar(item)
mediaRepository.atualizar(item)
mediaRepository.deletar(id)
mediaRepository.porTipo(MediaType.MOVIE)

// Cache de detalhes — via MediaCacheRepository
mediaCacheRepository.carregar(mediaItemId)
mediaCacheRepository.salvar(mediaItemId, dadosJson)
mediaCacheRepository.deletar(mediaItemId)
```

### Após qualquer alteração em entidades/DAOs

Incrementar a `version` em `@Database` (`AppDatabase.kt`) e adicionar uma nova `Migration` no companion object seguindo o padrão existente (`MIGRATION_N_N+1`), registrando-a na criação do `Room.databaseBuilder`. Não há build_runner/codegen manual — o Room usa KSP automaticamente no build do Gradle.

---

## Modelos principais

### MediaType
```kotlin
enum class MediaType(val label: String, val dbValue: String) {
    GAME, MANGA, WEBTOON, SERIES, MOVIE, BOOK
}
```
`dbValue` mantém os nomes originais em português (`jogo`, `manga`, `webtoon`, `serie`, `filme`, `livro`) para compatibilidade com o schema migrado do Flutter/Drift.

### MediaStatus
```kotlin
enum class MediaStatus(val label: String, val dbValue: String) {
    // Jogos
    COMPLETED, FINISHED, PLAYING, REPLAYING, PLATINUM,
    // Filmes
    WATCHED, WATCHING, REWATCHING,
    // Séries
    CONCLUDED, HISTORY, WAITING_EPISODES,
    // Mangás/Livros
    READ, READING, REREADING, ON_HOLD,
    // Todos
    QUEUED, DROPPED, WAITING_RELEASE
}
```

Métodos de lista por tipo/plataforma (companion object de `MediaStatus`):
- `forSteam()`, `forPlayStation()`, `forNintendo()`, `forOtherGames()` — variações por plataforma de jogo
- `forMovie()` → [WATCHED, REWATCHING, QUEUED, WAITING_RELEASE]
- `forSeries()` / `forSeriesAdd()` → [WATCHING, REWATCHING, QUEUED, HISTORY]
- `forManga()` → [READING, REREADING, ON_HOLD, READ, QUEUED, WAITING_RELEASE]
- `forMangaAdd()` → [READING, REREADING, QUEUED]
- `forBook()` → [READING, REREADING, READ, QUEUED, DROPPED]
- `forBookAdd()` → [READING, REREADING, QUEUED]

### MediaItem
Data class Kotlin — usar `.copy()` para atualizações imutáveis (equivalente ao `copyWith()` do Flutter). Campos `idExterno` e `fonteApi` identificam a origem na API.

---

## Cache e atualização de dados

### Padrão obrigatório em todas as telas de detalhe

Carregar o cache do banco imediatamente (síncrono/local) e disparar verificação em background via `MediaCacheService`, sem bloquear a UI — equivalente ao padrão `_carregarCache()` + `doubleCheck()` da versão Flutter.

### Ao adicionar um item à biblioteca
Chamar `MediaCacheService` para popular o cache imediatamente após salvar o item no banco (`MediaRepository`), usando o id gerado pelo Room.

### Rotina diária (WorkManager)
`CacheUpdateWorker` roda periodicamente e chama a atualização de todos os itens via `MediaCacheService`:
1. Percorre todos os itens com `idExterno`
2. Faz fetch da API correspondente por tipo
3. Compara com o cache atual (hash/comparação de JSON)
4. Só persiste se houve mudança
5. Verifica mudanças de status em séries (Ended/Cancelled → move para Histórico)

**Ao alterar qualquer tela de detalhe:** verificar se o JSON gerado em `MediaCacheService` reflete os novos campos exibidos.

### Sincronização de progresso de mangá via AniList
Se `anilist_username` estiver configurado em `secrets.json`, toda atualização de cache de um mangá/webtoon adicionado via AniList (`apiSource == "anilist"`) consulta a lista pública do usuário no AniList (`AniListService.getUserProgress`) e só avança `currentProgress` quando o valor de lá for maior que o local — nunca regride uma edição manual mais recente. Não requer OAuth (mesma lógica de credencial estática já usada por Steam/PSN); só funciona se o perfil AniList do usuário não for privado.

---

## APIs por tipo de mídia

| Tipo | Serviço principal | Fallback |
|---|---|---|
| Filmes | TMDB (`TmdbService`) | — |
| Séries | TMDB (`TmdbService`) | — |
| Jogos | IGDB (`IgdbService`, requer token Twitch); HLTB (`HltbService`) e ITAD (`ItadService`) como dados complementares | Busca por prefixo / dataset local (`GameDatasetImporter`) |
| Mangás/Webtoons | AniList (`AniListService`, GraphQL) | MangaDex (`MangaDexService`) — só é acionado quando AniList não retorna resultados (ver `MangaSearchService.kt`); também fornece a contagem de capítulos mais recente para séries em andamento via endpoint `/aggregate` |
| Livros | Google Books (`GoogleBooksService`) | Open Library (`OpenLibraryService`) |

### Disponibilidade verificada antes de usar
```kotlin
if (!secrets.tmdbConfigurado) { /* mostrar erro */ }
if (!secrets.igdbConfigurado) { /* mostrar erro */ }
if (!secrets.steamConfigurado) { /* mostrar erro */ }
if (!secrets.itadConfigurado) { /* mostrar erro */ }
```
Flags de disponibilidade ficam em `Secrets` (`app/src/main/kotlin/com/hobbiesvault/service/Secrets.kt`), carregado uma vez (singleton) a partir de `secrets.json`.

### secrets.json (`app/src/main/assets/secrets.json`, nunca commitar)
```json
{
  "tmdb_bearer_token": "eyJhbGci...",
  "igdb_client_id": "abc123",
  "igdb_client_secret": "xyz789",
  "google_books_api_key": "AIza...",
  "anilist_client_id": "12345",
  "anilist_username": "seu_usuario_anilist",
  "steam_api_key": "ABCD1234",
  "steam_id": "76561198XXXXXXXXX",
  "itad_api_key": "..."
}
```

---

## Rotas (Compose Navigation)

Definidas em `Routes.kt` e ligadas em `MainNavGraph.kt`.

| Rota | Tela |
|---|---|
| `home` | HomeScreen |
| `games` | GamesScreen |
| `games/add` | AddGameScreen |
| `games/detail` | GameDetailScreen |
| `films` | FilmsScreen |
| `films/add` | AddFilmScreen |
| `films/detail` | FilmDetailScreen |
| `series` | SeriesScreen |
| `series/add` | AddSeriesScreen |
| `series/detail` | SeriesDetailScreen |
| `manga` | MangaScreen |
| `manga/add` | AddMangaScreen |
| `manga/detail` | MangaDetailScreen |
| `books` | BooksScreen |
| `books/add` | AddBookScreen |
| `books/detail` | BookDetailScreen |
| `search` | SearchScreen |
| `settings` | SettingsScreen |
| `history` | HistoryScreen |
| `stats` | StatsScreen |
| `calendar` | CalendarScreen |

Passagem de objeto entre telas: seguir o padrão do NavGraph existente (savedStateHandle / argumentos de rota), verificar `MainNavGraph.kt` para o mecanismo em uso antes de adicionar novas rotas com parâmetros complexos.

---

## Cores por tipo de mídia (Color.kt)

```kotlin
ColorJogo        // #7B1FA2 (roxo)
ColorManga       // #E91E63 (rosa)
ColorWebtoon     // #00BCD4 (ciano)
ColorSerie       // #1976D2 (azul)
ColorFilme       // #FF6F00 (laranja)
ColorLivro       // #388E3C (verde)
ColorSteam       // #1B2838
ColorPlayStation // #00439C
ColorNintendo    // #E4000F
ColorXbox        // #107C10
```

Além disso, `Color.kt` define `appThemes`: uma lista de `AppThemeDefinition` (temas nomeados como Neko, Tako, Yin, Doki, Oceano, Meia-noite) com seeds e cores de fundo/superfície para dark/light.

---

## Convenções de código

### Listas e grids
Seguir o padrão visual da versão Flutter: `LazyVerticalGrid` com 3 colunas e proporção de capa portrait (~0.56), a menos que a implementação Compose atual já divirja — conferir as `*Screen.kt` de lista antes de assumir.

### Cards do grid
Capa portrait + título abaixo. Sem status, nota ou qualquer outra informação no card. Status e detalhes ficam na tela de detalhe.

### Persistência nas telas de detalhe
Sempre persistir via `MediaRepository`/`MediaCacheRepository` (nunca só estado local em memória). Ao remover um item, deletar também o cache correspondente (`MediaCacheRepository.deletar(id)`).

### Detecção de duplicatas na busca
A busca deve identificar itens cujo `idExterno` já exista na biblioteca (mesmo padrão do `existingIds` da versão Flutter) e sinalizar duplicatas na UI de resultados.

---

## Lógica específica de séries

Séries têm movimentação automática de status baseada no TMDB:

- **Status TMDB `Ended`/`Cancelled`** → move para `MediaStatus.HISTORY`
- **Status TMDB `Returning Series`** sem próxima temporada confirmada → move para `WAITING_RELEASE`

Verificações acontecem em:
1. Inicialização da `SeriesScreen` — valida séries em `WAITING_RELEASE`
2. Ao marcar o último episódio assistido na tela de detalhe
3. Rotina diária do `CacheUpdateWorker` — verifica todas as séries

---

## Comandos úteis

```bash
# Build de debug
./gradlew assembleDebug

# Instalar no dispositivo/emulador
./gradlew installDebug

# Build de release
./gradlew assembleRelease

# Rodar testes
./gradlew test
```

---

## O que NÃO fazer

- Não commitar `secrets.json` (`app/src/main/assets/secrets.json`)
- Não fazer fetch de API diretamente nas telas de detalhe (Composables) — usar `MediaCacheService`
- Não alterar os serializers em `MediaCacheService` sem refletir os novos campos nas telas
- Não esquecer de chamar `mediaCacheRepository.deletar(id)` ao remover um item da biblioteca
- Não tratar o projeto Flutter em `../Hobbies-Backup` como código ativo — é apenas referência histórica da versão anterior
