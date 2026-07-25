package com.hobbiesvault.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hobbiesvault.service.ApiServices

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsIntegrationsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Integrações") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { IntegrationRow("TMDB",    "Filmes e séries",         ApiServices.tmdbAvailable) }
            item { IntegrationRow("IGDB",    "Jogos",                   ApiServices.igdbAvailable) }
            item { IntegrationRow("Steam",   "Biblioteca e conquistas", ApiServices.steamAvailable) }
            item { IntegrationRow("HLTB",    "Tempo de jogo",           ApiServices.hltbAvailable) }
            item { IntegrationRow("AniList", "Mangás e webtoons",       true) }
        }
    }
}

/** Linha no molde do TrackingPreferenceWidget do Rokku: nome + check verde só quando ativo. */
@Composable
private fun IntegrationRow(name: String, description: String, connected: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (connected) {
            Icon(Icons.Default.Done, null, tint = Color(0xFF4CAF50), modifier = Modifier.padding(4.dp).size(24.dp))
        }
    }
}
