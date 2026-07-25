package com.hobbiesvault.ui.screens.books

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
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
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.components.NotesDialog
import com.hobbiesvault.ui.theme.ColorLivro
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun commentEntries(notes: String?): List<String> =
    notes?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

class BookDetailViewModel : ViewModel() {
    var mediaItem by mutableStateOf<MediaItem?>(null)
    var cache by mutableStateOf<Map<String, Any?>?>(null)
    var loadingCache by mutableStateOf(true)
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
            if (updated != null) cache = updated
        }
    }

    // Mudar para Lido sempre fixa o progresso no total (leitura concluída) e, se a
    // transição vier de uma releitura, grava data_releitura em vez de mexer na data
    // de conclusão original — mesma regra da versão Flutter de referência.
    private fun markRead(current: MediaItem, totalPages: Int?): MediaItem =
        if (current.status == MediaStatus.REREADING) {
            current.copy(
                status          = MediaStatus.READ,
                currentProgress = totalPages ?: current.currentProgress,
                rereadingDate   = Date(),
            )
        } else {
            current.copy(
                status          = MediaStatus.READ,
                currentProgress = totalPages ?: current.currentProgress,
                completionDate  = current.completionDate ?: Date(),
            )
        }

    fun setStatus(newStatus: MediaStatus, totalPages: Int?) {
        val current = mediaItem ?: return
        val updated = when (newStatus) {
            MediaStatus.READ                          -> markRead(current, totalPages)
            MediaStatus.READING, MediaStatus.QUEUED    -> current.copy(status = newStatus, completionDate = null)
            else                                       -> current.copy(status = newStatus)
        }
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun setProgress(page: Int, comment: String?, totalPages: Int?) {
        val current = mediaItem ?: return
        val clampedPage = totalPages?.let { page.coerceIn(0, it) } ?: page.coerceAtLeast(0)
        val newNotes = if (!comment.isNullOrBlank()) {
            val now = Date()
            val prefix = SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(now)
            val entry = "$prefix (p.$clampedPage): $comment"
            if (!current.notes.isNullOrBlank()) "${current.notes}\n$entry" else entry
        } else current.notes

        val withProgress = current.copy(currentProgress = clampedPage, notes = newNotes)
        val updated = if (totalPages != null && clampedPage >= totalPages) markRead(withProgress, totalPages) else withProgress
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun saveNotes(newNotes: String?) {
        val current = mediaItem ?: return
        val updated = current.copy(notes = newNotes?.takeIf { it.isNotBlank() })
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun setStartDate(date: Date) {
        val current = mediaItem ?: return
        val updated = current.copy(readingStartDate = date)
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun setCompletionDate(date: Date) {
        val current = mediaItem ?: return
        val updated = current.copy(completionDate = date)
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun toggleFavorite() {
        val current = mediaItem ?: return
        val updated = current.copy(favorite = !current.favorite)
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun setPersonalNotes(text: String) {
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

    fun delete(onDone: () -> Unit) {
        val current = mediaItem ?: return
        viewModelScope.launch {
            current.id?.let { DB.repo.delete(it); DB.cache.delete(it) }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    navController: NavController,
    initialItem: MediaItem,
    vm: BookDetailViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.init(initialItem) }

    val mediaItem = vm.mediaItem ?: initialItem
    val cache = vm.cache

    var showDelete by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPersonalNotes by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }
    var showPageDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var commentInput by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    // Book cache — prefer extracted fields, fall back to volumeInfo for legacy entries
    val volumeInfo    = cache?.get("volumeInfo") as? Map<*, *>
    val releaseDateMs = (cache?.get("releaseDate") as? Double)?.toLong()
        ?: (volumeInfo?.get("publishedDate") as? String)?.let {
            runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time }.getOrNull()
                ?: runCatching { java.text.SimpleDateFormat("yyyy", java.util.Locale.US).parse(it)?.time }.getOrNull()
        }
    val synopsis  = cache?.get("synopsis") as? String
        ?: volumeInfo?.get("description") as? String
    val authors   = (cache?.get("author") as? String)?.let { listOf(it) }
        ?: (volumeInfo?.get("authors") as? List<*>)?.filterIsInstance<String>()
    val publisher = cache?.get("publisher") as? String
        ?: volumeInfo?.get("publisher") as? String
    val pages     = (cache?.get("pages") as? Double)?.toInt()
        ?: (volumeInfo?.get("pageCount") as? Double)?.toInt()
        ?: mediaItem.totalProgress
    val genre     = cache?.get("genre") as? String ?: mediaItem.genre
    val coverFromCache = cache?.get("coverUrl") as? String
        ?: (volumeInfo?.get("imageLinks") as? Map<*, *>)?.let {
            (it["thumbnail"] as? String ?: it["smallThumbnail"] as? String)
                ?.replace("http://", "https://")?.replace("zoom=1", "zoom=3")
        }
    val coverUrl = coverFromCache ?: mediaItem.coverUrl

    val commentLines = commentEntries(mediaItem.notes)

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val coverWidth  = (screenWidth * 0.58f).coerceIn(160.dp, 230.dp)
    val coverHeight = coverWidth / 0.667f

    Scaffold { _ ->
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {

            // ── Header: blurred bg + centered cover + title ───────────────────
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(coverHeight + 160.dp),
                ) {
                    AsyncImage(
                        model              = coverUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxSize()
                            .blur(28.dp),
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .shadow(30.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            AsyncImage(
                                model              = coverUrl,
                                contentDescription = null,
                                contentScale       = ContentScale.Fit,
                                modifier           = Modifier.width(coverWidth).height(coverHeight),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            mediaItem.title,
                            color      = Color.White,
                            fontSize   = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                            lineHeight = 25.sp,
                            maxLines   = 3,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        if (!authors.isNullOrEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                authors.joinToString(", "),
                                color    = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // ── Status button + "..." button (centered) ───────────────────────
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Box {
                        Button(
                            onClick        = { showStatusMenu = true },
                            colors         = ButtonDefaults.buttonColors(containerColor = ColorLivro),
                            shape          = RoundedCornerShape(4.dp),
                            border         = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.24f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(mediaItem.status.label, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                            MediaStatus.forBook().forEach { s ->
                                val selected = s == mediaItem.status
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    trailingIcon = {
                                        if (selected) Icon(Icons.Default.Check, null, tint = ColorLivro, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = { vm.setStatus(s, pages); showStatusMenu = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box {
                        Box(
                            Modifier
                                .size(44.dp)
                                .border(1.5.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
                                .clickable { showMoreMenu = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.MoreHoriz, null)
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(text = { Text("Atualizar") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { vm.refreshCache(); showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Editar progresso") }, leadingIcon = { Icon(Icons.Default.Bookmark, null) }, onClick = { pageInput = (mediaItem.currentProgress ?: 0).toString(); commentInput = ""; showPageDialog = true; showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Editar data de início") }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null) }, onClick = { showStartDatePicker = true; showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Editar data de conclusão") }, leadingIcon = { Icon(Icons.Default.EventAvailable, null) }, onClick = { showEndDatePicker = true; showMoreMenu = false })
                            DropdownMenuItem(text = { Text("Notas") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) }, onClick = { showPersonalNotes = true; showMoreMenu = false })
                            DropdownMenuItem(text = { Text(if (mediaItem.favorite) "Remover dos favoritos" else "Favoritar") }, leadingIcon = { Icon(if (mediaItem.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }, onClick = { vm.toggleFavorite(); showMoreMenu = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Remover livro", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showDelete = true; showMoreMenu = false })
                        }
                    }
                }
            }

            // Synopsis
            if (synopsis != null) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sinopse", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(synopsis, style = MaterialTheme.typography.bodyMedium, maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4)
                        TextButton(onClick = { synopsisExpanded = !synopsisExpanded }) {
                            Text(if (synopsisExpanded) "Ver menos" else "Ver mais")
                        }
                    }
                }
            }

            // Informações
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Informações", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!authors.isNullOrEmpty())   BookInfoRow("Autor", authors.joinToString(", "))
                            if (publisher != null)           BookInfoRow("Editora", publisher)
                            if (genre != null)               BookInfoRow("Gênero", genre)
                            if (pages != null)               BookInfoRow("Páginas", "$pages")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Progresso
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Progresso", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(
                            onClick        = { pageInput = (mediaItem.currentProgress ?: 0).toString(); commentInput = ""; showPageDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Editar", color = ColorLivro, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val displayedPage = (mediaItem.currentProgress ?: 0).let { p ->
                                if (pages != null) p.coerceIn(0, pages) else p
                            }
                            if (pages != null && pages > 0) {
                                LinearProgressIndicator(
                                    progress   = { (displayedPage.toFloat() / pages.toFloat()).coerceIn(0f, 1f) },
                                    modifier   = Modifier.fillMaxWidth(),
                                    color      = ColorLivro,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                )
                            }
                            val pctSuffix = if (pages != null && pages > 0) {
                                val pct = (displayedPage.toFloat() / pages.toFloat() * 100).toInt().coerceIn(0, 100)
                                " ($pct%)"
                            } else ""
                            Text(
                                "Página $displayedPage" + (pages?.let { " de $it" } ?: "") + pctSuffix,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            if (commentLines.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                commentLines.take(2).forEach { line ->
                                    Text(
                                        line,
                                        style     = MaterialTheme.typography.bodySmall,
                                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                                if (commentLines.size > 2) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Ver histórico completo",
                                        color      = ColorLivro,
                                        fontSize   = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier   = Modifier.clickable { showHistorySheet = true },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Datas
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Datas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (mediaItem.readingStartDate != null) {
                                BookInfoRow("Início", dateFormatter.format(mediaItem.readingStartDate))
                            }
                            if (mediaItem.completionDate != null) {
                                BookInfoRow("Concluído", dateFormatter.format(mediaItem.completionDate))
                            }
                            if (mediaItem.rereadingDate != null) {
                                BookInfoRow("Releitura", dateFormatter.format(mediaItem.rereadingDate))
                            }
                            BookInfoRow("Adicionado", dateFormatter.format(mediaItem.addedDate))
                            if (releaseDateMs != null) {
                                BookInfoRow("Lançamento", dateFormatter.format(java.util.Date(releaseDateMs)))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
        // ── Floating nav: back ────────────────────────────────────────────
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
        } // end Box
    }

    if (showPageDialog) {
        val pageNum = pageInput.toIntOrNull()
        val error = when {
            pageInput.isBlank()               -> null
            pageNum == null                   -> "Digite um número válido"
            pageNum < 0                       -> "Não pode ser negativo"
            pages != null && pageNum > pages  -> "Máximo: $pages páginas"
            else                              -> null
        }
        val canSave = error == null && pageInput.isNotBlank()
        AlertDialog(
            onDismissRequest = { showPageDialog = false },
            title   = { Text("Página atual") },
            text    = {
                Column {
                    OutlinedTextField(
                        value         = pageInput,
                        onValueChange = { pageInput = it },
                        label         = { Text("Página") },
                        isError       = error != null,
                        supportingText = error?.let { { Text(it) } },
                        singleLine    = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value         = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder   = { Text("Comentário (opcional)", style = MaterialTheme.typography.bodySmall) },
                        minLines      = 2,
                        maxLines      = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        pageNum?.let { vm.setProgress(it, commentInput, pages) }
                        showPageDialog = false
                    },
                ) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { showPageDialog = false }) { Text("Cancelar") } },
        )
    }

    if (showHistorySheet) {
        BookHistorySheet(
            entries    = commentLines,
            onSave     = { newEntries -> vm.saveNotes(newEntries.takeIf { it.isNotEmpty() }?.joinToString("\n")) },
            onDismiss  = { showHistorySheet = false },
        )
    }

    if (showStartDatePicker) {
        BookDatePickerDialog(
            initial    = mediaItem.readingStartDate?.time,
            onConfirm  = { vm.setStartDate(java.util.Date(it)); showStartDatePicker = false },
            onDismiss  = { showStartDatePicker = false },
        )
    }

    if (showEndDatePicker) {
        BookDatePickerDialog(
            initial    = mediaItem.completionDate?.time,
            onConfirm  = { vm.setCompletionDate(java.util.Date(it)); showEndDatePicker = false },
            onDismiss  = { showEndDatePicker = false },
        )
    }

    if (showPersonalNotes) {
        NotesDialog(
            initialText = mediaItem.personalNotes ?: "",
            onDismiss   = { showPersonalNotes = false },
            onSave      = { vm.setPersonalNotes(it) },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title   = { Text("Remover livro") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookHistorySheet(
    entries: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var list by remember { mutableStateOf(entries) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Histórico de leitura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) {
                Text(
                    "Nenhuma anotação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            } else {
                list.forEachIndexed { index, entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            entry,
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            fontStyle = FontStyle.Italic,
                            modifier  = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick  = { editingIndex = index; editingText = entry },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick  = { deletingIndex = index },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                    if (index != list.lastIndex) HorizontalDivider()
                }
            }
        }
    }

    editingIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title   = { Text("Editar anotação") },
            text    = {
                OutlinedTextField(
                    value         = editingText,
                    onValueChange = { editingText = it },
                    minLines      = 2,
                    maxLines      = 4,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingText.isNotBlank()) {
                        list = list.toMutableList().also { it[index] = editingText }
                        onSave(list)
                    }
                    editingIndex = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editingIndex = null }) { Text("Cancelar") } },
        )
    }

    deletingIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { deletingIndex = null },
            title   = { Text("Remover anotação") },
            text    = { Text("Remover esta anotação do histórico?") },
            confirmButton = {
                Button(
                    onClick = {
                        list = list.toMutableList().also { it.removeAt(index) }
                        onSave(list)
                        deletingIndex = null
                    },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { deletingIndex = null }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDatePickerDialog(initial: Long?, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun BookInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
