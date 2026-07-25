package com.hobbiesvault.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.hobbiesvault.data.PlatformPreferences
import com.hobbiesvault.model.GameConsole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPlatformsScreen(navController: NavController) {
    val visibleConsoles by PlatformPreferences.visibleConsoles.collectAsStateWithLifecycle()
    val groups = remember(GameConsole.entries) { GameConsole.entries.groupBy { it.familyLabel } }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Plataformas") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "Escolha quais plataformas aparecem como opção de filtro na tela de Jogos.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            groups.forEach { (family, consoles) ->
                item { PreferenceGroupHeader(family) }
                items(consoles) { console ->
                    val checked = console in visibleConsoles
                    ListItem(
                        headlineContent = { Text(console.label) },
                        trailingContent = {
                            Switch(
                                checked         = checked,
                                onCheckedChange = { PlatformPreferences.setVisible(console, it) },
                            )
                        },
                        modifier = Modifier
                            .clickable { PlatformPreferences.setVisible(console, !checked) }
                            .heightIn(min = 52.dp),
                    )
                }
            }
        }
    }
}
