package com.hobbiesvault.ui.screens.series

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.ApiSearchResult
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.service.ApiServices
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.components.MediaGridCard
import com.hobbiesvault.ui.components.StatusOptionTile
import com.hobbiesvault.ui.theme.ColorSerie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class AddSeriesViewModel : ViewModel() {
    private val _results = MutableStateFlow<List<ApiSearchResult>>(emptyList())
    val results = _results.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()
    var loading by mutableStateOf(false)

    fun search(q: String) {
        if (q.isBlank()) return
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
                withContext(Dispatchers.IO) { ApiServices.tmdb.searchSeries(q) }
            }.fold(
                onSuccess = { it },
                onFailure = { e -> _searchError.value = e.message; emptyList() },
            )
            loading = false
        }
    }

    fun clear() {
        _results.value = emptyList()
        _searchError.value = null
    }

    fun add(result: ApiSearchResult, status: MediaStatus, onDone: () -> Unit) {
        viewModelScope.launch {
            val item = MediaItem(
                type       = MediaType.SERIES,
                title      = result.title,
                status     = status,
                coverUrl   = result.coverUrl,
                addedDate  = Date(),
                externalId = result.externalId,
                apiSource  = result.apiSource,
            )
            val newId = DB.repo.save(item)
            onDone()
            MediaCacheService.fetchAndPersist(item.copy(id = newId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSeriesScreen(navController: NavController, vm: AddSeriesViewModel = viewModel()) {
    val results     by vm.results.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showSheet by remember { mutableStateOf<ApiSearchResult?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Adicionar Série") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar série...") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; vm.clear() }) { Icon(Icons.Default.Clear, null) }
                    } else {
                        IconButton(onClick = { vm.search(query) }) { Icon(Icons.Default.Search, null) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
            if (vm.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (searchError != null) {
                Surface(
                    color    = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape    = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                ) {
                    Text(
                        searchError!!,
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { r -> MediaGridCard(title = r.title, coverUrl = r.coverUrl, onAddClick = { showSheet = r }) }
            }
        }
    }
    showSheet?.let { result ->
        val statuses = MediaStatus.forSeriesAdd()
        ModalBottomSheet(onDismissRequest = { showSheet = null }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                statuses.forEachIndexed { index, status ->
                    val (icon, subtitle) = seriesStatusInfo(status)
                    StatusOptionTile(
                        icon     = icon,
                        title    = status.label,
                        subtitle = subtitle,
                        selected = false,
                        color    = ColorSerie,
                        onClick  = { vm.add(result, status) { navController.popBackStack() }; showSheet = null },
                    )
                    if (index != statuses.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun seriesStatusInfo(status: MediaStatus): Pair<ImageVector, String> = when (status) {
    MediaStatus.WATCHING   -> Icons.Default.PlayArrow to "Acompanhando os episódios"
    MediaStatus.REWATCHING -> Icons.Default.Replay to "Vendo tudo de novo"
    MediaStatus.QUEUED     -> Icons.Default.Bookmark to "Quer começar a assistir"
    MediaStatus.HISTORY    -> Icons.Default.History to "Já assistiu, sem acompanhar mais"
    else                   -> Icons.Default.Bookmark to ""
}
