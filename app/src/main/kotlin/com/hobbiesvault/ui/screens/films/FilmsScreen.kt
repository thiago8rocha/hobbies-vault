package com.hobbiesvault.ui.screens.films

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import com.hobbiesvault.data.db.entity.MovieListEntity
import com.hobbiesvault.data.db.entity.MovieListItemEntity
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.components.EmptyState
import com.hobbiesvault.ui.components.OverflowMenu
import com.hobbiesvault.ui.components.OverflowMenuItem
import com.hobbiesvault.ui.components.ProportionalTabRow
import com.hobbiesvault.ui.navigation.Routes
import com.hobbiesvault.ui.theme.ColorFilme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*

private val ListButtonShape = RoundedCornerShape(6.dp)

class FilmsViewModel : ViewModel() {
    val allItems = DB.repo.watchByType(MediaType.MOVIE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists = DB.repo.watchLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listItems: kotlinx.coroutines.flow.StateFlow<Map<Int, Set<Int>>> =
        DB.repo.watchAllListItems()
            .map { rows: List<MovieListItemEntity> ->
                rows.groupBy { it.listId }
                    .mapValues { (_, v) -> v.map { it.mediaItemId }.toSet() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun createList(name: String, description: String?) {
        viewModelScope.launch { DB.repo.createList(name, description) }
    }

    fun updateList(id: Int, name: String, description: String?) {
        viewModelScope.launch { DB.repo.updateList(id, name, description) }
    }

    fun deleteList(id: Int) {
        viewModelScope.launch { DB.repo.deleteList(id) }
    }

    fun toggleFilmInList(listId: Int, filmId: Int, add: Boolean) {
        viewModelScope.launch {
            if (add) DB.repo.addToList(listId, filmId)
            else DB.repo.removeFromList(listId, filmId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmsScreen(navController: NavController, vm: FilmsViewModel = viewModel()) {
    val allItems  by vm.allItems.collectAsStateWithLifecycle()
    val lists     by vm.lists.collectAsStateWithLifecycle()
    val listItems by vm.listItems.collectAsStateWithLifecycle()

    val hoje = remember { Date() }
    val tabs = listOf("Todos", "Assistidos", "Quero Assistir", "Listas", "Em Breve")
    var selectedTab by remember { mutableIntStateOf(0) }

    val allListedIds = remember(listItems) { listItems.values.flatten().toSet() }

    val watched = remember(allItems) {
        allItems.filter { it.status == MediaStatus.WATCHED || it.status == MediaStatus.REWATCHING }
                .sortedByDescending { it.completionDate ?: it.addedDate }
    }
    val queued = remember(allItems) {
        allItems.filter { it.status == MediaStatus.QUEUED }.sortedBy { it.title }
    }
    val upcoming = remember(allItems, hoje) {
        allItems.filter { it.status == MediaStatus.WAITING_RELEASE }
                .sortedBy { it.releaseDate }
    }
    val queuedUngrouped = remember(queued, allListedIds) {
        queued.filter { it.id == null || it.id !in allListedIds }
    }

    // Dialogs / sheets
    var showMenu          by remember { mutableStateOf(false) }
    var showCreateDialog  by remember { mutableStateOf(false) }
    var openList          by remember { mutableStateOf<MovieListEntity?>(null) }
    var deleteTarget      by remember { mutableStateOf<MovieListEntity?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title          = { Text("Filmes") },
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
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        OverflowMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            OverflowMenuItem(
                                text    = "Configurações",
                                icon    = Icons.Outlined.Settings,
                                onClick = { showMenu = false; navController.navigate(Routes.SETTINGS) },
                            )
                        }
                    }
                )
                ProportionalTabRow(
                    selectedTabIndex = selectedTab,
                    tabs             = tabs,
                    selectedColor    = ColorFilme,
                    onTabSelected    = { selectedTab = it },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { navController.navigate(Routes.FILMS_ADD) },
                containerColor = ColorFilme,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Default.Add, null) },
                text           = { Text("Adicionar filme") },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                // ── Todos ──────────────────────────────────────────────────────
                0 -> {
                    if (allItems.isEmpty()) {
                        EmptyState("Nenhum filme na biblioteca", "Adicione um filme para começar",
                            "Adicionar filme", onButton = { navController.navigate(Routes.FILMS_ADD) })
                    } else {
                        FilmGrid(allItems) { navigateToDetail(navController, it) }
                    }
                }

                // ── Assistidos ─────────────────────────────────────────────────
                1 -> {
                    if (watched.isEmpty()) {
                        EmptyState("Nenhum filme assistido", "Filmes que você assistiu aparecerão aqui",
                            "Adicionar filme", onButton = { navController.navigate(Routes.FILMS_ADD) })
                    } else {
                        FilmGrid(watched) { navigateToDetail(navController, it) }
                    }
                }

                // ── Quero Assistir ─────────────────────────────────────────────
                2 -> {
                    if (queued.isEmpty()) {
                        EmptyState("Nenhum filme na fila", "Filmes que você quer assistir aparecerão aqui",
                            "Adicionar filme", onButton = { navController.navigate(Routes.FILMS_ADD) })
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                            if (queuedUngrouped.isNotEmpty()) {
                                filmGridRows(queuedUngrouped) { navigateToDetail(navController, it) }
                            }
                            lists.forEach { list ->
                                val listFilmIds = listItems[list.id] ?: emptySet()
                                val listFilms = queued.filter { it.id != null && it.id in listFilmIds }
                                if (listFilms.isNotEmpty()) {
                                    item(key = "list_header_${list.id}") {
                                        Text(
                                            list.name,
                                            style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color    = ColorFilme,
                                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                                        )
                                    }
                                    filmGridRows(listFilms, "list_${list.id}_") { navigateToDetail(navController, it) }
                                }
                            }
                        }
                    }
                }

                // ── Listas ─────────────────────────────────────────────────────
                3 -> {
                    if (lists.isEmpty()) {
                        // Centered empty state with button
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 32.dp),
                            verticalArrangement   = Arrangement.Center,
                            horizontalAlignment   = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Nenhuma lista ainda",
                                style     = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Crie listas para organizar filmes por tema ou ocasião",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(28.dp))
                            Button(
                                onClick  = { showCreateDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = ListButtonShape,
                                colors   = ButtonDefaults.buttonColors(containerColor = ColorFilme),
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Nova lista")
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // "Nova lista" button at the top
                            item {
                                Button(
                                    onClick  = { showCreateDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape    = ListButtonShape,
                                    colors   = ButtonDefaults.buttonColors(containerColor = ColorFilme),
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Nova lista")
                                }
                            }

                            lists.forEach { list ->
                                val listFilmIds = listItems[list.id] ?: emptySet()
                                val filmCount   = listFilmIds.size

                                item(key = "list_card_${list.id}") {
                                    ListCard(
                                        list      = list,
                                        filmCount = filmCount,
                                        onClick   = { openList = list },
                                        onDelete  = { deleteTarget = list },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Em Breve ───────────────────────────────────────────────────
                4 -> {
                    if (upcoming.isEmpty()) {
                        EmptyState("Nenhum lançamento pendente", "Filmes com data futura aparecerão aqui",
                            "Adicionar filme", onButton = { navController.navigate(Routes.FILMS_ADD) })
                    } else {
                        val noDate   = upcoming.filter { it.releaseDate == null }
                        val withDate = upcoming.filter { it.releaseDate != null }
                        val byMonth  = withDate.groupBy {
                            val cal = Calendar.getInstance().also { c -> c.time = it.releaseDate!! }
                            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                        }.toSortedMap(compareBy({ it.first }, { it.second }))
                        val monthFmt = remember { java.text.SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }

                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            byMonth.forEach { (yearMonth, films) ->
                                val cal = Calendar.getInstance().also { c ->
                                    c.set(yearMonth.first, yearMonth.second, 1)
                                }
                                val header = monthFmt.format(cal.time).replaceFirstChar { it.uppercaseChar() }
                                item(key = "header_${yearMonth.first}_${yearMonth.second}") {
                                    Text(
                                        header,
                                        style    = MaterialTheme.typography.labelLarge,
                                        color    = ColorFilme,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                                item(key = "grid_${yearMonth.first}_${yearMonth.second}") {
                                    FilmGridSection(films) { navigateToDetail(navController, it) }
                                }
                            }
                            if (noDate.isNotEmpty()) {
                                item(key = "header_nodate") {
                                    Text(
                                        "Sem data definida",
                                        style    = MaterialTheme.typography.labelLarge,
                                        color    = ColorFilme,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                                item(key = "grid_nodate") {
                                    FilmGridSection(noDate) { navigateToDetail(navController, it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── List detail sheet ──────────────────────────────────────────────────────
    openList?.let { list ->
        MovieListSheet(
            list      = list,
            allFilms  = allItems,
            filmIds   = listItems[list.id] ?: emptySet(),
            onDismiss = { openList = null },
            onSave    = { name, desc -> vm.updateList(list.id, name, desc); openList = null },
            onDelete  = { deleteTarget = list; openList = null },
            onToggle  = { filmId, add -> vm.toggleFilmInList(list.id, filmId, add) },
            navController = navController,
        )
    }

    // ── Create list dialog ─────────────────────────────────────────────────────
    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onCreate  = { name, desc ->
                vm.createList(name, desc)
                showCreateDialog = false
            },
        )
    }

    // ── Delete list confirmation ────────────────────────────────────────────────
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title   = { Text("Excluir lista") },
            text    = { Text("Excluir \"${target.name}\"? Os filmes não serão removidos da biblioteca.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteList(target.id); deleteTarget = null },
                    shape   = ListButtonShape,
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Excluir") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }, shape = ListButtonShape) { Text("Cancelar") }
            },
        )
    }
}

// ── Create list dialog ────────────────────────────────────────────────────────

@Composable
private fun CreateListDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova lista") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Nome *") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Descrição (opcional)") },
                    minLines      = 2,
                    maxLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { onCreate(name.trim(), description.trim().ifBlank { null }) },
                enabled  = name.isNotBlank(),
                shape    = ListButtonShape,
                colors   = ButtonDefaults.buttonColors(containerColor = ColorFilme),
            ) { Text("Criar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = ListButtonShape) { Text("Cancelar") }
        },
    )
}

// ── List card ─────────────────────────────────────────────────────────────────

@Composable
private fun ListCard(
    list: MovieListEntity,
    filmCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(8.dp),
        color   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (!list.description.isNullOrBlank()) {
                    Text(
                        list.description,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    "$filmCount filme${if (filmCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorFilme.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    null,
                    tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Movie list detail sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieListSheet(
    list: MovieListEntity,
    allFilms: List<MediaItem>,
    filmIds: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
    onDelete: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    navController: NavController,
) {
    var name        by remember { mutableStateOf(list.name) }
    var description by remember { mutableStateOf(list.description ?: "") }
    val nameChanged = name.trim() != list.name || description.trim() != (list.description ?: "")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp)
        ) {
            // Editable name + description
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Nome") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value         = description,
                onValueChange = { description = it },
                label         = { Text("Descrição (opcional)") },
                minLines      = 2,
                maxLines      = 3,
                modifier      = Modifier.fillMaxWidth(),
            )

            if (nameChanged) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = { onSave(name.trim(), description.trim().ifBlank { null }) },
                    enabled  = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape    = ListButtonShape,
                    colors   = ButtonDefaults.buttonColors(containerColor = ColorFilme),
                ) { Text("Salvar alterações") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Films section
            if (allFilms.isEmpty()) {
                Text(
                    "Nenhum filme na biblioteca ainda.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            } else {
                Text(
                    "Filmes",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier           = Modifier.weight(1f, fill = false).heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    lazyItems(allFilms, key = { it.id ?: it.title }) { film ->
                        val inList = film.id != null && film.id in filmIds
                        FilmToggleRow(
                            film     = film,
                            inList   = inList,
                            color    = ColorFilme,
                            onToggle = { film.id?.let { id -> onToggle(id, !inList) } },
                            onTap    = { navigateToDetail(navController, film); onDismiss() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick  = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape    = ListButtonShape,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Excluir lista")
            }
        }
    }
}

// ── Film toggle row (inside list detail sheet) ────────────────────────────────

@Composable
private fun FilmToggleRow(
    film: MediaItem,
    inList: Boolean,
    color: Color,
    onToggle: () -> Unit,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mini cover
        Box(
            Modifier
                .size(width = 36.dp, height = 52.dp)
                .background(
                    if (film.coverUrl != null) Color.Transparent
                    else color.copy(alpha = 0.15f),
                    RoundedCornerShape(4.dp),
                )
        ) {
            if (film.coverUrl != null) {
                AsyncImage(
                    model              = film.coverUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Outlined.Movie,
                    null,
                    tint     = color.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            film.title,
            style    = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        // Toggle button
        if (inList) {
            FilledIconButton(
                onClick  = onToggle,
                modifier = Modifier.size(36.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(containerColor = color),
            ) {
                Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
            }
        } else {
            OutlinedIconButton(
                onClick  = onToggle,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun FilmGrid(items: List<MediaItem>, onTap: (MediaItem) -> Unit) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        contentPadding        = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item -> FilmCard(item = item, onTap = { onTap(item) }) }
    }
}

@Composable
private fun FilmGridSection(items: List<MediaItem>, onTap: (MediaItem) -> Unit) {
    val rows = (items.size + 2) / 3
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    if (idx < items.size) {
                        Box(Modifier.weight(1f)) { FilmCard(item = items[idx], onTap = { onTap(items[idx]) }) }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.filmGridRows(
    items: List<MediaItem>,
    keyPrefix: String = "",
    onTap: (MediaItem) -> Unit,
) {
    val rows = items.chunked(3)
    rows.forEachIndexed { rowIdx, row ->
        item(key = "${keyPrefix}row_$rowIdx") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    Box(Modifier.weight(1f)) { FilmCard(item, onTap = { onTap(item) }) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FilmCard(item: MediaItem, onTap: () -> Unit) {
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
                    Modifier.fillMaxSize().background(ColorFilme.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Movie, null, tint = ColorFilme.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
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
    navController.navigate(Routes.FILMS_DETAIL)
}
