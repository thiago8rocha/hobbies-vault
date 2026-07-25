package com.hobbiesvault.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hobbiesvault.model.GameConsole
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.components.EmptyState
import com.hobbiesvault.ui.components.ProportionalTabRow
import com.hobbiesvault.ui.navigation.Routes
import com.hobbiesvault.ui.theme.ColorJogo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GamesViewModel : ViewModel() {
    val allItems = DB.repo.watchByType(MediaType.GAME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var filterStatus  by mutableStateOf<MediaStatus?>(null)
    var filterConsole by mutableStateOf<GameConsole?>(null)

    val hasActiveFilters get() = filterStatus != null || filterConsole != null

    fun setFilters(status: MediaStatus?, console: GameConsole?) {
        filterStatus  = status
        filterConsole = console
    }

    fun clearFilters() {
        filterStatus  = null
        filterConsole = null
    }

    fun markFinished(item: MediaItem, newStatus: MediaStatus) {
        viewModelScope.launch {
            DB.repo.update(item.copy(status = newStatus, completionDate = Date()))
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(navController: NavController, vm: GamesViewModel = viewModel()) {
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    var showFilters  by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf<MediaItem?>(null) }

    fun applyFilters(items: List<MediaItem>): List<MediaItem> {
        var r = items
        vm.filterStatus?.let  { s -> r = r.filter { it.status  == s } }
        vm.filterConsole?.let { c -> r = r.filter { it.console == c } }
        return r
    }

    val hoje = remember { Date() }

    val playing  = remember(allItems, vm.filterStatus, vm.filterConsole) {
        applyFilters(allItems.filter {
            it.status == MediaStatus.PLAYING || it.status == MediaStatus.REPLAYING
        })
    }
    val finished = remember(allItems, vm.filterStatus, vm.filterConsole) {
        applyFilters(allItems.filter {
            it.status == MediaStatus.FINISHED || it.status == MediaStatus.COMPLETED
        }).sortedByDescending { it.completionDate ?: it.addedDate }
    }
    val platinum = remember(allItems, vm.filterStatus, vm.filterConsole) {
        applyFilters(allItems.filter { it.status == MediaStatus.PLATINUM })
            .sortedByDescending { it.completionDate ?: it.addedDate }
    }
    val backlog  = remember(allItems, vm.filterStatus, vm.filterConsole) {
        applyFilters(allItems.filter {
            it.status == MediaStatus.QUEUED || it.status == MediaStatus.DROPPED
        }).sortedBy { it.title }
    }
    val upcoming = remember(allItems, vm.filterStatus, vm.filterConsole) {
        applyFilters(allItems.filter {
            it.status == MediaStatus.WAITING_RELEASE && it.releaseDate?.after(hoje) == true
        }).sortedBy { it.releaseDate }
    }

    val tabs = listOf("Jogando", "Zerado", "Platinado", "Backlog", "Em Breve")
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title          = { Text("Jogos") },
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
                        Box {
                            IconButton(onClick = { showFilters = true }) {
                                Icon(Icons.Outlined.Tune, contentDescription = null)
                            }
                            if (vm.hasActiveFilters) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 10.dp, end = 10.dp)
                                        .size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Outlined.Settings, contentDescription = null)
                        }
                    }
                )
                ProportionalTabRow(
                    selectedTabIndex = selectedTab,
                    tabs             = tabs,
                    selectedColor    = ColorJogo,
                    onTabSelected    = { selectedTab = it },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { navController.navigate(Routes.GAMES_ADD) },
                containerColor = ColorJogo,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Default.Add, contentDescription = null) },
                text           = { Text("Adicionar jogo") },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> GameGrid(
                    items         = playing,
                    emptyTitle    = "Nenhum jogo em andamento",
                    emptySubtitle = "Adicione um jogo para começar",
                    emptyButton   = "Adicionar jogo",
                    onEmpty       = { navController.navigate(Routes.GAMES_ADD) },
                    onTap         = { navigateToDetail(navController, it) },
                    showCheckBtn  = true,
                    onCheck       = { finishDialog = it },
                )
                1 -> GameGrid(
                    items         = finished,
                    emptyTitle    = "Nenhum jogo zerado",
                    emptySubtitle = "Jogos que você terminou aparecerão aqui",
                    emptyButton   = "Adicionar jogo",
                    onEmpty       = { navController.navigate(Routes.GAMES_ADD) },
                    onTap         = { navigateToDetail(navController, it) },
                )
                2 -> GameGrid(
                    items         = platinum,
                    emptyTitle    = "Nenhum jogo platinado",
                    emptySubtitle = "Jogos com conquistas completas aparecerão aqui",
                    emptyButton   = "Adicionar jogo",
                    onEmpty       = { navController.navigate(Routes.GAMES_ADD) },
                    onTap         = { navigateToDetail(navController, it) },
                )
                3 -> GameGrid(
                    items         = backlog,
                    emptyTitle    = "Backlog vazio",
                    emptySubtitle = "Jogos que você quer jogar aparecerão aqui",
                    emptyButton   = "Adicionar jogo",
                    onEmpty       = { navController.navigate(Routes.GAMES_ADD) },
                    onTap         = { navigateToDetail(navController, it) },
                )
                4 -> UpcomingTab(
                    items   = upcoming,
                    onEmpty = { navController.navigate(Routes.GAMES_ADD) },
                    onTap   = { navigateToDetail(navController, it) },
                )
            }
        }
    }

    // ── Finish dialog ─────────────────────────────────────────────────────────
    finishDialog?.let { item ->
        val isPs    = item.console?.isPlayStation == true
        val isSteam = item.console?.isSteam == true
        val isPc    = item.console == GameConsole.PC
        AlertDialog(
            onDismissRequest = { finishDialog = null },
            title = { Text("Jogo finalizado!") },
            text  = {
                Text(when {
                    isSteam || isPc -> "Você vai buscar todas as conquistas ou mover para Zerado?"
                    isPs            -> "Você vai buscar a platina ou mover para Zerado?"
                    else            -> "Deseja mover este jogo para Zerado?"
                })
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isSteam || isPc) {
                        TextButton(onClick = {
                            vm.markFinished(item, MediaStatus.COMPLETED)
                            finishDialog = null
                        }) { Text("Buscar conquistas") }
                    }
                    if (isPs) {
                        TextButton(onClick = {
                            vm.markFinished(item, MediaStatus.PLATINUM)
                            finishDialog = null
                        }) { Text("Buscar platina") }
                    }
                    TextButton(onClick = {
                        vm.markFinished(item, MediaStatus.FINISHED)
                        finishDialog = null
                    }) { Text("Mover para zerado") }
                    TextButton(onClick = { finishDialog = null }) { Text("Cancelar") }
                }
            },
            dismissButton = null,
        )
    }

    // ── Filter sheet ──────────────────────────────────────────────────────────
    if (showFilters) {
        FiltersSheet(
            currentStatus  = vm.filterStatus,
            currentConsole = vm.filterConsole,
            onApply        = { s, c -> vm.setFilters(s, c); showFilters = false },
            onClear        = { vm.clearFilters(); showFilters = false },
            onDismiss      = { showFilters = false },
        )
    }
}

// ── Game grid ─────────────────────────────────────────────────────────────────

@Composable
private fun GameGrid(
    items: List<MediaItem>,
    emptyTitle: String,
    emptySubtitle: String,
    emptyButton: String,
    onEmpty: () -> Unit,
    onTap: (MediaItem) -> Unit,
    showCheckBtn: Boolean = false,
    onCheck: ((MediaItem) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        EmptyState(emptyTitle, emptySubtitle, emptyButton, onEmpty)
        return
    }
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        contentPadding        = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item ->
            GameCard(
                item         = item,
                onTap        = { onTap(item) },
                showCheckBtn = showCheckBtn,
                onCheck      = if (showCheckBtn) { { onCheck?.invoke(item) } } else null,
            )
        }
    }
}

// ── Game card ─────────────────────────────────────────────────────────────────

@Composable
private fun GameCard(
    item: MediaItem,
    onTap: () -> Unit,
    showCheckBtn: Boolean = false,
    onCheck: (() -> Unit)? = null,
) {
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
                    Modifier.fillMaxSize().background(ColorJogo.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.SportsEsports, null, tint = ColorJogo.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                }
            }
            // Platform badge — top left
            item.console?.let { console ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        console.label,
                        fontSize   = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color      = console.color,
                    )
                }
            }
            // Check button — bottom right (Jogando tab only)
            if (showCheckBtn && onCheck != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onCheck),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
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

// ── Em Breve tab — grouped by month ──────────────────────────────────────────

@Composable
private fun UpcomingTab(
    items: List<MediaItem>,
    onEmpty: () -> Unit,
    onTap: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            title       = "Nenhum lançamento pendente",
            subtitle    = "Jogos com data de lançamento futura aparecerão aqui",
            buttonLabel = "Adicionar jogo",
            onButton    = onEmpty,
        )
        return
    }

    val months = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )

    data class MonthGroup(val header: String, val items: List<MediaItem>)

    val groups = remember(items) {
        items.groupBy { item ->
            val cal = Calendar.getInstance().apply { time = item.releaseDate!! }
            "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
        }.map { (h, i) -> MonthGroup(h, i) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPad      = 12.dp
        val gap       = 8.dp
        val cardWidth = (maxWidth - hPad * 2 - gap * 2) / 3

        LazyColumn(contentPadding = PaddingValues(start = hPad, end = hPad, top = 4.dp, bottom = 80.dp)) {
            groups.forEach { group ->
                item(key = group.header) {
                    Text(
                        group.header,
                        style    = MaterialTheme.typography.labelLarge.copy(
                            color       = ColorJogo,
                            fontWeight  = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                val rows = group.items.chunked(3)
                items(rows) { row ->
                    Row(
                        Modifier.padding(bottom = gap),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        repeat(3) { i ->
                            if (i < row.size) {
                                Box(Modifier.width(cardWidth)) {
                                    GameCard(item = row[i], onTap = { onTap(row[i]) })
                                }
                            } else {
                                Spacer(Modifier.width(cardWidth))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Filter sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(
    currentStatus: MediaStatus?,
    currentConsole: GameConsole?,
    onApply: (MediaStatus?, GameConsole?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var status  by remember { mutableStateOf(currentStatus) }
    var console by remember { mutableStateOf(currentConsole) }

    val consoleOptions = listOf(GameConsole.STEAM, GameConsole.PC, GameConsole.PS5, GameConsole.PS4, GameConsole.NS, GameConsole.NS2)
    val statusOptions  = listOf(
        MediaStatus.PLAYING, MediaStatus.REPLAYING,
        MediaStatus.FINISHED, MediaStatus.PLATINUM, MediaStatus.COMPLETED,
        MediaStatus.QUEUED, MediaStatus.DROPPED,
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtros", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.weight(1f))
                if (status != null || console != null) {
                    TextButton(onClick = onClear) { Text("Limpar") }
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("Plataforma", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                consoleOptions.forEach { c ->
                    val sel = console == c
                    FilterChip(
                        selected = sel,
                        onClick  = { console = if (sel) null else c },
                        label    = { Text(c.label) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.color,
                            selectedLabelColor     = Color.White,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Status", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                statusOptions.forEach { s ->
                    val sel = status == s
                    FilterChip(
                        selected = sel,
                        onClick  = { status = if (sel) null else s },
                        label    = { Text(s.label) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = s.color,
                            selectedLabelColor     = Color.White,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick  = { onApply(status, console) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Aplicar filtros")
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun navigateToDetail(navController: NavController, item: MediaItem) {
    navController.currentBackStackEntry?.savedStateHandle?.set("item", item)
    navController.navigate(Routes.GAMES_DETAIL)
}
