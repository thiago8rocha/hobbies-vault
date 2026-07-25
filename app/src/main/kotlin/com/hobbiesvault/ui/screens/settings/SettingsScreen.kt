package com.hobbiesvault.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hobbiesvault.ui.navigation.Routes

// ── Hub de Configurações ────────────────────────────────────────────────────
// Cada categoria abre sua própria sub-tela, no molde do menu principal de
// Configurações do Rokku: uma lista simples de linhas ícone + título + chevron.

private data class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

private val categories = listOf(
    SettingsCategory("Aparência",    "Temas, cores e modo de exibição", Icons.Default.Palette,      Routes.SETTINGS_APPEARANCE),
    SettingsCategory("Notificações", "Atualizações em segundo plano",   Icons.Default.Notifications, Routes.SETTINGS_NOTIFICATIONS),
    SettingsCategory("Integrações",  "Status das APIs conectadas",      Icons.Default.Cable,         Routes.SETTINGS_INTEGRATIONS),
    SettingsCategory("Plataformas",  "Filtros de plataforma em Jogos",  Icons.Default.SportsEsports, Routes.SETTINGS_PLATFORMS),
    SettingsCategory("Dados",        "Cache e biblioteca",              Icons.Default.Storage,       Routes.SETTINGS_DATA),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(categories) { category ->
                SettingsCategoryRow(category, onClick = { navController.navigate(category.route) })
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    ListItem(
        headlineContent   = { Text(category.label) },
        supportingContent = { Text(category.subtitle) },
        leadingContent    = { Icon(category.icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent   = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
        modifier          = Modifier
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp),
    )
}
