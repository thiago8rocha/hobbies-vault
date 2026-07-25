package com.hobbiesvault.ui.screens.films

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.data.db.entity.MovieListEntity
import com.hobbiesvault.model.ApiSearchResult
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.service.ApiServices
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.components.MediaGridCard
import com.hobbiesvault.ui.components.StatusOptionTile
import com.hobbiesvault.ui.theme.ColorFilme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

enum class OpcaoFilme { ASSISTIDO, QUERO_ASSISTIR, ADICIONAR_LISTA }

class AddFilmViewModel : ViewModel() {
    private val _results = MutableStateFlow<List<ApiSearchResult>>(emptyList())
    val results = _results.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()
    private val _existingIds = MutableStateFlow<Set<String>>(emptySet())
    val existingIds = _existingIds.asStateFlow()
    var loading by mutableStateOf(false)
    private var debounceJob: Job? = null

    init {
        viewModelScope.launch {
            val items = DB.repo.getByType(MediaType.MOVIE)
            _existingIds.value = items.mapNotNull { it.externalId }.toSet()
        }
    }

    fun onQueryChange(query: String) {
        debounceJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            _searchError.value = null
            return
        }
        debounceJob = viewModelScope.launch {
            delay(600)
            search(query)
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            loading = true
            _searchError.value = null
            if (!ApiServices.tmdbAvailable) {
                _searchError.value = "TMDB não configurado — adicione tmdb_bearer_token ao secrets.json"
                _results.value = emptyList()
                loading = false
                return@launch
            }
            _results.value = runCatching {
                withContext(Dispatchers.IO) {
                    ApiServices.tmdb.searchMovies(query)
                        .sortedByDescending { it.releaseDate }
                }
            }.fold(
                onSuccess = { it },
                onFailure = { e -> _searchError.value = e.message; emptyList() },
            )
            loading = false
        }
    }

    fun statusParaQueroAssistir(result: ApiSearchResult): MediaStatus {
        val release = result.releaseDate
        return if (release != null && release.after(Date())) MediaStatus.WAITING_RELEASE
        else MediaStatus.QUEUED
    }

    fun add(result: ApiSearchResult, opcao: OpcaoFilme, listId: Int?, onDone: () -> Unit) {
        viewModelScope.launch {
            val status = when (opcao) {
                OpcaoFilme.ASSISTIDO      -> MediaStatus.WATCHED
                OpcaoFilme.QUERO_ASSISTIR -> statusParaQueroAssistir(result)
                OpcaoFilme.ADICIONAR_LISTA -> statusParaQueroAssistir(result)
            }
            val item = MediaItem(
                type        = MediaType.MOVIE,
                title       = result.title,
                status      = status,
                coverUrl    = result.coverUrl,
                addedDate   = Date(),
                externalId  = result.externalId.ifBlank { null },
                apiSource   = result.apiSource,
                releaseDate = result.releaseDate,
            )
            val newId = DB.repo.save(item)
            if (listId != null) DB.repo.addToList(listId, newId)
            _existingIds.value = _existingIds.value + setOfNotNull(result.externalId.ifBlank { null })
            onDone()
            MediaCacheService.doubleCheck(item.copy(id = newId))
        }
    }

    fun addManual(title: String, opcao: OpcaoFilme, listId: Int?, onDone: () -> Unit) {
        viewModelScope.launch {
            val status = when (opcao) {
                OpcaoFilme.ASSISTIDO -> MediaStatus.WATCHED
                else -> MediaStatus.QUEUED
            }
            val newId = DB.repo.save(MediaItem(type = MediaType.MOVIE, title = title, status = status, addedDate = Date()))
            if (listId != null) DB.repo.addToList(listId, newId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilmScreen(navController: NavController, vm: AddFilmViewModel = viewModel()) {
    val results     by vm.results.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    val existingIds by vm.existingIds.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selectedResult by remember { mutableStateOf<ApiSearchResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar filme") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.onQueryChange(it) },
                placeholder = { Text("Buscar filme via TMDB...") },
                leadingIcon  = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    when {
                        vm.loading -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(2.dp),
                            strokeWidth = 2.dp,
                        )
                        query.isNotEmpty() -> IconButton(onClick = {
                            query = ""
                            vm.onQueryChange("")
                        }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (searchError != null) {
                Text(
                    searchError!!,
                    color    = MaterialTheme.colorScheme.error,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (results.isEmpty() && !vm.loading && query.isNotEmpty()) {
                TextButton(
                    onClick  = { selectedResult = ApiSearchResult(externalId = "", title = query, apiSource = "manual") },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Adicionar manualmente") }
            }

            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                contentPadding        = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                items(results) { result ->
                    MediaGridCard(
                        title      = result.title,
                        coverUrl   = result.coverUrl,
                        inLibrary  = result.externalId in existingIds,
                        onAddClick = { if (result.externalId !in existingIds) selectedResult = result },
                    )
                }
            }
        }
    }

    selectedResult?.let { result ->
        val isUnreleased = result.releaseDate == null || result.releaseDate.after(Date())
        if (isUnreleased) {
            LaunchedEffect(result) {
                vm.add(result, OpcaoFilme.QUERO_ASSISTIR, null) {
                    selectedResult = null
                    navController.popBackStack()
                }
            }
        } else {
            AddFilmeSheet(
                result      = result,
                accentColor = ColorFilme,
                onDismiss   = { selectedResult = null },
                onSave      = { opcao, listId ->
                    if (result.externalId.isBlank()) {
                        vm.addManual(result.title, opcao, listId) {
                            selectedResult = null
                            navController.popBackStack()
                        }
                    } else {
                        vm.add(result, opcao, listId) {
                            selectedResult = null
                            navController.popBackStack()
                        }
                    }
                },
            )
        }
    }
}

// ── Bottom sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFilmeSheet(
    result: ApiSearchResult,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (OpcaoFilme, Int?) -> Unit,
) {
    var opcao by remember { mutableStateOf<OpcaoFilme?>(null) }
    var listaSelecionadaId by remember { mutableStateOf<Int?>(null) }
    var listas by remember { mutableStateOf<List<MovieListEntity>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        listas = DB.repo.getAllLists()
    }

    val botaoAtivo = opcao != null && (opcao != OpcaoFilme.ADICIONAR_LISTA || listaSelecionadaId != null)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp)
        ) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            StatusOptionTile(
                icon      = Icons.Outlined.CheckCircle,
                title     = "Assistido",
                subtitle  = "Já vi este filme",
                selected  = opcao == OpcaoFilme.ASSISTIDO,
                color     = accentColor,
                onClick   = { opcao = OpcaoFilme.ASSISTIDO; listaSelecionadaId = null },
            )
            Spacer(Modifier.height(8.dp))

            StatusOptionTile(
                icon      = Icons.Outlined.Bookmark,
                title     = "Quero Assistir",
                subtitle  = "Adicionar à fila",
                selected  = opcao == OpcaoFilme.QUERO_ASSISTIR,
                color     = accentColor,
                onClick   = { opcao = OpcaoFilme.QUERO_ASSISTIR; listaSelecionadaId = null },
            )
            Spacer(Modifier.height(8.dp))

            StatusOptionTile(
                icon      = Icons.Outlined.PlaylistAdd,
                title     = "Adicionar a Lista",
                subtitle  = "Organizar em uma lista temática",
                selected  = opcao == OpcaoFilme.ADICIONAR_LISTA,
                color     = accentColor,
                onClick   = { opcao = OpcaoFilme.ADICIONAR_LISTA },
            )

            if (opcao == OpcaoFilme.ADICIONAR_LISTA) {
                Spacer(Modifier.height(12.dp))
                if (listas.isEmpty()) {
                    TextButton(
                        onClick = { showNewListDialog = true },
                        colors  = ButtonDefaults.textButtonColors(contentColor = accentColor),
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Criar primeira lista", fontSize = 13.sp)
                    }
                } else {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listas.forEach { lista ->
                            FilterChip(
                                selected = listaSelecionadaId == lista.id,
                                onClick  = { listaSelecionadaId = lista.id },
                                label    = { Text(lista.name, fontSize = 12.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor     = Color.White,
                                ),
                            )
                        }
                        AssistChip(
                            onClick = { showNewListDialog = true },
                            label   = { Text("Nova lista", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick  = {
                    if (!saving && botaoAtivo) {
                        saving = true
                        onSave(opcao!!, listaSelecionadaId)
                    }
                },
                enabled  = botaoAtivo && !saving,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                if (saving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Adicionar")
                }
            }
        }
    }

    if (showNewListDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title   = { Text("Nova lista") },
            text    = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    placeholder = { Text("Nome da lista") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch {
                                DB.repo.createList(newName)
                                listas = DB.repo.getAllLists()
                                listaSelecionadaId = listas.lastOrNull()?.id
                                showNewListDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

