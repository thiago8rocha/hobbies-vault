package com.hobbiesvault.ui.screens.manga

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
import com.hobbiesvault.ui.theme.ColorManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class AddMangaViewModel : ViewModel() {
    private val _results = MutableStateFlow<List<ApiSearchResult>>(emptyList())
    val results = _results.asStateFlow()
    var loading by mutableStateOf(false)

    fun search(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch {
            loading = true
            _results.value = runCatching {
                withContext(Dispatchers.IO) { ApiServices.mangaSearch.search(q) }
            }.getOrElse { emptyList() }
            loading = false
        }
    }

    fun clear() {
        _results.value = emptyList()
    }

    fun add(result: ApiSearchResult, status: MediaStatus, onDone: () -> Unit) {
        viewModelScope.launch {
            val item = MediaItem(
                type          = MediaType.MANGA,
                title         = result.title,
                status        = status,
                coverUrl      = result.coverUrl,
                addedDate     = Date(),
                externalId    = result.externalId,
                apiSource     = result.apiSource,
                totalProgress = result.chapters,
            )
            val newId = DB.repo.save(item)
            onDone()
            MediaCacheService.fetchAndPersist(item.copy(id = newId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMangaScreen(navController: NavController, vm: AddMangaViewModel = viewModel()) {
    val results by vm.results.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showSheet by remember { mutableStateOf<ApiSearchResult?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Adicionar Mangá") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Buscar mangá ou webtoon...") },
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
            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { r -> MediaGridCard(title = r.title, coverUrl = r.coverUrl, onAddClick = { showSheet = r }) }
            }
        }
    }
    showSheet?.let { result ->
        val statuses = MediaStatus.forMangaAdd()
        ModalBottomSheet(onDismissRequest = { showSheet = null }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                statuses.forEachIndexed { index, status ->
                    val (icon, subtitle) = mangaStatusInfo(status)
                    StatusOptionTile(
                        icon     = icon,
                        title    = mangaStatusLabel(status),
                        subtitle = subtitle,
                        selected = false,
                        color    = ColorManga,
                        onClick  = { vm.add(result, status) { navController.popBackStack() }; showSheet = null },
                    )
                    if (index != statuses.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun mangaStatusInfo(status: MediaStatus): Pair<ImageVector, String> = when (status) {
    MediaStatus.READING   -> Icons.Default.MenuBook to "Acompanhando os capítulos"
    MediaStatus.REREADING -> Icons.Default.Replay to "Lendo de novo"
    MediaStatus.QUEUED    -> Icons.Default.Bookmark to "Quer começar a ler"
    else                  -> Icons.Default.Bookmark to ""
}
