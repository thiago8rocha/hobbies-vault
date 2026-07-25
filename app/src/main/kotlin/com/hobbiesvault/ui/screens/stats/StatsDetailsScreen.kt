package com.hobbiesvault.ui.screens.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.components.BarChartCanvas
import com.hobbiesvault.ui.components.BarItem
import com.hobbiesvault.ui.components.PieChartCanvas
import com.hobbiesvault.ui.components.PieSlice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class StatsDetailsViewModel : ViewModel() {
    val allItems = DB.repo.watchAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailsScreen(navController: NavController, vm: StatsDetailsViewModel = viewModel()) {
    val allItems by vm.allItems.collectAsStateWithLifecycle()

    val typeFilter = remember {
        val raw = navController.previousBackStackEntry?.savedStateHandle?.get<String>("typeFilter")
        raw?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
    }
    val chartHobbies = remember(typeFilter) { typeFilter?.let { listOf(it) } ?: hobbySections }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(if (typeFilter != null) "Detalhes — ${typeFilter.labelPt}" else "Detalhes") },
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
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(chartHobbies, key = { it.dbValue }) { hobby ->
                val hobbyItems = allItems.filter { it.matchesHobby(hobby) }
                if (hobbyItems.isNotEmpty()) {
                    HobbyChartCard(
                        hobby    = hobby,
                        items    = hobbyItems,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Uma seção por hobby: nome em maiúsculas na cor do hobby (sem caixa de cor ao lado),
 * o gráfico de distribuição de status abaixo, e a legenda dentro de uma caixa (estilo das
 * seções nas telas de detalhe) abaixo do gráfico.
 *
 * As fatias usam a cor fixa de cada status (a mesma usada em toda a UI, ex. StatusChip) —
 * não uma cor derivada do hobby. Testamos gerar cores a partir da cor do hobby e a leitura
 * piorou bastante (tons de baixo contraste); a legenda também usa texto em tinta neutra com
 * um marcador colorido ao lado — texto colorido direto é mais difícil de ler, especialmente
 * em cores claras como amarelo e magenta.
 */
@Composable
private fun HobbyChartCard(hobby: MediaType, items: List<MediaItem>, modifier: Modifier = Modifier) {
    val statusGroups = remember(items) { groupItems(items, StatDimension.STATUS) }
    val ratingGroups  = remember(items) { groupItems(items, StatDimension.RATING) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hobby.labelPtPlural.uppercase(),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = hobby.color,
        )
        Spacer(Modifier.height(16.dp))

        PieChartCanvas(
            slices   = statusGroups.map { group -> PieSlice(group.label, group.count.toFloat(), group.color) },
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
            shape    = RoundedCornerShape(12.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                statusGroups.forEach { group ->
                    val pct = group.count.toFloat() / items.size.toFloat() * 100f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(group.color, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            group.label,
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${group.count} (${"%.0f".format(pct)}%)",
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (ratingGroups.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            val bars = (1..5).map { star ->
                val group = ratingGroups.firstOrNull { it.key == star.toString() }
                BarItem(
                    label = "$star★",
                    value = group?.count?.toFloat() ?: 0f,
                    color = ratingStarColor(star),
                )
            }
            BarChartCanvas(
                bars     = bars,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                barAreaHeight = 70.dp,
            )
        }
    }
}
