package com.hobbiesvault.ui.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.GameConsole
import com.hobbiesvault.model.GamePlaythrough
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.service.ApiServices
import com.hobbiesvault.service.HltbResult
import com.hobbiesvault.service.ItadDeal
import com.hobbiesvault.service.ItadPricePoint
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.components.CoverImage
import com.hobbiesvault.ui.components.NotesDialog
import com.hobbiesvault.ui.theme.ColorJogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class GameDetailViewModel : ViewModel() {
    var mediaItem by mutableStateOf<MediaItem?>(null)
    var cache by mutableStateOf<Map<String, Any?>?>(null)
    var loadingCache by mutableStateOf(true)
    var hltbResult by mutableStateOf<HltbResult?>(null)
    var itadDeals by mutableStateOf<List<ItadDeal>>(emptyList())
    var priceHistory by mutableStateOf<List<ItadPricePoint>>(emptyList())
    private var initialized = false

    fun init(initial: MediaItem) {
        if (initialized) return
        initialized = true
        mediaItem = initial
        viewModelScope.launch {
            cache = MediaCacheService.load(initial)
            loadingCache = false
        }
        MediaCacheService.doubleCheck(initial) {
            val updated = MediaCacheService.load(initial)
            cache = updated
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (ApiServices.hltbAvailable) {
                hltbResult = runCatching { ApiServices.hltb.search(initial.title).firstOrNull() }.getOrNull()
            }
        }
        if (initial.console?.isSteam == true && initial.externalId != null) {
            val steamAppId = initial.externalId.toIntOrNull()
            if (steamAppId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    itadDeals = runCatching { ApiServices.itad?.getPrices(steamAppId) ?: emptyList() }.getOrNull() ?: emptyList()
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val uuid = runCatching { ApiServices.itad?.lookupGameUuid(steamAppId) }.getOrNull()
                    if (uuid != null) {
                        priceHistory = runCatching { ApiServices.itad?.getPriceHistory(uuid) ?: emptyList() }.getOrNull() ?: emptyList()
                    }
                }
            }
        }
    }

    fun setStatus(newStatus: MediaStatus) {
        val current = mediaItem ?: return
        val now = java.util.Date()
        val isCompletion = newStatus in listOf(MediaStatus.FINISHED, MediaStatus.COMPLETED, MediaStatus.PLATINUM)
        val updated = current.copy(
            status = newStatus,
            // completionDate genérico segue usado por GamesScreen para ordenar Histórico/Platinado.
            completionDate = if (isCompletion) now else current.completionDate,
            historyCompletionDate  = if (newStatus == MediaStatus.FINISHED && current.historyCompletionDate == null) now else current.historyCompletionDate,
            extrasCompletionDate   = if (newStatus == MediaStatus.COMPLETED && current.extrasCompletionDate == null) now else current.extrasCompletionDate,
            platinumCompletionDate = if (newStatus == MediaStatus.PLATINUM && current.platinumCompletionDate == null) now else current.platinumCompletionDate,
        )
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun setConsole(console: GameConsole) {
        val current = mediaItem ?: return
        val updated = current.copy(console = console)
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

    fun savePlaythrough(playthrough: GamePlaythrough) {
        val id = mediaItem?.id ?: return
        viewModelScope.launch { DB.repo.savePlaythrough(id, playthrough) }
    }

    fun deletePlaythrough(id: Int) {
        viewModelScope.launch { DB.repo.deletePlaythrough(id) }
    }

    fun refreshCache() {
        val current = mediaItem ?: return
        viewModelScope.launch {
            MediaCacheService.fetchAndPersist(current)
            cache = MediaCacheService.load(current)
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = mediaItem ?: return
        viewModelScope.launch {
            current.id?.let { DB.repo.delete(it); DB.cache.delete(it) }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GameDetailScreen(
    navController: NavController,
    initialItem: MediaItem,
    vm: GameDetailViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.init(initialItem) }

    val mediaItem   = vm.mediaItem ?: initialItem
    val cache       = vm.cache
    val hltb        = vm.hltbResult
    val console     = mediaItem.console
    val platformColor = console?.color ?: ColorJogo

    var showDelete       by remember { mutableStateOf(false) }
    var showMoreMenu     by remember { mutableStateOf(false) }
    var showStatusMenu   by remember { mutableStateOf(false) }
    var showCompletedMenu by remember { mutableStateOf(false) }
    var showConsoleMenu  by remember { mutableStateOf(false) }
    var showNotes        by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }
    var showAddPlaythrough by remember { mutableStateOf(false) }
    var editingPlaythrough by remember { mutableStateOf<GamePlaythrough?>(null) }

    val playthroughs by remember(mediaItem.id) {
        mediaItem.id?.let { DB.repo.watchPlaythroughs(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val artworkUrl   = cache?.get("artworkUrl") as? String
    val coverUrl     = cache?.get("coverUrl") as? String ?: mediaItem.coverUrl
    val synopsis     = cache?.get("synopsis") as? String
    val genre        = cache?.get("genre") as? String ?: mediaItem.genre
    val developer    = cache?.get("developer") as? String ?: mediaItem.developer
    val publisher    = cache?.get("publisher") as? String
    val platforms    = (cache?.get("platforms") as? List<*>)?.filterIsInstance<String>()
    val releaseDateMs = (cache?.get("releaseDate") as? Double)?.toLong()
    val dlcs          = (cache?.get("dlcs") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val expansions    = (cache?.get("expansions") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val recommendations = (cache?.get("recommendations") as? List<*>)?.filterIsInstance<Map<String, Any?>>()

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val currentPlatformLabel = consoleDisplayName(console)
    // Enquanto o cache não traz a lista completa da API (ex.: jogo recém-adicionado),
    // ainda mostramos ao menos a plataforma já rastreada — essencial pra exclusivos.
    val platformsDisplay = platforms?.takeIf { it.isNotEmpty() } ?: currentPlatformLabel?.let { listOf(it) }
    val bgUrl = artworkUrl ?: coverUrl

    val registroLabel = when (mediaItem.status) {
        MediaStatus.PLAYING              -> "Jogando"
        MediaStatus.REPLAYING            -> "Rejogando"
        MediaStatus.QUEUED               -> "Backlog"
        MediaStatus.FINISHED             -> "Completado · História"
        MediaStatus.COMPLETED            -> "Completado · Extras"
        MediaStatus.PLATINUM           -> "Completado · 100%"
        MediaStatus.DROPPED              -> "Abandonado"
        MediaStatus.WAITING_RELEASE      -> "Em Breve"
        else                             -> mediaItem.status.label
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val coverWidth  = (screenWidth * 0.58f).coerceIn(160.dp, 230.dp)
    val coverHeight = coverWidth / 0.714f

    Scaffold { _ ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {

                // ── Header ────────────────────────────────────────────────────
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(coverHeight + 160.dp) // cover + top status bar space + title
                    ) {
                        // Blurred background
                        AsyncImage(
                            model              = bgUrl,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize().blur(28.dp),
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

                        // Centered cover + title (below status bar area)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                                .padding(top = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Cover with shadow
                            Box(
                                Modifier
                                    .shadow(30.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model              = coverUrl,
                                    contentDescription = null,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.width(coverWidth).height(coverHeight),
                                )
                            }
                            Spacer(Modifier.height(28.dp))
                            // Title
                            Text(
                                mediaItem.title,
                                color      = Color.White,
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign  = TextAlign.Center,
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.padding(horizontal = 28.dp),
                                lineHeight = 26.sp,
                            )
                        }

                        // Bottom gradient fade
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                                    )
                                )
                        )
                    }
                }

                // ── Status button row (centered) ──────────────────────────────
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        // Status button — IntrinsicWidth style, centered
                        // Aguardando Lançamento é um status fixo: some sozinho quando o jogo sai.
                        val statusFixed = mediaItem.status == MediaStatus.WAITING_RELEASE
                        Box {
                            Surface(
                                color   = platformColor,
                                shape   = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .widthIn(min = 150.dp, max = 260.dp)
                                    .let { if (statusFixed) it else it.clickable { showStatusMenu = true } },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (statusFixed) Icons.Default.Schedule else Icons.Default.UnfoldMore,
                                        null, tint = Color.White, modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        registroLabel,
                                        color      = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 14.sp,
                                    )
                                }
                            }
                            DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                                GameStatusMenuItem(Icons.Default.PlayArrow, "Jogando", mediaItem.status == MediaStatus.PLAYING, platformColor) { vm.setStatus(MediaStatus.PLAYING); showStatusMenu = false }
                                GameStatusMenuItem(Icons.Default.Replay, "Rejogando", mediaItem.status == MediaStatus.REPLAYING, platformColor) { vm.setStatus(MediaStatus.REPLAYING); showStatusMenu = false }
                                GameStatusMenuItem(Icons.Default.Queue, "Backlog", mediaItem.status == MediaStatus.QUEUED, platformColor) { vm.setStatus(MediaStatus.QUEUED); showStatusMenu = false }
                                DropdownMenuItem(
                                    text = { Text("Completado") },
                                    leadingIcon  = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                                    trailingIcon = {
                                        if (mediaItem.status in listOf(MediaStatus.FINISHED, MediaStatus.COMPLETED, MediaStatus.PLATINUM))
                                            Icon(Icons.Default.Check, null, tint = platformColor, modifier = Modifier.size(16.dp))
                                        else
                                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = { showStatusMenu = false; showCompletedMenu = true },
                                )
                                GameStatusMenuItem(Icons.Default.Close, "Abandonado", mediaItem.status == MediaStatus.DROPPED, platformColor) { vm.setStatus(MediaStatus.DROPPED); showStatusMenu = false }
                            }
                        }
                        // "Completado" submenu — História / Extras / 100%
                        Box {
                            DropdownMenu(expanded = showCompletedMenu, onDismissRequest = { showCompletedMenu = false }) {
                                GameStatusMenuItem(Icons.Default.MenuBook, "História", mediaItem.status == MediaStatus.FINISHED, platformColor) { vm.setStatus(MediaStatus.FINISHED); showCompletedMenu = false }
                                GameStatusMenuItem(Icons.Default.AutoAwesome, "Extras", mediaItem.status == MediaStatus.COMPLETED, platformColor) { vm.setStatus(MediaStatus.COMPLETED); showCompletedMenu = false }
                                GameStatusMenuItem(Icons.Default.EmojiEvents, "100%", mediaItem.status == MediaStatus.PLATINUM, platformColor) { vm.setStatus(MediaStatus.PLATINUM); showCompletedMenu = false }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // "..." button — 48x48 square with border
                        Box {
                            Surface(
                                shape   = RoundedCornerShape(10.dp),
                                color   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.size(48.dp).clickable { showMoreMenu = true },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MoreHoriz, null, modifier = Modifier.size(22.dp))
                                }
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(text = { Text("Atualizar") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { vm.refreshCache(); showMoreMenu = false })
                                DropdownMenuItem(text = { Text("Notas") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) }, onClick = { showNotes = true; showMoreMenu = false })
                                if ((platforms?.size ?: 0) > 1) {
                                    DropdownMenuItem(text = { Text("Alterar plataforma") }, leadingIcon = { Icon(Icons.Default.Devices, null) }, onClick = { showConsoleMenu = true; showMoreMenu = false })
                                }
                                DropdownMenuItem(text = { Text(if (mediaItem.favorite) "Remover dos favoritos" else "Favoritar") }, leadingIcon = { Icon(if (mediaItem.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }, onClick = { vm.toggleFavorite(); showMoreMenu = false })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Remover jogo", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showDelete = true; showMoreMenu = false })
                            }
                        }
                    }
                }

                val contentPad = Modifier.padding(horizontal = 16.dp)

                // ── Sinopse ───────────────────────────────────────────────────
                if (synopsis != null) {
                    item {
                        Column(contentPad) {
                            GameSectionTitle("Sinopse")
                            Spacer(Modifier.height(10.dp))
                            Text(
                                synopsis,
                                style   = MaterialTheme.typography.bodyMedium,
                                color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 22.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (synopsisExpanded) "Ver menos" else "Ver mais",
                                color      = platformColor,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.clickable { synopsisExpanded = !synopsisExpanded },
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Informações ───────────────────────────────────────────────
                if (genre != null || developer != null || publisher != null) {
                    item {
                        Column(contentPad) {
                            GameSectionTitle("Informações")
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (genre != null)      GameInfoTile("Gênero",        genre)
                                if (developer != null)  GameInfoTile("Desenvolvedor", developer)
                                if (publisher != null)  GameInfoTile("Publicadora",   publisher)
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Disponível em ─────────────────────────────────────────────
                if (!platformsDisplay.isNullOrEmpty()) {
                    item {
                        Column(contentPad) {
                            GameSectionTitle("Disponível em")
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement   = Arrangement.spacedBy(6.dp),
                            ) {
                                platformsDisplay.forEach { platform ->
                                    val highlighted = platform == currentPlatformLabel
                                    PlatformTag(platform, highlighted, platformColor)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── DLCs e Expansões ────────────────────────────────────────────
                if (!dlcs.isNullOrEmpty() || !expansions.isNullOrEmpty()) {
                    item {
                        Column(contentPad) {
                            GameSectionTitle("DLCs e Expansões")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items((expansions.orEmpty() + dlcs.orEmpty())) { g ->
                                    GameRelatedTile(
                                        title    = g["title"] as? String ?: "",
                                        coverUrl = g["coverUrl"] as? String,
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Datas ─────────────────────────────────────────────────────
                item {
                    Column(contentPad) {
                        GameSectionTitle("Datas")
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GameInfoTile("Adicionado", dateFormatter.format(mediaItem.addedDate))
                            if (mediaItem.historyCompletionDate != null)
                                GameInfoTile("Concluído (História)", dateFormatter.format(mediaItem.historyCompletionDate))
                            if (mediaItem.extrasCompletionDate != null)
                                GameInfoTile("Concluído (Extras)", dateFormatter.format(mediaItem.extrasCompletionDate))
                            if (mediaItem.platinumCompletionDate != null)
                                GameInfoTile("Concluído (100%)", dateFormatter.format(mediaItem.platinumCompletionDate))
                            if (releaseDateMs != null)
                                GameInfoTile("Lançamento", dateFormatter.format(java.util.Date(releaseDateMs)))
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // ── Duração estimada (HLTB) ───────────────────────────────────
                if (hltb != null && (hltb.mainStorySeconds != null || hltb.mainExtraSeconds != null || hltb.completionistSeconds != null)) {
                    item {
                        Column(contentPad) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                GameSectionTitle("Duração estimada")
                                Spacer(Modifier.width(6.dp))
                                Text("HowLongToBeat", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                            Spacer(Modifier.height(10.dp))
                            HltbCard(hltb, platformColor)
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Jogatinas ─────────────────────────────────────────────────
                item {
                    Column(contentPad) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            GameSectionTitle("Jogatinas")
                            IconButton(
                                onClick  = { editingPlaythrough = null; showAddPlaythrough = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Nova jogatina", tint = platformColor, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (playthroughs.isEmpty()) {
                            Text(
                                "Nenhuma jogatina registrada ainda",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                playthroughs.forEach { pt ->
                                    PlaythroughTile(
                                        playthrough = pt,
                                        dateFormatter = dateFormatter,
                                        color = platformColor,
                                        onClick = { editingPlaythrough = pt; showAddPlaythrough = true },
                                        onDelete = { vm.deletePlaythrough(pt.id) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // ── Preços (IsThereAnyDeal) ────────────────────────────────────
                if (vm.itadDeals.isNotEmpty()) {
                    item {
                        Column(contentPad) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                GameSectionTitle("Preços")
                                Spacer(Modifier.width(6.dp))
                                Text("IsThereAnyDeal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                vm.itadDeals.sortedBy { it.price }.forEach { deal -> PriceDealTile(deal, platformColor) }
                            }
                            if (vm.priceHistory.size >= 2) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Menor preço ao longo do tempo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                                Spacer(Modifier.height(8.dp))
                                val runningMinPoints = remember(vm.priceHistory) {
                                    var min = Double.MAX_VALUE
                                    vm.priceHistory.map { p ->
                                        min = minOf(min, p.price)
                                        com.hobbiesvault.ui.components.LinePoint(p.timestampMs.toFloat(), min.toFloat())
                                    }
                                }
                                com.hobbiesvault.ui.components.LineChartCanvas(
                                    points    = runningMinPoints,
                                    lineColor = platformColor,
                                    modifier  = Modifier.fillMaxWidth().height(80.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        dateFormatter.format(java.util.Date(vm.priceHistory.first().timestampMs)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                    Text(
                                        "R$ %.2f menor preço".format(runningMinPoints.last().y),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = platformColor,
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Conquistas / Troféus ──────────────────────────────────────
                val showAchievements = mediaItem.achievementsUnlocked != null || mediaItem.totalAchievements != null || mediaItem.hasTrophies
                if (showAchievements) {
                    item {
                        Column(contentPad) {
                            GameSectionTitle(if (console?.isPlayStation == true) "Troféus" else "Conquistas")
                            Spacer(Modifier.height(10.dp))
                            AchievementsCard(mediaItem, console?.isPlayStation == true, platformColor)
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Recomendações ────────────────────────────────────────────────
                if (!recommendations.isNullOrEmpty()) {
                    item {
                        Column(contentPad) {
                            GameSectionTitle("Recomendações")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(recommendations) { g ->
                                    GameRelatedTile(
                                        title    = g["title"] as? String ?: "",
                                        coverUrl = g["coverUrl"] as? String,
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            // Floating back button — circular, black-54 background
            IconButton(
                onClick  = { navController.popBackStack() },
                modifier = Modifier.statusBarsPadding().padding(4.dp),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.54f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    // ── Console selection sheet ────────────────────────────────────────────────
    if (showConsoleMenu) {
        // PC e Steam são a mesma plataforma para fins de biblioteca (sem suporte a Epic
        // Games ou outras lojas separadas) — apresentadas como uma única opção "PC",
        // que usa o console STEAM internamente para manter o rastreio de conquistas.
        val groups = listOf(
            "PC"           to listOf(GameConsole.STEAM),
            "PlayStation"  to listOf(GameConsole.PS5, GameConsole.PS4, GameConsole.PS3, GameConsole.PS2, GameConsole.PS1, GameConsole.PSP, GameConsole.PS_VITA),
            "Xbox"         to listOf(GameConsole.XSX, GameConsole.X_ONE, GameConsole.X360, GameConsole.XBOX),
            "Nintendo"     to listOf(GameConsole.NS2, GameConsole.NS, GameConsole.WII_U, GameConsole.WII, GameConsole.GCN, GameConsole.N64, GameConsole.SNES, GameConsole.NES, GameConsole.N3DS, GameConsole.DS, GameConsole.GBA),
            "Outros"       to listOf(GameConsole.MOBILE, GameConsole.OUTRO),
        )
        ModalBottomSheet(onDismissRequest = { showConsoleMenu = false }) {
            Text("Alterar plataforma", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                groups.forEach { (groupLabel, consoles) ->
                    item {
                        Text(
                            groupLabel.uppercase(),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(consoles.size) { idx ->
                        val c = consoles[idx]
                        val isSelected = c == console || (c == GameConsole.STEAM && console == GameConsole.PC)
                        ListItem(
                            headlineContent = { Text(c.label) },
                            leadingContent  = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick  = { vm.setConsole(c); showConsoleMenu = false },
                                    colors   = RadioButtonDefaults.colors(selectedColor = platformColor),
                                )
                            },
                            modifier = Modifier.clickable { vm.setConsole(c); showConsoleMenu = false },
                        )
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
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

    if (showAddPlaythrough) {
        AddPlaythroughDialog(
            initial   = editingPlaythrough,
            color     = platformColor,
            onDismiss = { showAddPlaythrough = false },
            onSave    = { vm.savePlaythrough(it); showAddPlaythrough = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title   = { Text("Remover jogo") },
            text    = { Text("Remover \"${mediaItem.title}\" da biblioteca?") },
            confirmButton = {
                Button(onClick = { vm.delete { navController.popBackStack() } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } },
        )
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun GameSectionTitle(text: String) {
    Text(
        text,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
    )
}

@Composable
private fun GameInfoTile(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                label,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.width(110.dp),
            )
            Text(
                value,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlatformTag(label: String, highlighted: Boolean, color: Color) {
    Box(
        Modifier
            .background(
                if (highlighted) color.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (highlighted) Modifier.padding(0.dp) else Modifier
            )
    ) {
        Surface(
            color  = Color.Transparent,
            border = BorderStroke(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) color.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                label,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                color      = if (highlighted) color else MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun PlaythroughTile(
    playthrough: GamePlaythrough,
    dateFormatter: SimpleDateFormat,
    color: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(playthrough.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val range = listOfNotNull(
                    playthrough.startDate?.let { dateFormatter.format(it) },
                    playthrough.endDate?.let { dateFormatter.format(it) },
                ).joinToString(" – ")
                val details = listOfNotNull(
                    range.ifEmpty { null },
                    playthrough.hoursPlayed?.let { "${it}h" },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = color)
                }
                if (!playthrough.notes.isNullOrBlank()) {
                    Text(
                        playthrough.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remover", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddPlaythroughDialog(
    initial: GamePlaythrough?,
    color: Color,
    onDismiss: () -> Unit,
    onSave: (GamePlaythrough) -> Unit,
) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    var title    by remember { mutableStateOf(initial?.title ?: "") }
    var startStr by remember { mutableStateOf(initial?.startDate?.let { fmt.format(it) } ?: "") }
    var endStr   by remember { mutableStateOf(initial?.endDate?.let { fmt.format(it) } ?: "") }
    var hoursStr by remember { mutableStateOf(initial?.hoursPlayed?.toString() ?: "") }
    var notes    by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(if (initial == null) "Nova jogatina" else "Editar jogatina") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    placeholder = { Text("Ex.: Primeira zerada, Speedrun...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startStr, onValueChange = { startStr = it },
                        label = { Text("Início (dd/MM/aaaa)") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endStr, onValueChange = { endStr = it },
                        label = { Text("Fim (dd/MM/aaaa)") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = hoursStr, onValueChange = { hoursStr = it.filter(Char::isDigit) },
                    label = { Text("Horas jogadas") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    placeholder = { Text("Notas (opcional)") }, minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        GamePlaythrough(
                            id          = initial?.id ?: 0,
                            title       = title.trim(),
                            startDate   = runCatching { startStr.takeIf { it.isNotBlank() }?.let { fmt.parse(it) } }.getOrNull(),
                            endDate     = runCatching { endStr.takeIf { it.isNotBlank() }?.let { fmt.parse(it) } }.getOrNull(),
                            hoursPlayed = hoursStr.toIntOrNull(),
                            notes       = notes.trim().ifBlank { null },
                        )
                    )
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun GameRelatedTile(title: String, coverUrl: String?) {
    Column(Modifier.width(90.dp)) {
        CoverImage(
            url      = coverUrl,
            modifier = Modifier.width(90.dp).height(130.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            title,
            style     = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines  = 2,
            overflow  = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PriceDealTile(deal: ItadDeal, color: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (deal.url.isNotBlank()) {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(deal.url)))
                    }
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(deal.store, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (deal.discount > 0) {
                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "-${deal.discount}%",
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text("R$ %.2f".format(deal.price), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun HltbCard(hltb: HltbResult, color: Color) {
    fun fmtSec(s: Int?): String {
        if (s == null || s <= 0) return "—"
        val h = s / 3600; val m = (s % 3600) / 60
        return when { h == 0 -> "${m}min"; m == 0 -> "${h}h"; else -> "${h}h ${m}min" }
    }
    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shape  = RoundedCornerShape(12.dp),
    ) {
        IntrinsicRow {
            HltbItem(Icons.Default.MenuBook, "História",    fmtSec(hltb.mainStorySeconds),     color)
            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            HltbItem(Icons.Default.Explore, "Hist.+Extras", fmtSec(hltb.mainExtraSeconds),    color)
            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            HltbItem(Icons.Default.CheckCircle, "100%",    fmtSec(hltb.completionistSeconds), color)
        }
    }
}

@Composable
private fun IntrinsicRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), content = content)
}

@Composable
private fun RowScope.HltbItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color) {
    Column(
        Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun AchievementsCard(item: MediaItem, isPS: Boolean, color: Color) {
    val unlocked = item.achievementsUnlocked
    val total    = item.totalAchievements
    val pct      = if (unlocked != null && total != null && total > 0) unlocked.toFloat() / total else null

    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shape  = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            if (pct != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (isPS) "Troféus desbloqueados" else "Conquistas desbloqueadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Text("$unlocked / $total", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth(),
                    color    = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text("${(pct * 100).toInt()}% concluído", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                if (item.hasTrophies) Spacer(Modifier.height(16.dp))
            }
            if (item.hasTrophies) {
                Row {
                    if (item.platinumTrophy == true) TrophyBadge("✓", "Platina", Color(0xFF90A4AE))
                    if (item.goldTrophies != null)   TrophyBadge("${item.goldTrophies}", "Ouro",   Color(0xFFFFD700))
                    if (item.silverTrophies != null) TrophyBadge("${item.silverTrophies}", "Prata", Color(0xFFB0BEC5))
                    if (item.bronzeTrophies != null) TrophyBadge("${item.bronzeTrophies}", "Bronze", Color(0xFFCD7F32))
                }
            }
            if (pct == null && !item.hasTrophies) {
                Row {
                    Icon(
                        if (isPS) Icons.Default.EmojiEvents else Icons.Default.MilitaryTech,
                        null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isPS) "Nenhum troféu registrado ainda." else "Nenhuma conquista registrada ainda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrophyBadge(value: String, label: String, color: Color) {
    Column(Modifier.padding(end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun GameStatusMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
        trailingIcon = { if (selected) Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
    )
}

private fun consoleDisplayName(console: GameConsole?): String? = when (console) {
    GameConsole.STEAM, GameConsole.PC -> "PC"
    GameConsole.PS1       -> "PS1"
    GameConsole.PS2       -> "PS2"
    GameConsole.PS3       -> "PS3"
    GameConsole.PS4       -> "PS4"
    GameConsole.PS5       -> "PS5"
    GameConsole.PSP       -> "PSP"
    GameConsole.PS_VITA   -> "PS Vita"
    GameConsole.XBOX      -> "Xbox"
    GameConsole.X360      -> "Xbox 360"
    GameConsole.X_ONE     -> "Xbox One"
    GameConsole.XSX       -> "Series X/S"
    GameConsole.NS        -> "Switch"
    GameConsole.NS2       -> "Switch 2"
    GameConsole.WII       -> "Wii"
    GameConsole.WII_U     -> "Wii U"
    GameConsole.NES       -> "NES"
    GameConsole.SNES      -> "SNES"
    GameConsole.N64       -> "N64"
    GameConsole.GCN       -> "GameCube"
    GameConsole.GBA       -> "GBA"
    GameConsole.DS        -> "DS"
    GameConsole.N3DS      -> "3DS"
    else                  -> null
}
