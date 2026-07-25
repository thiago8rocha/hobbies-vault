package com.hobbiesvault.ui.screens.films

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.service.MediaCacheService
import com.hobbiesvault.ui.theme.ColorFilme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilmDetailViewModel : ViewModel() {
    var mediaItem by mutableStateOf<MediaItem?>(null)
    var cache by mutableStateOf<Map<String, Any?>?>(null)
    var loadingCache by mutableStateOf(true)
    private var initialized = false

    fun init(initial: MediaItem) {
        if (initialized) return
        initialized = true
        mediaItem = initial
        viewModelScope.launch {
            val loaded = MediaCacheService.load(initial)
            android.util.Log.d("FilmDetail", "init load: cache=${if (loaded != null) "found keys=${loaded.keys}" else "null"}")
            cache = loaded
            loadingCache = false
        }
        MediaCacheService.doubleCheck(initial) {
            val updated = MediaCacheService.load(initial)
            android.util.Log.d("FilmDetail", "doubleCheck callback: updated=${if (updated != null) "found keys=${updated.keys}" else "null"}")
            if (updated != null) cache = updated
        }
    }

    fun setStatus(newStatus: MediaStatus) {
        val current = mediaItem ?: return
        val updated = current.copy(
            status = newStatus,
            completionDate = if (newStatus == MediaStatus.WATCHED) Date() else current.completionDate,
        )
        mediaItem = updated
        viewModelScope.launch { DB.repo.update(updated) }
    }

    fun toggleFavorite() {
        val current = mediaItem ?: return
        val updated = current.copy(favorite = !current.favorite)
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

@Composable
fun FilmDetailScreen(
    navController: NavController,
    initialItem: MediaItem,
    vm: FilmDetailViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.init(initialItem) }

    val mediaItem = vm.mediaItem ?: initialItem
    val cache = vm.cache

    var showDelete        by remember { mutableStateOf(false) }
    var showStatusMenu    by remember { mutableStateOf(false) }
    var showMoreMenu      by remember { mutableStateOf(false) }
    var synopsisExpanded  by remember { mutableStateOf(false) }
    var showCast          by remember { mutableStateOf(false) }
    var showCrew          by remember { mutableStateOf(false) }
    var showRelated       by remember { mutableStateOf(false) }

    val statusLabel = when (mediaItem.status) {
        MediaStatus.WATCHED         -> "Assistido"
        MediaStatus.REWATCHING      -> "Reassistindo"
        MediaStatus.WAITING_RELEASE -> "Aguardando Lançamento"
        else                        -> "Quero Assistir"
    }
    val synopsis    = cache?.get("synopsis") as? String
    val posterUrl   = cache?.get("posterUrl") as? String ?: mediaItem.coverUrl
    val backdropUrl = cache?.get("backdropUrl") as? String
    val runtime     = (cache?.get("runtimeMinutes") as? Double)?.toInt()
    val releaseDateMs = (cache?.get("releaseDate") as? Double)?.toLong()
    val genres      = (cache?.get("genres") as? List<*>)?.filterIsInstance<String>()
    val cast        = (cache?.get("cast") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val crew        = (cache?.get("crew") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
    val related     = (cache?.get("related") as? List<*>)?.filterIsInstance<Map<String, Any?>>()

    val providers: List<Map<String, Any?>> = run {
        val raw = (cache?.get("providers") as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
        val seen = mutableSetOf<String>()
        raw.filter { p ->
            val nome = (p["nome"] as? String ?: p["name"] as? String ?: "").lowercase()
            val base = listOf("netflix", "prime video", "disney+", "max", "apple tv+",
                "globoplay", "paramount+", "crunchyroll", "telecine", "star+", "claro tv+")
                .firstOrNull { nome.contains(it) } ?: nome
            seen.add(base)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    val dataLabel = releaseDateMs?.let {
        val d = Date(it)
        dateFormatter.format(d)
    } ?: ""

    val duracaoLabel = runtime?.let {
        val h = it / 60; val m = it % 60
        when { h == 0 -> "${m}min"; m == 0 -> "${h}h"; else -> "${h}h${m}min" }
    } ?: ""

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val coverWidth  = (screenWidth * 0.58f).coerceIn(160.dp, 230.dp)
    val coverHeight = coverWidth / 0.714f
    val bgUrl       = backdropUrl ?: posterUrl

    Scaffold { _ ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {

                // ── Header: capa centralizada + título (formato jogos) ─────────
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(coverHeight + 160.dp)
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
                                    model              = posterUrl,
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
                        // Status button
                        Box {
                            Surface(
                                color   = ColorFilme,
                                shape   = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .widthIn(min = 150.dp, max = 260.dp)
                                    .clickable { showStatusMenu = true },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.UnfoldMore, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        statusLabel,
                                        color      = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 14.sp,
                                    )
                                }
                            }
                            DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                                FilmStatusMenuItem(Icons.Default.CheckCircle, "Assistido", mediaItem.status == MediaStatus.WATCHED, ColorFilme) { vm.setStatus(MediaStatus.WATCHED); showStatusMenu = false }
                                FilmStatusMenuItem(Icons.Default.Replay, "Reassistindo", mediaItem.status == MediaStatus.REWATCHING, ColorFilme) { vm.setStatus(MediaStatus.REWATCHING); showStatusMenu = false }
                                FilmStatusMenuItem(Icons.Default.Queue, "Quero Assistir", mediaItem.status == MediaStatus.QUEUED, ColorFilme) { vm.setStatus(MediaStatus.QUEUED); showStatusMenu = false }
                                FilmStatusMenuItem(Icons.Default.Schedule, "Aguardando Lançamento", mediaItem.status == MediaStatus.WAITING_RELEASE, ColorFilme) { vm.setStatus(MediaStatus.WAITING_RELEASE); showStatusMenu = false }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // "..." button
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
                                DropdownMenuItem(text = { Text("Filmes Relacionados") }, leadingIcon = { Icon(Icons.Default.MovieCreation, null) }, onClick = { showRelated = !showRelated; showMoreMenu = false })
                                DropdownMenuItem(text = { Text(if (mediaItem.favorite) "Remover dos favoritos" else "Favoritar") }, leadingIcon = { Icon(if (mediaItem.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }, onClick = { vm.toggleFavorite(); showMoreMenu = false })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Remover filme", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showDelete = true; showMoreMenu = false })
                            }
                        }
                    }
                }

                // ── Gêneros ──────────────────────────────────────────────────
                if (!genres.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            FilmSectionTitle("Gêneros")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                genres.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                // ── Meta row: data + duração ──────────────────────────────────
                if (dataLabel.isNotEmpty() || duracaoLabel.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (dataLabel.isNotEmpty()) {
                                FilmMetaItem(Icons.Default.CalendarToday, dataLabel)
                            }
                            if (duracaoLabel.isNotEmpty()) {
                                FilmMetaItem(Icons.Default.Schedule, duracaoLabel)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }

                // ── Sinopse ──────────────────────────────────────────────────
                if (synopsis != null) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            FilmSectionTitle("Sinopse")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                synopsis,
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                lineHeight = 22.sp,
                                maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (synopsisExpanded) "Ver menos" else "Ver mais",
                                color      = ColorFilme,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.clickable { synopsisExpanded = !synopsisExpanded },
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                if (vm.loadingCache) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ColorFilme)
                        }
                    }
                } else {

                    // ── Elenco ───────────────────────────────────────────────
                    if (!cast.isNullOrEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                FilmSectionTitle("Elenco")
                                Spacer(Modifier.height(10.dp))
                                (if (showCast) cast else cast.take(5)).forEach { p ->
                                    FilmPersonRow(
                                        name     = p["name"] as? String ?: p["nome"] as? String ?: "",
                                        sub      = p["character"] as? String ?: p["personagem"] as? String ?: "",
                                        photoUrl = p["photoUrl"] as? String ?: p["fotoUrl"] as? String,
                                    )
                                }
                                if (cast.size > 5) {
                                    Text(
                                        if (showCast) "Ver menos" else "Ver todos (${cast.size})",
                                        color      = ColorFilme,
                                        fontSize   = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier   = Modifier
                                            .clickable { showCast = !showCast }
                                            .padding(top = 2.dp, bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ── Equipe Técnica ────────────────────────────────────────
                    if (!crew.isNullOrEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                FilmSectionTitle("Equipe Técnica")
                                Spacer(Modifier.height(10.dp))
                                (if (showCrew) crew else crew.take(5)).forEach { p ->
                                    FilmPersonRow(
                                        name     = p["name"] as? String ?: p["nome"] as? String ?: "",
                                        sub      = p["role"] as? String ?: p["funcao"] as? String ?: "",
                                        photoUrl = p["photoUrl"] as? String ?: p["fotoUrl"] as? String,
                                    )
                                }
                                if (crew.size > 5) {
                                    Text(
                                        if (showCrew) "Ver menos" else "Ver todos (${crew.size})",
                                        color      = ColorFilme,
                                        fontSize   = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier   = Modifier
                                            .clickable { showCrew = !showCrew }
                                            .padding(top = 2.dp, bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ── Onde Assistir ─────────────────────────────────────────
                    if (providers.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                FilmSectionTitle("Onde Assistir")
                                Spacer(Modifier.height(12.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    providers.forEach { p ->
                                        val name    = p["nome"] as? String ?: p["name"] as? String ?: ""
                                        val logoUrl = p["logoUrl"] as? String
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (logoUrl != null) {
                                                AsyncImage(
                                                    model              = logoUrl,
                                                    contentDescription = name,
                                                    contentScale       = ContentScale.Crop,
                                                    modifier           = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(6.dp)),
                                                )
                                            }
                                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }

                // ── Filmes Relacionados (toggle) ──────────────────────────────
                if (showRelated && !related.isNullOrEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            FilmSectionTitle("Filmes Relacionados")
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                related.forEach { rel ->
                                    val title     = rel["title"] as? String ?: rel["titulo"] as? String ?: ""
                                    val relPoster = rel["posterUrl"] as? String
                                    val year      = (rel["year"] as? Double)?.toInt() ?: (rel["ano"] as? Double)?.toInt()
                                    Column(Modifier.width(90.dp)) {
                                        AsyncImage(
                                            model              = relPoster,
                                            contentDescription = title,
                                            contentScale       = ContentScale.Crop,
                                            modifier           = Modifier
                                                .width(90.dp)
                                                .height(130.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                        )
                                        Spacer(Modifier.height(5.dp))
                                        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (year != null) Text("$year", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
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

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title   = { Text("Remover filme") },
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
private fun FilmStatusMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
        trailingIcon = { if (selected) Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun FilmSectionTitle(text: String) {
    Text(
        text,
        style         = MaterialTheme.typography.titleSmall,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 0.3.sp,
    )
}

@Composable
private fun FilmMetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = c, modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilmPersonRow(name: String, sub: String, photoUrl: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ColorFilme.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl, contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.38f))
            }
        }
        Column {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), fontSize = 11.sp)
            }
        }
    }
}

