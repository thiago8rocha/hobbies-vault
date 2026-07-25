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
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val concludedStatuses = setOf(
    MediaStatus.COMPLETED, MediaStatus.PLATINUM, MediaStatus.FINISHED,
    MediaStatus.WATCHED, MediaStatus.CONCLUDED, MediaStatus.READ, MediaStatus.HISTORY,
)

private val months = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)

class HistoryViewModel : ViewModel() {
    val concluded = DB.repo.watchAll().map { items ->
        items
            .filter { it.status in concludedStatuses }
            .sortedByDescending { it.completionDate ?: it.addedDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, vm: HistoryViewModel = viewModel()) {
    val concluded by vm.concluded.collectAsStateWithLifecycle()

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var showWrapped by remember { mutableStateOf(false) }

    val availableYears = remember(concluded) {
        concluded.mapNotNull { it.completionDate?.let { d ->
            Calendar.getInstance().also { c -> c.time = d }.get(Calendar.YEAR)
        }}.toSet().sortedDescending().ifEmpty { listOf(currentYear) }
    }

    val itemsForYear = remember(concluded, selectedYear) {
        concluded.filter { item ->
            item.completionDate?.let { d ->
                Calendar.getInstance().also { c -> c.time = d }.get(Calendar.YEAR) == selectedYear
            } ?: false
        }
    }

    val groupedByMonth = remember(itemsForYear) {
        val map = linkedMapOf<String, MutableList<MediaItem>>()
        for (item in itemsForYear) {
            val d = item.completionDate ?: continue
            val cal = Calendar.getInstance().also { it.time = d }
            val month = months[cal.get(Calendar.MONTH)]
            map.getOrPut(month) { mutableListOf() }.add(item)
        }
        map
    }

    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Histórico") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showWrapped = true }) {
                        Icon(Icons.Outlined.Celebration, null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Year chips
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableYears.forEach { year ->
                    FilterChip(
                        selected = year == selectedYear,
                        onClick  = { selectedYear = year },
                        label    = { Text(year.toString()) },
                        shape    = RoundedCornerShape(4.dp),
                    )
                }
            }

            if (itemsForYear.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nenhum título concluído em $selectedYear",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Títulos concluídos aparecem aqui",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    groupedByMonth.forEach { (month, monthItems) ->
                        item(key = "header_$month") {
                            Text(
                                month,
                                style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color    = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(monthItems, key = { it.id ?: it.title }) { mediaItem ->
                            HistoryItemRow(
                                item    = mediaItem,
                                dateFmt = dateFmt,
                                onClick = { navigateToDetail(navController, mediaItem) },
                            )
                            HorizontalDivider(Modifier.padding(start = 76.dp))
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showWrapped) {
        AlertDialog(
            onDismissRequest = { showWrapped = false },
            title   = { Text("Resumo do ano") },
            text    = { Text("O resumo visual do seu ano estará disponível em breve.") },
            confirmButton = { TextButton(onClick = { showWrapped = false }) { Text("Ok") } },
        )
    }
}

@Composable
private fun HistoryItemRow(
    item: MediaItem,
    dateFmt: SimpleDateFormat,
    onClick: () -> Unit,
) {
    val typeColor = item.type.color

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${item.type.labelPt} · ${item.status.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        if (item.completionDate != null) {
            Text(
                dateFmt.format(item.completionDate),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
        }
    }
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
