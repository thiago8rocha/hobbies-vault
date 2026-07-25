package com.hobbiesvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CalendarViewModel : ViewModel() {
    val upcoming = DB.repo.watchAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController, vm: CalendarViewModel = viewModel()) {
    val allItems by vm.upcoming.collectAsStateWithLifecycle()

    val now = remember { Calendar.getInstance() }
    var selectedYear  by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var typeFilter    by remember { mutableStateOf<MediaType?>(null) }

    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    fun prevMonth() {
        if (selectedMonth == 0) { selectedMonth = 11; selectedYear-- }
        else selectedMonth--
    }
    fun nextMonth() {
        if (selectedMonth == 11) { selectedMonth = 0; selectedYear++ }
        else selectedMonth++
    }

    // Items with WAITING_RELEASE status
    val upcoming = remember(allItems, selectedYear, selectedMonth, typeFilter) {
        allItems.filter { item ->
            if (item.status != MediaStatus.WAITING_RELEASE) return@filter false
            if (typeFilter != null && item.type != typeFilter) return@filter false
            val d = item.releaseDate ?: return@filter false
            val cal = Calendar.getInstance().also { c -> c.time = d }
            cal.get(Calendar.YEAR) == selectedYear && cal.get(Calendar.MONTH) == selectedMonth
        }.sortedBy { it.releaseDate }
    }

    val today = remember { Date() }

    // Divide os lançamentos do mês selecionado em blocos semanais fixos por dia do
    // mês (01-07, 08-14, 15-21, 22-28, 29-fim), em vez de uma lista única.
    val weekBuckets = remember(upcoming, selectedYear, selectedMonth) {
        val daysInMonth = Calendar.getInstance()
            .apply { set(selectedYear, selectedMonth, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)
        val buckets = mutableListOf<Pair<String, List<MediaItem>>>()
        var start = 1
        while (start <= daysInMonth) {
            val end = minOf(start + 6, daysInMonth)
            val itemsInWeek = upcoming.filter { item ->
                val d = item.releaseDate ?: return@filter false
                val day = Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_MONTH)
                day in start..end
            }
            if (itemsInWeek.isNotEmpty()) {
                buckets.add("%02d a %02d".format(start, end) to itemsInWeek)
            }
            start += 7
        }
        buckets
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Lançamentos") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            // Month navigator
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { prevMonth() }) {
                        Icon(Icons.Default.ChevronLeft, null)
                    }
                    Text(
                        "${months[selectedMonth]} $selectedYear",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { nextMonth() }) {
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }

            // Type filter chips
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick  = { typeFilter = null },
                        label    = { Text("Todos") },
                        shape    = RoundedCornerShape(4.dp),
                    )
                    MediaType.entries.forEach { t ->
                        FilterChip(
                            selected = typeFilter == t,
                            onClick  = { typeFilter = if (typeFilter == t) null else t },
                            label    = { Text(t.labelPt) },
                            shape    = RoundedCornerShape(4.dp),
                        )
                    }
                }
            }

            if (upcoming.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nenhum lançamento em ${months[selectedMonth]}", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Adicione títulos com status 'Aguardando lançamento'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            } else {
                weekBuckets.forEach { (weekLabel, weekItems) ->
                    item {
                        Text(
                            "Semana de $weekLabel",
                            style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    items(weekItems, key = { it.id ?: it.title + weekLabel }) { item ->
                        ReleaseTile(item = item, dateFmt = dateFmt, today = today, onClick = { navigateToDetail(navController, item) })
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ReleaseTile(
    item: MediaItem,
    dateFmt: SimpleDateFormat,
    today: Date,
    onClick: () -> Unit,
) {
    val releaseDate = item.releaseDate
    val daysUntil = releaseDate?.let { d ->
        TimeUnit.MILLISECONDS.toDays(d.time - today.time)
    }
    val isUrgent = daysUntil != null && daysUntil in 0..7
    val isPast   = daysUntil != null && daysUntil < 0
    val typeColor = item.type.color

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cover
        Box(
            Modifier
                .width(44.dp)
                .height(62.dp)
                .background(typeColor.copy(alpha = 0.15f)),
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

        // Info
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                item.type.labelPt,
                style = MaterialTheme.typography.bodySmall,
                color = typeColor,
                fontSize = 11.sp,
            )
            if (releaseDate != null) {
                Text(
                    dateFmt.format(releaseDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        // Days badge
        if (daysUntil != null) {
            Surface(
                color = when {
                    isPast   -> MaterialTheme.colorScheme.surfaceVariant
                    isUrgent -> Color(0xFFFF9800).copy(alpha = 0.15f)
                    else     -> MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = when {
                        isPast          -> "Lançado"
                        daysUntil == 0L -> "Hoje"
                        daysUntil == 1L -> "Amanhã"
                        else            -> "${daysUntil}d"
                    },
                    color = when {
                        isPast   -> MaterialTheme.colorScheme.onSurfaceVariant
                        isUrgent -> Color(0xFFFF9800)
                        else     -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 72.dp))
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

private val months = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)
