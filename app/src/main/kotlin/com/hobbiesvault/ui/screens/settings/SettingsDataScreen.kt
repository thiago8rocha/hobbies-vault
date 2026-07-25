package com.hobbiesvault.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.service.MediaCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsDataViewModel : ViewModel() {
    var updatingCache by mutableStateOf(false)
    var updateDone    by mutableStateOf(false)

    fun updateAllCache() {
        if (updatingCache) return
        viewModelScope.launch {
            updatingCache = true
            updateDone    = false
            val items = withContext(Dispatchers.IO) { DB.repo.getAll() }
            items.filter { it.externalId != null }.forEach { item ->
                MediaCacheService.fetchAndPersist(item)
            }
            updatingCache = false
            updateDone    = true
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            DB.repo.clearAll()
            DB.cache.deleteAll()
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataScreen(navController: NavController, vm: SettingsDataViewModel = viewModel()) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Dados") },
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
                ListItem(
                    headlineContent   = { Text("Atualizar todos os caches") },
                    supportingContent = {
                        when {
                            vm.updatingCache -> Text("Atualizando…")
                            vm.updateDone    -> Text("Cache atualizado com sucesso")
                            else             -> Text("Sincronizar metadados com as APIs")
                        }
                    },
                    trailingContent   = {
                        if (vm.updatingCache) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { vm.updateAllCache() }, enabled = !vm.updatingCache) {
                                Icon(Icons.Default.Sync, null)
                            }
                        }
                    },
                    modifier = Modifier.clickable(enabled = !vm.updatingCache) { vm.updateAllCache() },
                )
            }

            item {
                ListItem(
                    headlineContent   = { Text("Apagar todos os dados", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Remove toda a biblioteca e histórico") },
                    trailingContent   = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    modifier          = Modifier.clickable { showDeleteDialog = true },
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon   = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title  = { Text("Apagar todos os dados?") },
            text   = {
                Text("Esta ação é irreversível. Toda a sua biblioteca, histórico e cache serão removidos permanentemente.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        vm.clearAllData { navController.popBackStack() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Apagar tudo") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
        )
    }
}
