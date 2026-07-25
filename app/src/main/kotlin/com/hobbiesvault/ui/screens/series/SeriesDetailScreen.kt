package com.hobbiesvault.ui.screens.series

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.data.db.entity.SeriesEpisodeEntity
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.service.ApiServices
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.components.NotesDialog
import com.hobbiesvault.ui.theme.ColorSerie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// TMDB TV genre names → Portuguese
private val genreTranslations = mapOf(
    "Action & Adventure" to "Ação & Aventura",
    "Action"             to "Ação",
    "Adventure"          to "Aventura",
    "Animation"          to "Animação",
    "Comedy"             to "Comédia",
    "Crime"              to "Crime",
    "Documentary"        to "Documentário",
    "Drama"              to "Drama",
    "Family"             to "Família",
    "Fantasy"            to "Fantasia",
    "Horror"             to "Terror",
    "History"            to "História",
    "Kids"               to "Infantil",
    "Music"              to "Música",
    "Mystery"            to "Mistério",
    "News"               to "Notícias",
    "Reality"            to "Reality Show",
    "Romance"            to "Romance",
    "Sci-Fi & Fantasy"   to "Ficção Científica & Fantasia",
    "Science Fiction"    to "Ficção Científica",
    "Soap"               to "Novela",
    "Sport"              to "Esporte",
    "Talk"               to "Talk Show",
    "Thriller"           to "Suspense",
    "War & Politics"     to "Guerra & Política",
    "War"                to "Guerra",
    "Western"            to "Faroeste",
)

private fun translateGenre(genre: String): String = genreTranslations[genre] ?: genre

class SeriesDetailViewModel : ViewModel() {
    var mediaItem by mutableStateOf<MediaItem?>(null)
    var cache by mutableStateOf<Map<String, Any?>?>(null)
    var loadingCache by mutableStateOf(true)
    var watchedEpisodes by mutableStateOf<List<SeriesEpisodeEntity>>(emptyList())
    // episode details keyed by seasonNum → (episodeNum → Pair(name, airDate))
    val episodeDetailsBySeason: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Map<Int, Pair<String, String?>>> =
        mutableStateMapOf()
    private var initialized = false

    fun init(initial: MediaItem) {
        if (initialized) return
        initialized = true
        mediaItem = initial
        viewModelScope.launch {
            val loaded = MediaCacheService.load(initial)
            cache = loaded
            loadingCache = false
            // Restore episode details persisted in the cache JSON
            restoreEpisodeDetailsFromCache(loaded)
        }
        MediaCacheService.doubleCheck(initial) {
            val updated = MediaCacheService.load(initial)
            if (updated != null) {
                cache = updated
                restoreEpisodeDetailsFromCache(updated)
            }
        }
        val id = initial.id ?: return
        viewModelScope.launch {
            DB.repo.watchEpisodesBySeries(id).collectLatest { eps ->
                watchedEpisodes = eps
            }
        }
    }

    private fun restoreEpisodeDetailsFromCache(cacheMap: Map<String, Any?>?) {
        val raw = cacheMap?.get("episodeDetails") as? Map<*, *> ?: return
        raw.forEach { (seasonKey, seasonData) ->
            val seasonNum = (seasonKey as? String)?.toIntOrNull() ?: return@forEach
            if (episodeDetailsBySeason.containsKey(seasonNum)) return@forEach
            val epMap = seasonData as? Map<*, *> ?: return@forEach
            episodeDetailsBySeason[seasonNum] = epMap.entries.mapNotNull { (k, v) ->
                val epNum = (k as? String)?.toIntOrNull() ?: return@mapNotNull null
                val vMap  = v as? Map<*, *>
                epNum to Pair(vMap?.get("name") as? String ?: "", vMap?.get("airDate") as? String)
            }.toMap()
        }
    }

    fun loadSeasonEpisodes(seriesExternalId: Int, seasonNum: Int, dateFmt: java.text.SimpleDateFormat) {
        if (episodeDetailsBySeason.containsKey(seasonNum)) return
        val current = mediaItem ?: return
        viewModelScope.launch {
            runCatching {
                val eps = withContext(Dispatchers.IO) {
                    ApiServices.tmdb.getSeasonEpisodes(seriesExternalId, seasonNum)
                }
                val epDetails = eps.associate {
                    it.number to Pair(it.name, it.airDate?.let { d -> dateFmt.format(d) })
                }
                episodeDetailsBySeason[seasonNum] = epDetails

                // Persist to existing cache JSON so it survives ViewModel recreation
                val currentCache = (MediaCacheService.load(current) ?: emptyMap<String, Any?>()).toMutableMap()
                @Suppress("UNCHECKED_CAST")
                val allEpDetails = (currentCache["episodeDetails"] as? Map<String, Any?>)
                    ?.toMutableMap() ?: mutableMapOf()
                allEpDetails[seasonNum.toString()] = epDetails.mapValues { (_, v) ->
                    mapOf("name" to v.first, "airDate" to v.second)
                }
                currentCache["episodeDetails"] = allEpDetails
                current.id?.let { id -> DB.cache.save(id, currentCache) }
            }
        }
    }

    fun setStatus(newStatus: MediaStatus) {
        val current = mediaItem ?: return
        val updated = current.copy(status = newStatus)
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun toggleFavorite() {
        val current = mediaItem ?: return
        val updated = current.copy(favorite = !current.favorite)
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun setNotes(text: String) {
        val current = mediaItem ?: return
        val updated = current.copy(personalNotes = text.ifBlank { null })
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun refreshCache() {
        val current = mediaItem ?: return
        viewModelScope.launch {
            MediaCacheService.fetchAndPersist(current)
            cache = MediaCacheService.load(current)
        }
    }

    fun markEpisode(season: Int, episode: Int) {
        val current = mediaItem ?: return
        val id = current.id ?: return
        viewModelScope.launch {
            // get episode name from cached details if available
            val epName = episodeDetailsBySeason[season]?.get(episode)?.first
            DB.repo.markEpisode(
                mediaItemId = id,
                season      = season,
                episode     = episode,
                seriesName  = current.title,
                coverUrl    = current.coverUrl,
                episodeName = epName,
            )
        }
    }

    fun unmarkEpisode(season: Int, episode: Int) {
        val current = mediaItem ?: return
        val id = current.id ?: return
        viewModelScope.launch { DB.repo.unmarkEpisode(id, season, episode) }
    }

    fun delete(onDone: () -> Unit) {
        val current = mediaItem ?: return
        viewModelScope.launch {
            current.id?.let {
                DB.repo.delete(it)
                DB.cache.delete(it)
                DB.repo.deleteSeriesEpisodes(it)
            }
            onDone()
        }
    }
}

@Composable
fun SeriesDetailScreen(
    navController: NavController,
    initialItem: MediaItem,
    vm: SeriesDetailViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.init(initialItem) }

    val mediaItem = vm.mediaItem ?: initialItem
    val cache     = vm.cache

    var showDelete       by remember { mutableStateOf(false) }
    var showMoreMenu     by remember { mutableStateOf(false) }
    var showNotes        by remember { mutableStateOf(false) }
    var showStatusMenu   by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }
    var synopsisOverflows by remember { mutableStateOf(false) }
    var showRelated      by remember { mutableStateOf(true) }

    val synopsis      = cache?.get("synopsis") as? String
    val posterUrl     = cache?.get("posterUrl") as? String ?: mediaItem.coverUrl
    val totalEpisodes = (cache?.get("totalEpisodes") as? Double)?.toInt()
    val genres        = (cache?.get("genres") as? List<*>)?.filterIsInstance<String>()
                            ?.map { translateGenre(it) }
    val cast          = (cache?.get("cast") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val crew          = (cache?.get("crew") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val providers     = (cache?.get("providers") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val related       = (cache?.get("related") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val seasons       = (cache?.get("seasons") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
        ?.filter { ((it["number"] as? Double)?.toInt() ?: 0) > 0 }

    val firstYear = (cache?.get("firstAirDate") as? Double)?.toLong()?.let {
        val cal = Calendar.getInstance(); cal.timeInMillis = it; cal.get(Calendar.YEAR)
    } ?: mediaItem.releaseDate?.let {
        val cal = Calendar.getInstance(); cal.timeInMillis = it.time; cal.get(Calendar.YEAR)
    }
    val lastYear = (cache?.get("lastAirDate") as? Double)?.toLong()?.let {
        val cal = Calendar.getInstance(); cal.timeInMillis = it; cal.get(Calendar.YEAR)
    }
    val tmdbStatus = cache?.get("tmdbStatus") as? String
    val periodLabel = when {
        firstYear == null -> null
        lastYear != null && lastYear != firstYear -> "$firstYear – $lastYear"
        tmdbStatus in listOf("Ended", "Canceled", "Cancelled") && lastYear != null -> "$firstYear – $lastYear"
        else -> "$firstYear – presente"
    }

    Scaffold { _ ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {

                // ── Header ───────────────────────────────────────────────────────
                item {
                    Box(Modifier.fillMaxWidth().height(400.dp)) {
                        AsyncImage(
                            model = posterUrl, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(28.dp),
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                        Column(
                            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                model = posterUrl, contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(0.52f).aspectRatio(0.7f).clip(RoundedCornerShape(4.dp)),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                mediaItem.title,
                                color = Color.White, fontSize = 19.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                lineHeight = 25.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // ── Status + more ────────────────────────────────────────────────
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Button(
                                onClick = { showStatusMenu = true },
                                colors  = ButtonDefaults.buttonColors(containerColor = ColorSerie),
                                shape   = RoundedCornerShape(4.dp),
                                border  = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.24f)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(mediaItem.status.label, fontSize = 13.sp)
                            }
                            DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                                MediaStatus.forSeries().forEach { s ->
                                    val selected = s == mediaItem.status
                                    DropdownMenuItem(
                                        text    = { Text(s.label) },
                                        trailingIcon = {
                                            if (selected) Icon(Icons.Default.Check, null, tint = ColorSerie, modifier = Modifier.size(16.dp))
                                        },
                                        onClick = { vm.setStatus(s); showStatusMenu = false },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box {
                            Box(
                                Modifier.size(44.dp)
                                    .border(1.5.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
                                    .clickable { showMoreMenu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.MoreHoriz, null)
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Atualizar") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = { vm.refreshCache(); showMoreMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Notas") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                                    onClick = { showNotes = true; showMoreMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (mediaItem.favorite) "Remover dos favoritos" else "Favoritar") },
                                    leadingIcon = { Icon(if (mediaItem.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                                    onClick = { vm.toggleFavorite(); showMoreMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (showRelated) "Ocultar relacionadas" else "Mostrar relacionadas") },
                                    leadingIcon = { Icon(if (showRelated) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                                    onClick = { showRelated = !showRelated; showMoreMenu = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Remover série", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showDelete = true; showMoreMenu = false },
                                )
                            }
                        }
                    }
                }

                // ── Meta ────────────────────────────────────────────────────────
                if (periodLabel != null || totalEpisodes != null) {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val c = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            if (periodLabel != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = c)
                                    Text(periodLabel, style = MaterialTheme.typography.bodySmall, color = c)
                                }
                            }
                            if (totalEpisodes != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp), tint = c)
                                    Text("$totalEpisodes episódios", style = MaterialTheme.typography.bodySmall, color = c)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── Sinopse ─────────────────────────────────────────────────────
                if (synopsis != null) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            SeriesSectionTitle("Sinopse")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                synopsis,
                                style      = MaterialTheme.typography.bodyMedium,
                                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                lineHeight = 22.sp,
                                maxLines   = if (synopsisExpanded) Int.MAX_VALUE else 4,
                                overflow   = TextOverflow.Ellipsis,
                                onTextLayout = { result -> synopsisOverflows = result.hasVisualOverflow },
                            )
                            if (synopsisOverflows || synopsisExpanded) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (synopsisExpanded) "Ver menos" else "Ver mais",
                                    color      = ColorSerie,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier   = Modifier.clickable { synopsisExpanded = !synopsisExpanded },
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Gêneros ─────────────────────────────────────────────────────
                if (!genres.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            SeriesSectionTitle("Gêneros")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                genres.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                // ── Onde Assistir ────────────────────────────────────────────────
                if (!providers.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SeriesSectionTitle("Onde Assistir")
                            Spacer(Modifier.height(10.dp))
                            if (providers.size > 2) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    providers.chunked(2).forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            row.forEach { p -> SeriesProviderRow(p, modifier = Modifier.weight(1f)) }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    providers.forEach { p -> SeriesProviderRow(p) }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Temporadas ──────────────────────────────────────────────────
                if (!seasons.isNullOrEmpty()) {
                    item {
                        SeriesSectionTitle(
                            "Temporadas (${seasons.size})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    seasons.forEach { season ->
                        item(key = "season_${season["number"]}") {
                            SeasonCard(
                                season           = season,
                                seriesExternalId = mediaItem.externalId?.toIntOrNull(),
                                watchedEpisodes  = vm.watchedEpisodes,
                                episodeDetails   = vm.episodeDetailsBySeason[
                                    (season["number"] as? Double)?.toInt() ?: 0
                                ] ?: emptyMap(),
                                onExpand         = { seasonNum ->
                                    val extId = mediaItem.externalId?.toIntOrNull()
                                    if (extId != null && ApiServices.tmdbAvailable) {
                                        vm.loadSeasonEpisodes(extId, seasonNum,
                                            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")))
                                    }
                                },
                                onMarkEpisode    = { s, e -> vm.markEpisode(s, e) },
                                onUnmarkEpisode  = { s, e -> vm.unmarkEpisode(s, e) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // ── Elenco ──────────────────────────────────────────────────────
                if (!cast.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SeriesSectionTitle("Elenco")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(cast) { p ->
                                    SeriesPersonRow(p["name"] as? String ?: "", p["character"] as? String ?: "", p["photoUrl"] as? String)
                                }
                            }
                        }
                    }
                }

                // ── Equipe Técnica ───────────────────────────────────────────────
                if (!crew.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SeriesSectionTitle("Equipe Técnica")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(crew) { p ->
                                    SeriesPersonRow(p["name"] as? String ?: "", p["role"] as? String ?: "", p["photoUrl"] as? String)
                                }
                            }
                        }
                    }
                }

                // ── Séries relacionadas ──────────────────────────────────────────
                if (showRelated && !related.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SeriesSectionTitle("Séries relacionadas")
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                related.forEach { rel ->
                                    val title     = rel["title"] as? String ?: ""
                                    val relPoster = rel["posterUrl"] as? String
                                    val year      = (rel["year"] as? Double)?.toInt()
                                    Column(Modifier.width(90.dp)) {
                                        AsyncImage(model = relPoster, contentDescription = title, contentScale = ContentScale.Crop,
                                            modifier = Modifier.width(90.dp).height(130.dp).clip(RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.height(5.dp))
                                        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (year != null) {
                                            Text("$year", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            // ── Floating back button ─────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Box(
                        Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.54f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showNotes) {
        NotesDialog(
            initialText = mediaItem.personalNotes ?: "",
            onDismiss   = { showNotes = false },
            onSave      = { vm.setNotes(it) },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title   = { Text("Remover série") },
            text    = { Text("Remover \"${mediaItem.title}\" da sua biblioteca?") },
            confirmButton = {
                Button(
                    onClick = { vm.delete { navController.popBackStack() } },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } },
        )
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────────────

@Composable
private fun SeriesSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
}

@Composable
private fun SeriesProviderRow(p: Map<String, Any?>, modifier: Modifier = Modifier) {
    val name    = p["name"] as? String ?: ""
    val logoUrl = p["logoUrl"] as? String
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (logoUrl != null) {
            AsyncImage(model = logoUrl, contentDescription = name, contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)))
        }
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SeasonCard(
    season: Map<String, Any?>,
    seriesExternalId: Int?,
    watchedEpisodes: List<SeriesEpisodeEntity>,
    episodeDetails: Map<Int, Pair<String, String?>>,
    onExpand: (seasonNum: Int) -> Unit,
    onMarkEpisode: (season: Int, episode: Int) -> Unit,
    onUnmarkEpisode: (season: Int, episode: Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val seasonNum  = (season["number"] as? Double)?.toInt() ?: 0
    val seasonName = season["name"] as? String ?: "Temporada $seasonNum"
    val totalEps   = (season["episodes"] as? Double)?.toInt() ?: 0
    val posterUrl  = season["posterUrl"] as? String
    val airDateMs  = (season["airDate"] as? Double)?.toLong()
    val airYear    = airDateMs?.let {
        val cal = Calendar.getInstance(); cal.timeInMillis = it; cal.get(Calendar.YEAR)
    }

    val watchedInSeason = watchedEpisodes.filter { it.season == seasonNum }.map { it.episode }.toSet()
    val watchedCount    = watchedInSeason.size

    // Trigger load when first expanded
    LaunchedEffect(expanded) {
        if (expanded && episodeDetails.isEmpty() && seriesExternalId != null) {
            onExpand(seasonNum)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (posterUrl != null) {
                    AsyncImage(model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(50.dp).height(75.dp).clip(RoundedCornerShape(2.dp)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(seasonName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val subLabel = buildString {
                        if (totalEps > 0) append("$totalEps episódios")
                        if (airYear != null) append(" · $airYear")
                    }
                    if (subLabel.isNotEmpty()) {
                        Text(subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    if (totalEps > 0) {
                        LinearProgressIndicator(
                            progress   = { (watchedCount.toFloat() / totalEps).coerceIn(0f, 1f) },
                            modifier   = Modifier.fillMaxWidth(),
                            color      = ColorSerie,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            strokeCap  = StrokeCap.Butt,
                        )
                        Text("$watchedCount/$totalEps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }

            if (expanded && totalEps > 0) {
                HorizontalDivider()
                Column {
                    for (ep in 1..totalEps) {
                        val isWatched = ep in watchedInSeason
                        val epData    = episodeDetails[ep]
                        val epName    = epData?.first
                        val epDate    = epData?.second
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isWatched) onUnmarkEpisode(seasonNum, ep)
                                    else onMarkEpisode(seasonNum, ep)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Checkmark icon instead of RadioButton
                            Box(
                                Modifier.size(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isWatched) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = "Assistido",
                                        tint     = ColorSerie,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Não assistido",
                                        tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                val label = if (epName != null) "$ep. $epName" else "Episódio $ep"
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isWatched) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                )
                                if (epDate != null) {
                                    Text(epDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                            }
                        }
                        if (ep < totalEps) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesPersonRow(name: String, sub: String, photoUrl: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(ColorSerie.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                AsyncImage(model = photoUrl, contentDescription = name, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(28.dp), tint = Color.White.copy(alpha = 0.38f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) {
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
