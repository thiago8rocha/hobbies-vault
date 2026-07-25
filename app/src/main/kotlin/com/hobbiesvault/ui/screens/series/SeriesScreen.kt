package com.hobbiesvault.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import com.hobbiesvault.data.db.entity.SeriesEpisodeEntity
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.components.EmptyState
import com.hobbiesvault.ui.components.ProportionalTabRow
import com.hobbiesvault.ui.navigation.Routes
import com.hobbiesvault.ui.theme.ColorSerie
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*

class SeriesViewModel : ViewModel() {
    val allItems = DB.repo.watchByType(MediaType.SERIES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEpisodes = DB.repo.watchAllEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(navController: NavController, vm: SeriesViewModel = viewModel()) {
    val allItems   by vm.allItems.collectAsStateWithLifecycle()
    val allEpisodes by vm.allEpisodes.collectAsStateWithLifecycle()

    val hoje = remember { Date() }
    val tabs  = listOf("Todos", "Assistindo", "Quero Assistir", "Histórico", "Em Breve")
    var selectedTab by remember { mutableIntStateOf(0) }

    // Em Breve = WAITING_RELEASE ou WAITING_EPISODES (renovada, nova temporada a caminho)
    val upcoming = remember(allItems, hoje) {
        allItems.filter {
            it.status == MediaStatus.WAITING_RELEASE ||
            it.status == MediaStatus.WAITING_EPISODES
        }.sortedBy { it.releaseDate }
    }

    // Histórico = HISTORY ou CONCLUDED (cancelada/terminada)
    val history = remember(allItems) {
        allItems.filter {
            it.status == MediaStatus.HISTORY ||
            it.status == MediaStatus.CONCLUDED
        }.sortedByDescending { it.completionDate ?: it.addedDate }
    }

    val filtered = remember(allItems, selectedTab) {
        when (selectedTab) {
            0 -> allItems
            1 -> allItems.filter { it.status == MediaStatus.WATCHING || it.status == MediaStatus.REWATCHING }
            2 -> allItems.filter { it.status == MediaStatus.QUEUED }.sortedBy { it.title }
            3 -> history
            4 -> upcoming
            else -> allItems
        }
    }

    // Episodes grouped by series id, for Histórico view
    val episodesBySeriesId = remember(allEpisodes) {
        allEpisodes.groupBy { it.mediaItemId }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title          = { Text("Séries") },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.navigate(Routes.HOME) { launchSingleTop = true }
                        }) {
                            Icon(Icons.Outlined.Home, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        }
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = null)
                        }
                    }
                )
                ProportionalTabRow(
                    selectedTabIndex = selectedTab,
                    tabs             = tabs,
                    selectedColor    = ColorSerie,
                    onTabSelected    = { selectedTab = it },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { navController.navigate(Routes.SERIES_ADD) },
                containerColor = ColorSerie,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Default.Add, contentDescription = null) },
                text           = { Text("Adicionar série") },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // ── Histórico — mostra episódios assistidos agrupados por série ──
            if (selectedTab == 3) {
                if (history.isEmpty()) {
                    EmptyState("Histórico vazio", "Séries concluídas aparecerão aqui",
                        "Adicionar série", onButton = { navController.navigate(Routes.SERIES_ADD) })
                } else {
                    val seriesById = remember(history) { history.associateBy { it.id } }
                    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        history.forEach { series ->
                            val episodes = episodesBySeriesId[series.id] ?: emptyList()

                            item(key = "series_header_${series.id}") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { navigateToDetail(navController, series) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (series.coverUrl != null) {
                                        AsyncImage(
                                            model              = series.coverUrl,
                                            contentDescription = null,
                                            contentScale       = ContentScale.Crop,
                                            modifier           = Modifier
                                                .width(44.dp)
                                                .height(64.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                        )
                                    } else {
                                        Box(
                                            Modifier.width(44.dp).height(64.dp)
                                                .background(ColorSerie.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Outlined.Tv, null, tint = ColorSerie.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(series.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${episodes.size} episódio${if (episodes.size != 1) "s" else ""} assistido${if (episodes.size != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    }
                                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                                }
                            }

                            if (episodes.isNotEmpty()) {
                                lazyItems(
                                    items    = episodes.take(5),
                                    key      = { "ep_${series.id}_${it.season}_${it.episode}" },
                                ) { ep ->
                                    EpisodeHistoryRow(ep = ep, dateFmt = dateFmt)
                                }
                                if (episodes.size > 5) {
                                    item(key = "ep_more_${series.id}") {
                                        Text(
                                            "… e mais ${episodes.size - 5} episódio${if (episodes.size - 5 != 1) "s" else ""}",
                                            style    = MaterialTheme.typography.bodySmall,
                                            color    = ColorSerie,
                                            modifier = Modifier.padding(start = 72.dp, bottom = 8.dp),
                                        )
                                    }
                                }
                            }

                            item(key = "divider_${series.id}") {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                val (title, subtitle) = when (selectedTab) {
                    0 -> "Nenhuma série na biblioteca" to "Adicione uma série para começar"
                    1 -> "Nenhuma série em andamento" to "Séries que você está assistindo aparecerão aqui"
                    2 -> "Nenhuma série na fila" to "Séries que você quer assistir aparecerão aqui"
                    else -> "Nenhum lançamento pendente" to "Séries aguardando estreia ou nova temporada aparecerão aqui"
                }
                EmptyState(title, subtitle, "Adicionar série", onButton = { navController.navigate(Routes.SERIES_ADD) })
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    contentPadding        = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered) { item ->
                        SeriesCard(item = item, onTap = { navigateToDetail(navController, item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeHistoryRow(ep: SeriesEpisodeEntity, dateFmt: SimpleDateFormat) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "T${ep.season} E${ep.episode}${if (!ep.episodeName.isNullOrBlank()) " · ${ep.episodeName}" else ""}",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            dateFmt.format(Date(ep.watchedAtMs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SeriesCard(item: MediaItem, onTap: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onTap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.56f)) {
            if (item.coverUrl != null) {
                AsyncImage(
                    model              = item.coverUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(ColorSerie.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Tv, null, tint = ColorSerie.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                }
            }
        }
        Text(
            item.title,
            style    = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(34.dp),
        )
    }
}

private fun navigateToDetail(navController: NavController, item: MediaItem) {
    navController.currentBackStackEntry?.savedStateHandle?.set("item", item)
    navController.navigate(Routes.SERIES_DETAIL)
}
