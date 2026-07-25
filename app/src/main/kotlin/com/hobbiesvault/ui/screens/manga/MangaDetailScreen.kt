package com.hobbiesvault.ui.screens.manga

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.hobbiesvault.model.MangaReview
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.components.NotesDialog
import com.hobbiesvault.ui.theme.ColorManga
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDetailViewModel : ViewModel() {
    var mediaItem by mutableStateOf<MediaItem?>(null)
    var cache by mutableStateOf<Map<String, Any?>?>(null)
    var loadingCache by mutableStateOf(true)
    var reviewHistory by mutableStateOf<List<MangaReview>>(emptyList())
    private var initialized = false

    fun init(initial: MediaItem) {
        if (initialized) return
        initialized = true
        mediaItem = initial
        viewModelScope.launch {
            cache = MediaCacheService.load(initial)
            loadingCache = false
        }
        initial.id?.let { id ->
            viewModelScope.launch { reviewHistory = DB.repo.mangaReviewHistory(id) }
        }
        MediaCacheService.doubleCheck(initial) {
            val updated = MediaCacheService.load(initial)
            if (updated != null) cache = updated
        }
    }

    fun setStatus(newStatus: MediaStatus) {
        val current = mediaItem ?: return
        viewModelScope.launch {
            // Ao começar uma releitura, a avaliação/resenha anterior vira histórico —
            // o usuário está formando um novo julgamento, não editando o antigo.
            if (newStatus == MediaStatus.REREADING && current.status == MediaStatus.READ) {
                current.id?.let { id ->
                    DB.repo.archiveMangaReview(
                        mediaItemId = id,
                        rating      = current.rating,
                        reviewTitle = current.reviewTitle,
                        reviewText  = current.notes,
                        completedAt = current.completionDate ?: java.util.Date(),
                    )
                    reviewHistory = DB.repo.mangaReviewHistory(id)
                }
            }
            val clearingForReread = newStatus == MediaStatus.REREADING && current.status == MediaStatus.READ
            val updated = current.copy(
                status = newStatus,
                completionDate = if (newStatus == MediaStatus.READ) current.completionDate ?: java.util.Date() else current.completionDate,
                rating      = if (clearingForReread) null else current.rating,
                reviewTitle = if (clearingForReread) null else current.reviewTitle,
                notes       = if (clearingForReread) null else current.notes,
                rereadingDate = if (clearingForReread) java.util.Date() else current.rereadingDate,
            )
            mediaItem = updated
            DB.repo.update(updated)
        }
    }

    fun setProgress(chapter: Int, totalChapters: Int?) {
        val current = mediaItem ?: return
        val clamped = totalChapters?.let { chapter.coerceIn(0, it) } ?: chapter.coerceAtLeast(0)
        val updated = current.copy(currentProgress = clamped)
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

    fun savePersonal(rating: Double?, reviewTitle: String?, notes: String?) {
        val current = mediaItem ?: return
        val updated = current.copy(
            rating      = rating,
            reviewTitle = reviewTitle?.takeIf { it.isNotBlank() },
            notes       = notes?.takeIf { it.isNotBlank() },
        )
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
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
fun MangaDetailScreen(
    navController: NavController,
    initialItem: MediaItem,
    vm: MangaDetailViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.init(initialItem) }

    val mediaItem = vm.mediaItem ?: initialItem
    val cache     = vm.cache

    var showDelete       by remember { mutableStateOf(false) }
    var showStatusMenu   by remember { mutableStateOf(false) }
    var showMoreMenu     by remember { mutableStateOf(false) }
    var showPersonalNotes by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }
    var chapterInput     by remember { mutableStateOf("") }
    var editingPersonal  by remember { mutableStateOf(false) }
    var pendingRating    by remember { mutableStateOf(0) }
    var pendingReviewTitle by remember { mutableStateOf("") }
    var pendingNotes     by remember { mutableStateOf("") }
    val dateFormatter    = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    val coverUrl            = cache?.get("coverUrl") as? String ?: mediaItem.coverUrl
    val synopsis            = cache?.get("synopsis") as? String
    val chapters            = (cache?.get("chapters") as? Number)?.toInt() ?: mediaItem.totalProgress
    val volumes             = (cache?.get("volumes") as? Number)?.toInt()
    val serializationStatus = cache?.get("serializationStatus") as? String
    val format              = cache?.get("format") as? String
    val genres              = (cache?.get("genres") as? List<*>)?.filterIsInstance<String>()
    val authors             = (cache?.get("authors") as? List<*>)?.filterIsInstance<String>()
    val startDateMs         = (cache?.get("startDateMs") as? Number)?.toLong()
    val endDateMs           = (cache?.get("endDateMs")   as? Number)?.toLong()
    @Suppress("UNCHECKED_CAST")
    val staffList           = cache?.get("staff")       as? List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val characters          = cache?.get("characters")  as? List<Map<String, Any?>>
    val synonyms             = (cache?.get("synonyms") as? List<*>)?.filterIsInstance<String>()
        ?.filter { it.isNotBlank() && !it.equals(mediaItem.title, ignoreCase = true) }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val coverWidth  = (screenWidth * 0.58f).coerceIn(160.dp, 230.dp)
    val coverHeight = coverWidth / 0.7f

    Scaffold { _ ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {

                // ── Header: blurred bg + centered cover + title ───────────────
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

                // ── Status button + "..." button (centered) ───────────────────
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
                                colors         = ButtonDefaults.buttonColors(containerColor = ColorManga),
                                shape          = RoundedCornerShape(4.dp),
                                border         = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.24f)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(mangaStatusLabel(mediaItem.status), fontSize = 13.sp)
                            }
                            DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                                MediaStatus.forManga()
                                    .filter { it != MediaStatus.READ || serializationStatus != "Em andamento" }
                                    .forEach { s ->
                                        DropdownMenuItem(
                                            text    = { Text(mangaStatusLabel(s)) },
                                            onClick = { vm.setStatus(s); showStatusMenu = false },
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
                                DropdownMenuItem(
                                    text          = { Text("Atualizar") },
                                    leadingIcon   = { Icon(Icons.Default.Refresh, null) },
                                    onClick       = { vm.refreshCache(); showMoreMenu = false },
                                )
                                DropdownMenuItem(
                                    text          = { Text("Editar progresso") },
                                    leadingIcon   = { Icon(Icons.Default.Bookmark, null) },
                                    onClick       = { chapterInput = (mediaItem.currentProgress ?: 0).toString(); showChapterDialog = true; showMoreMenu = false },
                                )
                                DropdownMenuItem(
                                    text          = { Text("Notas") },
                                    leadingIcon   = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                                    onClick       = { showPersonalNotes = true; showMoreMenu = false },
                                )
                                DropdownMenuItem(
                                    text          = { Text(if (mediaItem.favorite) "Remover dos favoritos" else "Favoritar") },
                                    leadingIcon   = { Icon(if (mediaItem.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                                    onClick       = { vm.toggleFavorite(); showMoreMenu = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text          = { Text("Remover mangá", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon   = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick       = { showDelete = true; showMoreMenu = false },
                                )
                            }
                        }
                    }
                }

                // ── Sinopse ──────────────────────────────────────────────────
                if (synopsis != null) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            MangaSectionTitle("Sinopse")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                synopsis,
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                lineHeight = 22.sp,
                                maxLines = if (synopsisExpanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (synopsisExpanded) "Ver menos" else "Ver mais",
                                color      = ColorManga,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.clickable { synopsisExpanded = !synopsisExpanded },
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Sinônimos ────────────────────────────────────────────────
                if (!synonyms.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            MangaSectionTitle("Sinônimos")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                synonyms.joinToString(", "),
                                style      = MaterialTheme.typography.bodyMedium,
                                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Progresso de capítulos ────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            MangaSectionTitle("Progresso")
                            TextButton(
                                onClick        = { chapterInput = (mediaItem.currentProgress ?: 0).toString(); showChapterDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("Editar", color = ColorManga, fontSize = 12.sp)
                            }
                        }
                        val displayedProgress = (mediaItem.currentProgress ?: 0).let { p ->
                            if (chapters != null) p.coerceIn(0, chapters) else p
                        }
                        if (chapters != null && chapters > 0) {
                            LinearProgressIndicator(
                                progress   = { (displayedProgress.toFloat() / chapters.toFloat()).coerceIn(0f, 1f) },
                                modifier   = Modifier.fillMaxWidth(),
                                color      = ColorManga,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            )
                        }
                        Text(
                            "Capítulo $displayedProgress" + (chapters?.let { " de $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Informações ───────────────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        MangaSectionTitle("Informações")
                        Spacer(Modifier.height(10.dp))
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (format != null)            MangaInfoRow("Formato", format)
                                if (!genres.isNullOrEmpty())   MangaInfoRow("Gênero", genres.take(3).joinToString(", "))
                                if (volumes != null)           MangaInfoRow("Volumes", "$volumes")
                                MangaInfoRow("Capítulos", chapters?.toString() ?: "Em andamento")
                                if (serializationStatus != null) MangaInfoRow("Status", serializationStatus)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Datas ─────────────────────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        MangaSectionTitle("Datas")
                        Spacer(Modifier.height(10.dp))
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (startDateMs != null) MangaInfoRow("Início", dateFormatter.format(java.util.Date(startDateMs)))
                                if (endDateMs != null)   MangaInfoRow("Conclusão", dateFormatter.format(java.util.Date(endDateMs)))
                                MangaInfoRow("Adicionado", dateFormatter.format(mediaItem.addedDate))
                                if (mediaItem.completionDate != null) {
                                    MangaInfoRow("Lido em", dateFormatter.format(mediaItem.completionDate))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Avaliação (só disponível quando Lido) ─────────────────────
                if (mediaItem.status == MediaStatus.READ) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                MangaSectionTitle("Avaliação")
                                if (!editingPersonal) {
                                    TextButton(
                                        onClick        = {
                                            pendingRating      = (mediaItem.rating ?: 0.0).toInt()
                                            pendingReviewTitle = mediaItem.reviewTitle ?: ""
                                            pendingNotes       = mediaItem.notes ?: ""
                                            editingPersonal = true
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Editar", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    if (editingPersonal) {
                                        StarRatingPicker(rating = pendingRating, onRatingChange = { pendingRating = it })
                                    } else if (mediaItem.rating != null) {
                                        StarRatingDisplay(rating = mediaItem.rating.toInt())
                                    } else {
                                        Text(
                                            "Nenhuma avaliação ainda",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── Resenha ────────────────────────────────────────────────
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            MangaSectionTitle("Resenha")
                            Spacer(Modifier.height(10.dp))
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (editingPersonal) {
                                        OutlinedTextField(
                                            value         = pendingReviewTitle,
                                            onValueChange = { pendingReviewTitle = it },
                                            placeholder   = { Text("Título (opcional)", style = MaterialTheme.typography.bodySmall) },
                                            singleLine    = true,
                                            modifier      = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value         = pendingNotes,
                                            onValueChange = { pendingNotes = it },
                                            placeholder   = { Text("Impressões, spoilers…", style = MaterialTheme.typography.bodySmall) },
                                            minLines      = 3,
                                            maxLines      = 6,
                                            modifier      = Modifier.fillMaxWidth(),
                                        )
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            OutlinedButton(
                                                onClick  = { editingPersonal = false },
                                                modifier = Modifier.weight(1f),
                                                shape    = RoundedCornerShape(12.dp),
                                            ) { Text("Cancelar") }
                                            Button(
                                                onClick = {
                                                    vm.savePersonal(
                                                        rating      = if (pendingRating > 0) pendingRating.toDouble() else null,
                                                        reviewTitle = pendingReviewTitle,
                                                        notes       = pendingNotes,
                                                    )
                                                    editingPersonal = false
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape    = RoundedCornerShape(12.dp),
                                                colors   = ButtonDefaults.buttonColors(containerColor = ColorManga),
                                            ) { Text("Salvar") }
                                        }
                                    } else {
                                        if (!mediaItem.reviewTitle.isNullOrBlank()) {
                                            Text(mediaItem.reviewTitle!!, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        if (!mediaItem.notes.isNullOrBlank()) {
                                            Text(
                                                mediaItem.notes!!,
                                                style    = MaterialTheme.typography.bodyMedium,
                                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                                fontStyle = FontStyle.Italic,
                                            )
                                        }
                                        if (mediaItem.reviewTitle.isNullOrBlank() && mediaItem.notes.isNullOrBlank()) {
                                            Text(
                                                "Nenhuma resenha ainda",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── Leituras anteriores (histórico de releituras) ─────────────
                if (vm.reviewHistory.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            MangaSectionTitle("Leituras anteriores")
                            Spacer(Modifier.height(10.dp))
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    vm.reviewHistory.forEachIndexed { index, review ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                dateFormatter.format(review.completedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            )
                                            if (review.rating != null) {
                                                StarRatingDisplay(rating = review.rating.toInt())
                                            }
                                            if (!review.reviewTitle.isNullOrBlank()) {
                                                Text(review.reviewTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            }
                                            if (!review.reviewText.isNullOrBlank()) {
                                                Text(
                                                    review.reviewText,
                                                    style     = MaterialTheme.typography.bodyMedium,
                                                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                                    fontStyle = FontStyle.Italic,
                                                )
                                            }
                                        }
                                        if (index != vm.reviewHistory.lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── Personagens ───────────────────────────────────────────────
                if (!characters.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            MangaSectionTitle("Personagens")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(characters) { char ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(72.dp),
                                    ) {
                                        AsyncImage(
                                            model              = char["photoUrl"] as? String,
                                            contentDescription = null,
                                            contentScale       = ContentScale.Crop,
                                            modifier           = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            char["name"] as? String ?: "",
                                            style    = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── Staff ─────────────────────────────────────────────────────
                if (!staffList.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            MangaSectionTitle("Staff")
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(staffList) { member ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(72.dp),
                                    ) {
                                        AsyncImage(
                                            model              = member["photoUrl"] as? String,
                                            contentDescription = null,
                                            contentScale       = ContentScale.Crop,
                                            modifier           = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape),
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            member["name"] as? String ?: "",
                                            style    = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            member["role"] as? String ?: "",
                                            style  = MaterialTheme.typography.labelSmall,
                                            color  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
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
        }
    }

    if (showChapterDialog) {
        val chapterNum = chapterInput.toIntOrNull()
        val chapterError = when {
            chapterInput.isBlank()                        -> null
            chapterNum == null                             -> "Digite um número válido"
            chapterNum < 0                                  -> "Não pode ser negativo"
            chapters != null && chapterNum > chapters       -> "Máximo: $chapters capítulos"
            else                                             -> null
        }
        val canSaveChapter = chapterError == null && chapterInput.isNotBlank()
        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title   = { Text("Capítulo atual") },
            text    = {
                OutlinedTextField(
                    value         = chapterInput,
                    onValueChange = { chapterInput = it },
                    label         = { Text("Capítulo") },
                    isError       = chapterError != null,
                    supportingText = chapterError?.let { { Text(it) } },
                    singleLine    = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = canSaveChapter,
                    onClick = {
                        chapterNum?.let { vm.setProgress(it, chapters) }
                        showChapterDialog = false
                    },
                ) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { showChapterDialog = false }) { Text("Cancelar") } },
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
            title   = { Text("Remover mangá") },
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

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun StarRatingPicker(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { star ->
            Icon(
                if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint     = Color(0xFFFFC107),
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onRatingChange(star) },
            )
        }
    }
}

@Composable
private fun StarRatingDisplay(rating: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { star ->
            Icon(
                if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint     = Color(0xFFFFC107),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

fun mangaStatusLabel(status: MediaStatus): String = when (status) {
    MediaStatus.QUEUED  -> "Quero Ler"
    MediaStatus.ON_HOLD -> "Em Hiato"
    else                -> status.label
}

@Composable
private fun MangaSectionTitle(text: String) {
    Text(
        text,
        style         = MaterialTheme.typography.titleSmall,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 0.3.sp,
    )
}

@Composable
private fun MangaInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

