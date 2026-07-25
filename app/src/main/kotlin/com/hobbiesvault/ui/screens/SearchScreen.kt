package com.hobbiesvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.*
import com.hobbiesvault.service.ApiServices
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.components.MediaGridCard
import com.hobbiesvault.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Date

// ── ViewModel ─────────────────────────────────────────────────────────────────

/** Remove acentos/diacríticos para permitir busca "Drac" encontrar "Drácula". */
private fun String.foldAccents(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "")

class SearchViewModel : ViewModel() {
    // Library search
    private val _query    = MutableStateFlow("")
    val query             = _query.asStateFlow()
    private val allItems  = DB.repo.watchAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localResults = combine(allItems, _query) { items, q ->
        if (q.isBlank()) emptyList()
        else {
            val needle = q.foldAccents()
            items.filter { it.title.foldAccents().contains(needle, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val existingExternalIds: StateFlow<Set<String>> = allItems.map { items ->
        items.mapNotNull { it.externalId }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // API search
    var selectedType  by mutableStateOf(MediaType.GAME)
    var apiResults    by mutableStateOf<List<ApiSearchResult>>(emptyList())
    var loading       by mutableStateOf(false)
    var apiSearched   by mutableStateOf(false)
    var searchError   by mutableStateOf<String?>(null)
        private set

    fun setQuery(q: String) { _query.value = q }

    fun setType(t: MediaType) {
        selectedType = t
        apiResults   = emptyList()
        apiSearched  = false
        searchError  = null
    }

    fun searchApi(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            loading = true
            apiSearched = true
            searchError = null

            // Fail fast if the required API key isn't configured
            val configError = ApiServices.serviceUnavailableReason(selectedType)
            if (configError != null) {
                searchError = configError
                apiResults  = emptyList()
                loading     = false
                return@launch
            }

            apiResults = runCatching {
                when (selectedType) {
                    MediaType.GAME    -> withContext(Dispatchers.IO) {
                        ApiServices.gameSearch.search(query)
                    }
                    MediaType.MOVIE   -> withContext(Dispatchers.IO) {
                        ApiServices.tmdb.searchMovies(query)
                    }
                    MediaType.SERIES  -> withContext(Dispatchers.IO) {
                        ApiServices.tmdb.searchSeries(query)
                    }
                    MediaType.MANGA, MediaType.WEBTOON -> withContext(Dispatchers.IO) {
                        ApiServices.mangaSearch.search(query)
                    }
                    MediaType.BOOK    -> withContext(Dispatchers.IO) {
                        ApiServices.bookSearch.searchBooks(query)
                    }
                }
            }.fold(
                onSuccess = { it },
                onFailure = { e -> searchError = e.message; emptyList() },
            )
            loading = false
        }
    }

    fun add(
        result: ApiSearchResult,
        status: MediaStatus,
        console: GameConsole? = null,
        streamingPlatform: String? = null,
        currentProgress: Int? = null,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val item = MediaItem(
                type              = selectedType,
                title             = result.title,
                status            = status,
                coverUrl          = result.coverUrl,
                addedDate         = Date(),
                completionDate    = if (status in setOf(MediaStatus.WATCHED, MediaStatus.READ, MediaStatus.FINISHED, MediaStatus.COMPLETED)) Date() else null,
                externalId        = result.externalId,
                apiSource         = result.apiSource,
                genre             = result.genre,
                developer         = result.developer,
                releaseDate       = result.releaseDate,
                console           = console,
                streamingPlatform = streamingPlatform,
                currentProgress   = currentProgress,
                totalProgress     = result.chapters ?: result.pages,
            )
            val id = DB.repo.save(item)
            val saved = DB.repo.getById(id)
            saved?.let { MediaCacheService.fetchAndPersist(it) }
            onDone()
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

private val streamingPlatforms = listOf(
    "Netflix", "Prime Video", "Disney+", "Apple TV+", "HBO Max", "Crunchyroll", "Outro",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, vm: SearchViewModel = viewModel()) {
    val query         by vm.query.collectAsStateWithLifecycle()
    val localResults  by vm.localResults.collectAsStateWithLifecycle()
    val existingIds   by vm.existingExternalIds.collectAsStateWithLifecycle()

    var apiMode by remember { mutableStateOf(false) }
    var apiQuery by remember { mutableStateOf("") }

    // Sheet state
    var addSheetResult by remember { mutableStateOf<ApiSearchResult?>(null) }

    val focusRequester    = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (apiMode) {
                        OutlinedTextField(
                            value         = apiQuery,
                            onValueChange = { apiQuery = it },
                            placeholder   = { Text("Buscar ${vm.selectedType.labelPt.lowercase()}…") },
                            singleLine    = true,
                            trailingIcon  = {
                                if (apiQuery.isNotEmpty()) {
                                    IconButton(onClick = { apiQuery = ""; vm.apiResults = emptyList(); vm.apiSearched = false }) {
                                        Icon(Icons.Default.Clear, null)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                vm.searchApi(apiQuery)
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    } else {
                        OutlinedTextField(
                            value         = query,
                            onValueChange = { vm.setQuery(it) },
                            placeholder   = { Text("Buscar na biblioteca…") },
                            leadingIcon   = { Icon(Icons.Default.Search, null) },
                            trailingIcon  = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { vm.setQuery("") }) {
                                        Icon(Icons.Default.Clear, null)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier   = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Mode toggle
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !apiMode,
                    onClick  = { apiMode = false; vm.setQuery("") },
                    label    = { Text("Biblioteca") },
                    leadingIcon = { Icon(Icons.Outlined.LibraryBooks, null, modifier = Modifier.size(16.dp)) },
                    shape    = RoundedCornerShape(4.dp),
                )
                FilterChip(
                    selected = apiMode,
                    onClick  = { apiMode = true; vm.setQuery("") },
                    label    = { Text("Nova busca") },
                    leadingIcon = { Icon(Icons.Outlined.TravelExplore, null, modifier = Modifier.size(16.dp)) },
                    shape    = RoundedCornerShape(4.dp),
                )
            }

            if (apiMode) {
                ApiSearchContent(
                    vm         = vm,
                    apiQuery   = apiQuery,
                    existingIds = existingIds,
                    onSearch   = { keyboardController?.hide(); vm.searchApi(apiQuery) },
                    onAddClick = { result -> addSheetResult = result },
                )
            } else {
                LibrarySearchContent(
                    query     = query,
                    results   = localResults,
                    onTap     = { item -> navigateToDetail(navController, item) },
                )
            }
        }
    }

    // Add bottom sheet
    addSheetResult?.let { result ->
        AddSheet(
            result  = result,
            type    = vm.selectedType,
            onDismiss = { addSheetResult = null },
            onAdd   = { status, console, platform, progress ->
                vm.add(result, status, console, platform, progress) { addSheetResult = null }
            },
        )
    }
}

// ── Library search content ────────────────────────────────────────────────────

@Composable
private fun LibrarySearchContent(
    query: String,
    results: List<MediaItem>,
    onTap: (MediaItem) -> Unit,
) {
    if (query.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Search, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                Text("Busque na sua biblioteca", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        return
    }
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum resultado para \"$query\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.id ?: it.title }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onTap(item) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.width(40.dp).height(56.dp).background(item.type.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.coverUrl != null) {
                        AsyncImage(
                            model              = item.coverUrl,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "${item.type.labelPt} · ${item.status.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
            HorizontalDivider(Modifier.padding(start = 68.dp))
        }
    }
}

// ── API search content ────────────────────────────────────────────────────────

@Composable
private fun ApiSearchContent(
    vm: SearchViewModel,
    apiQuery: String,
    existingIds: Set<String>,
    onSearch: () -> Unit,
    onAddClick: (ApiSearchResult) -> Unit,
) {
    val typeColor = vm.selectedType.color

    Column(Modifier.fillMaxSize()) {
        // Type chips
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaType.entries.forEach { t ->
                val selected = vm.selectedType == t
                Surface(
                    color    = if (selected) t.color else t.color.copy(alpha = 0.1f),
                    shape    = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { vm.setType(t) },
                ) {
                    Text(
                        t.labelPt,
                        color      = if (selected) Color.White else t.color,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // API banner — shows config warning or service label
        val serviceWarning = ApiServices.serviceUnavailableReason(vm.selectedType)
        val bannerColor = if (serviceWarning != null)
            MaterialTheme.colorScheme.errorContainer
        else
            typeColor.copy(alpha = 0.08f)
        val bannerContentColor = if (serviceWarning != null)
            MaterialTheme.colorScheme.onErrorContainer
        else
            typeColor
        Surface(
            color    = bannerColor,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape    = RoundedCornerShape(4.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (serviceWarning != null) Icons.Outlined.Warning else Icons.Outlined.Info,
                    null,
                    tint     = bannerContentColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    serviceWarning ?: "Buscando via ${apiLabelFor(vm.selectedType)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = bannerContentColor,
                )
            }
        }

        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color    = typeColor,
            )
        }

        when {
            vm.apiResults.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    contentPadding        = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.apiResults) { result ->
                        val inLibrary = result.externalId in existingIds
                        Box {
                            MediaGridCard(
                                title      = result.title,
                                coverUrl   = result.coverUrl,
                                accentColor = typeColor,
                                onAddClick  = if (inLibrary) null else ({ onAddClick(result) }),
                            )
                            if (inLibrary) {
                                Surface(
                                    color    = Color.Black.copy(alpha = 0.55f),
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                                ) {
                                    Text(
                                        "Na biblioteca",
                                        color    = Color.White,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            vm.apiSearched && !vm.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        if (vm.searchError != null) {
                            Icon(
                                Icons.Outlined.Warning,
                                null,
                                modifier = Modifier.size(40.dp),
                                tint     = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Não foi possível buscar",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                vm.searchError!!,
                                style     = MaterialTheme.typography.bodySmall,
                                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        } else {
                            Text(
                                "Nenhum resultado encontrado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
            !vm.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.TravelExplore, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Busque um ${vm.selectedType.labelPt.lowercase()}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Digite o nome e pressione Buscar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    }
}

// ── Add bottom sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(
    result: ApiSearchResult,
    type: MediaType,
    onDismiss: () -> Unit,
    onAdd: (status: MediaStatus, console: GameConsole?, platform: String?, progress: Int?) -> Unit,
) {
    var selectedStatus    by remember { mutableStateOf<MediaStatus?>(null) }
    var selectedConsole   by remember { mutableStateOf<GameConsole?>(null) }
    var selectedPlatform  by remember { mutableStateOf<String?>(null) }
    var progressInput     by remember { mutableStateOf("") }

    val typeColor = type.color
    val statuses = when (type) {
        MediaType.GAME -> when {
            selectedConsole?.isSteam == true || selectedConsole == GameConsole.PC -> MediaStatus.forSteam()
            selectedConsole?.isPlayStation == true                                 -> MediaStatus.forPlayStation()
            selectedConsole?.isNintendo == true                                    -> MediaStatus.forNintendo()
            else                                                                   -> MediaStatus.forOtherGames()
        }
        MediaType.MOVIE             -> MediaStatus.forMovie()
        MediaType.SERIES            -> MediaStatus.forSeries()
        MediaType.MANGA, MediaType.WEBTOON -> MediaStatus.forManga()
        MediaType.BOOK              -> MediaStatus.forBook()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title with type color accent
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(4.dp).height(20.dp).background(typeColor))
                Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            }

            // Console picker (games only)
            if (type == MediaType.GAME) {
                SheetSection("Plataforma") {
                    // PC e Steam contam como a mesma plataforma (sem suporte a lojas
                    // separadas como Epic Games) — apenas STEAM é oferecido aqui.
                    val mainConsoles = listOf(
                        GameConsole.STEAM, GameConsole.PS5, GameConsole.PS4,
                        GameConsole.NS, GameConsole.NS2, GameConsole.XBOX,
                    )
                    FlowChips(mainConsoles.map { it.label to it.color }) { idx ->
                        val c = mainConsoles[idx]
                        val sel = selectedConsole == c
                        selectedConsole = if (sel) null else c
                        selectedStatus  = null
                    }
                }
            }

            // Streaming platform (film/series only)
            if (type == MediaType.MOVIE || type == MediaType.SERIES) {
                SheetSection("Plataforma (opcional)") {
                    FlowChips(streamingPlatforms.map { it to typeColor }) { idx ->
                        val p = streamingPlatforms[idx]
                        selectedPlatform = if (selectedPlatform == p) null else p
                    }
                }
            }

            // Status chips
            SheetSection("Status") {
                FlowChips(statuses.map { it.label to it.color }) { idx ->
                    selectedStatus = if (selectedStatus == statuses[idx]) null else statuses[idx]
                }
            }

            // Progress field (reading/playing status)
            val showProgress = selectedStatus == MediaStatus.READING || selectedStatus == MediaStatus.PLAYING
            if (showProgress) {
                val progressLabel = when (type) {
                    MediaType.BOOK -> "Página atual (opcional)"
                    MediaType.MANGA, MediaType.WEBTOON -> "Capítulo atual (opcional)"
                    else -> null
                }
                if (progressLabel != null) {
                    SheetSection(progressLabel) {
                        OutlinedTextField(
                            value         = progressInput,
                            onValueChange = { progressInput = it },
                            placeholder   = { Text("0") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                }
            }

            // Add button
            Button(
                onClick  = {
                    selectedStatus?.let { status ->
                        onAdd(status, selectedConsole, selectedPlatform, progressInput.toIntOrNull())
                    }
                },
                enabled  = selectedStatus != null && (type != MediaType.GAME || selectedConsole != null),
                colors   = ButtonDefaults.buttonColors(containerColor = typeColor),
                shape    = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Adicionar ${type.labelPt.lowercase()}", color = Color.White)
            }
        }
    }
}

@Composable
private fun SheetSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(
    items: List<Pair<String, Color>>,
    onToggle: (index: Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { idx, (label, color) ->
            FilterChip(
                selected = false,
                onClick  = { onToggle(idx) },
                label    = { Text(label, fontSize = 12.sp) },
                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = color),
                shape    = RoundedCornerShape(4.dp),
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun apiLabelFor(type: MediaType) = when (type) {
    MediaType.MOVIE, MediaType.SERIES  -> "TMDB"
    MediaType.GAME                     -> "IGDB"
    MediaType.MANGA, MediaType.WEBTOON -> "AniList / MangaDex"
    MediaType.BOOK                     -> "Google Books"
}

private fun navigateToDetail(navController: NavController, item: MediaItem) {
    navController.currentBackStackEntry?.savedStateHandle?.set("item", item)
    val route = when (item.type) {
        MediaType.GAME              -> Routes.GAMES_DETAIL
        MediaType.MOVIE             -> Routes.FILMS_DETAIL
        MediaType.SERIES            -> Routes.SERIES_DETAIL
        MediaType.MANGA, MediaType.WEBTOON -> Routes.MANGA_DETAIL
        MediaType.BOOK              -> Routes.BOOKS_DETAIL
    }
    navController.navigate(route)
}
