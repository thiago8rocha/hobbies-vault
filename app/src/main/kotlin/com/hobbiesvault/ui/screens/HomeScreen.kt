package com.hobbiesvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.hobbiesvault.ui.navigation.Routes
import com.hobbiesvault.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.Date

class HomeViewModel : ViewModel() {
    val allItems = DB.repo.watchAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun lastActiveGame(items: List<MediaItem>, filter: (MediaItem) -> Boolean): MediaItem? {
    val candidates = items.filter { it.type == MediaType.GAME && filter(it) }
    val active = candidates.filter { it.status == MediaStatus.PLAYING || it.status == MediaStatus.REPLAYING }
    if (active.isNotEmpty()) return active.maxByOrNull { it.addedDate ?: Date(0) }
    val done = candidates.filter { it.status in listOf(MediaStatus.PLATINUM, MediaStatus.COMPLETED, MediaStatus.FINISHED) }
    return done.maxByOrNull { it.addedDate ?: Date(0) }
}

private fun lastActive(items: List<MediaItem>, types: List<MediaType>, statuses: Set<MediaStatus>): MediaItem? =
    items.filter { it.type in types && it.status in statuses }
         .maxByOrNull { it.addedDate ?: Date(0) }

/** Clareia a cor de destaque em tema escuro para manter contraste legível sobre fundos tingidos com a mesma cor. */
private fun readableAccent(color: Color, darkTheme: Boolean): Color =
    if (darkTheme) lerp(color, Color.White, 0.35f) else color

private val CardShape = RoundedCornerShape(12.dp)
private val HeaderShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: HomeViewModel = viewModel()) {
    val allItems by vm.allItems.collectAsStateWithLifecycle()

    val steamItem       = remember(allItems) { lastActiveGame(allItems) { it.console?.isSteam == true || it.console == GameConsole.PC } }
    val nintendoItem    = remember(allItems) { lastActiveGame(allItems) { it.console?.isNintendo == true } }
    val playstationItem = remember(allItems) { lastActiveGame(allItems) { it.console?.isPlayStation == true } }

    val filmeItem = remember(allItems) { lastActive(allItems, listOf(MediaType.MOVIE), setOf(MediaStatus.WATCHED, MediaStatus.REWATCHING)) }
    val serieItem = remember(allItems) { lastActive(allItems, listOf(MediaType.SERIES), setOf(MediaStatus.WATCHING, MediaStatus.REWATCHING, MediaStatus.HISTORY)) }
    val mangaItem = remember(allItems) { lastActive(allItems, listOf(MediaType.MANGA, MediaType.WEBTOON), setOf(MediaStatus.READING, MediaStatus.REREADING, MediaStatus.READ)) }
    val livroItem = remember(allItems) { lastActive(allItems, listOf(MediaType.BOOK), setOf(MediaStatus.READ, MediaStatus.READING, MediaStatus.REREADING)) }

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HobbiesVault") },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.CALENDAR) }) { Icon(Icons.Outlined.CalendarMonth, "") }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text    = { Text("Configurações") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { showMenu = false; navController.navigate(Routes.SETTINGS) },
                            )
                            DropdownMenuItem(
                                text    = { Text("Status") },
                                leadingIcon = { Icon(Icons.Outlined.BarChart, null) },
                                onClick = { showMenu = false; navController.navigate(Routes.STATS) },
                            )
                            DropdownMenuItem(
                                text    = { Text("Histórico") },
                                leadingIcon = { Icon(Icons.Outlined.History, null) },
                                onClick = { showMenu = false; navController.navigate(Routes.HISTORY) },
                            )
                            DropdownMenuItem(
                                text    = { Text("Sobre") },
                                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                onClick = { showMenu = false; navController.navigate(Routes.ABOUT) },
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Games card ────────────────────────────────────────────────────
            GamesCard(
                steam       = steamItem,
                nintendo    = nintendoItem,
                playstation = playstationItem,
                onTap       = { navController.navigate(Routes.GAMES) },
                modifier    = Modifier.padding(horizontal = 16.dp),
            )

            // ── Filmes + Séries ───────────────────────────────────────────────
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryCard(
                    label    = "Filmes",
                    color    = ColorFilme,
                    lastItem = filmeItem,
                    onTap    = { navController.navigate(Routes.FILMS) },
                    modifier = Modifier.weight(1f),
                )
                CategoryCard(
                    label    = "Séries",
                    color    = ColorSerie,
                    lastItem = serieItem,
                    onTap    = { navController.navigate(Routes.SERIES) },
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Mangás + Livros ───────────────────────────────────────────────
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryCard(
                    label    = "Mangás",
                    color    = ColorManga,
                    lastItem = mangaItem,
                    onTap    = { navController.navigate(Routes.MANGA) },
                    modifier = Modifier.weight(1f),
                )
                CategoryCard(
                    label    = "Livros",
                    color    = ColorLivro,
                    lastItem = livroItem,
                    onTap    = { navController.navigate(Routes.BOOKS) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── GamesCard ─────────────────────────────────────────────────────────────────

@Composable
private fun GamesCard(
    steam: MediaItem?,
    nintendo: MediaItem?,
    playstation: MediaItem?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface   = MaterialTheme.colorScheme.surface
    val outline   = MaterialTheme.colorScheme.outline
    val darkTheme = AppThemeController.darkMode
    val headerAccent = readableAccent(ColorJogo, darkTheme)

    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(surface)
            .border(1.dp, outline.copy(alpha = 0.15f), CardShape)
            .clickable(onClick = onTap)
    ) {
        Box(
            Modifier.fillMaxWidth().clip(HeaderShape).background(ColorJogo.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Jogos", color = headerAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth()) {
            PlatformColumn("Steam",        steam,       ColorSteam.copy(0.12f), readableAccent(Color(0xFF66C0F4), darkTheme), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(0.15f)))
            PlatformColumn("Nintendo",    nintendo,    Color(0xFFE4000F).copy(0.12f), readableAccent(Color(0xFFE4000F), darkTheme), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(0.15f)))
            PlatformColumn("PlayStation", playstation, Color(0xFF00439C).copy(0.12f), readableAccent(Color(0xFF00439C), darkTheme), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlatformColumn(
    label: String,
    item: MediaItem?,
    bg: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.typography
    Column(modifier.background(bg), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().background(accentColor.copy(0.12f)).padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
        if (item != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                if (item.coverUrl != null) {
                    AsyncImage(model = item.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(accentColor.copy(0.15f)))
                }
            }
            Text(
                item.title,
                style     = theme.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 8.dp),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("Nada\nainda", fontSize = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }
}

// ── CategoryCard ──────────────────────────────────────────────────────────────

@Composable
private fun CategoryCard(
    label: String,
    color: Color,
    lastItem: MediaItem?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme     = MaterialTheme.typography
    val surface   = MaterialTheme.colorScheme.surface
    val darkTheme = AppThemeController.darkMode

    Column(
        modifier
            .clip(CardShape)
            .background(surface)
            .border(1.dp, color.copy(alpha = 0.25f), CardShape)
            .clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().clip(HeaderShape).background(color.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = readableAccent(color, darkTheme), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (lastItem != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                if (lastItem.coverUrl != null) {
                    AsyncImage(model = lastItem.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(color.copy(0.15f)))
                }
            }
            Text(
                lastItem.title,
                style     = theme.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Nada ainda", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }
}

