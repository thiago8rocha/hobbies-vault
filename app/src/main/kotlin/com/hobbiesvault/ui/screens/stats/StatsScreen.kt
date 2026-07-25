package com.hobbiesvault.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.components.PieChartCanvas
import com.hobbiesvault.ui.components.PieSlice
import com.hobbiesvault.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class StatsViewModel : ViewModel() {
    val allItems = DB.repo.watchAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

fun navigateToStatsDetails(navController: NavController, typeFilter: MediaType? = null) {
    val handle = navController.currentBackStackEntry?.savedStateHandle
    if (typeFilter != null) handle?.set("typeFilter", typeFilter.name) else handle?.remove<String>("typeFilter")
    navController.navigate(Routes.STATS_DETAILS)
}

private data class GeneralStatItem(val count: Int, val label: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController, vm: StatsViewModel = viewModel()) {
    val allItems by vm.allItems.collectAsStateWithLifecycle()

    val generalStats = remember(allItems) {
        val games   = allItems.filter { it.type == MediaType.GAME }
        val movies  = allItems.filter { it.type == MediaType.MOVIE }
        val series  = allItems.filter { it.type == MediaType.SERIES }
        val mangas  = allItems.filter { it.matchesHobby(MediaType.MANGA) }
        val books   = allItems.filter { it.type == MediaType.BOOK }

        // Paleta categórica fixa e validada — oito cores bem distintas, uma por métrica.
        val colors = categoricalColors(8)

        listOf(
            GeneralStatItem(games.count { it.status == MediaStatus.FINISHED }, "Jogos Zerados", colors[0]),
            GeneralStatItem(games.count { it.status == MediaStatus.PLATINUM }, "Jogos Platinados", colors[1]),
            GeneralStatItem(movies.count { it.status == MediaStatus.WATCHED }, "Filmes Assistidos", colors[2]),
            GeneralStatItem(movies.count { it.status == MediaStatus.QUEUED }, "Filmes em Quero Assistir", colors[3]),
            GeneralStatItem(series.sumOf { it.currentProgress ?: 0 }, "Episódios Assistidos", colors[4]),
            GeneralStatItem(series.count { it.status == MediaStatus.QUEUED }, "Séries em Quero Assistir", colors[5]),
            GeneralStatItem(mangas.sumOf { it.currentProgress ?: 0 }, "Capítulos Lidos", colors[6]),
            GeneralStatItem(books.count { it.status == MediaStatus.READ }, "Livros Lidos", colors[7]),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Status") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Geral ────────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { navigateToStatsDetails(navController) }) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ver estatísticas detalhadas")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Geral", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    generalStats.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { stat ->
                                GeneralStat(stat = stat, modifier = Modifier.weight(1f))
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // ── Gráfico compilando os dados acima, com legenda ao lado ─────
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PieChartCanvas(
                        slices   = generalStats.map { PieSlice(it.label, it.count.toFloat(), it.color) },
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        generalStats.forEach { stat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).background(stat.color, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(stat.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(
                                    "${stat.count}",
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Número centralizado em relação à frase abaixo — estilo Rokku. */
@Composable
private fun GeneralStat(stat: GeneralStatItem, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${stat.count}",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = stat.color,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stat.label,
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
