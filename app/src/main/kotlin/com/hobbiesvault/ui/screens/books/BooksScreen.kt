package com.hobbiesvault.ui.screens.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.components.EmptyState
import com.hobbiesvault.ui.components.OverflowMenu
import com.hobbiesvault.ui.components.OverflowMenuItem
import com.hobbiesvault.ui.components.ProportionalTabRow
import com.hobbiesvault.ui.navigation.Routes
import com.hobbiesvault.ui.theme.ColorLivro
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class BooksViewModel : ViewModel() {
    val allItems = DB.repo.watchByType(MediaType.BOOK)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(navController: NavController, vm: BooksViewModel = viewModel()) {
    val allItems by vm.allItems.collectAsStateWithLifecycle()

    val tabs = listOf("Todos", "Lendo", "Lido", "Abandonado", "Quero Ler")
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    val filtered = remember(allItems, selectedTab) {
        when (selectedTab) {
            0 -> allItems
            1 -> allItems.filter { it.status == MediaStatus.READING || it.status == MediaStatus.REREADING }
            2 -> allItems.filter { it.status == MediaStatus.READ }
                         .sortedByDescending { it.completionDate ?: it.addedDate }
            3 -> allItems.filter { it.status == MediaStatus.DROPPED }
            4 -> allItems.filter { it.status == MediaStatus.QUEUED || it.status == MediaStatus.WAITING_RELEASE }
                         .sortedBy { it.title }
            else -> allItems
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title          = { Text("Livros") },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.navigate(Routes.HOME) { launchSingleTop = true }
                        }) {
                            Icon(Icons.Outlined.Home, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        OverflowMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            OverflowMenuItem(
                                text    = "Configurações",
                                icon    = Icons.Outlined.Settings,
                                onClick = { showMenu = false; navController.navigate(Routes.SETTINGS) },
                            )
                        }
                    }
                )
                ProportionalTabRow(
                    selectedTabIndex = selectedTab,
                    tabs             = tabs,
                    selectedColor    = ColorLivro,
                    onTabSelected    = { selectedTab = it },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { navController.navigate(Routes.BOOKS_ADD) },
                containerColor = ColorLivro,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Default.Add, contentDescription = null) },
                text           = { Text("Adicionar livro") },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (filtered.isEmpty()) {
                val (title, subtitle) = when (selectedTab) {
                    0 -> "Nenhum livro na biblioteca" to "Adicione um livro para começar"
                    1 -> "Nenhum livro em leitura" to "Livros que você está lendo aparecerão aqui"
                    2 -> "Nenhum livro lido" to "Livros que você concluiu aparecerão aqui"
                    3 -> "Nenhum livro abandonado" to "Livros que você parou de ler aparecerão aqui"
                    else -> "Lista de leitura vazia" to "Livros que você quer ler aparecerão aqui"
                }
                EmptyState(title, subtitle, "Adicionar livro", onButton = { navController.navigate(Routes.BOOKS_ADD) })
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    contentPadding        = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered) { item ->
                        BookCard(item = item, onTap = { navigateToDetail(navController, item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCard(item: MediaItem, onTap: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onTap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.56f)) {
            if (item.coverUrl != null) {
                AsyncImage(
                    model              = item.coverUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(ColorLivro.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Book, null, tint = ColorLivro.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                }
            }
        }
        Text(
            item.title,
            style    = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(34.dp),
        )
    }
}

private fun navigateToDetail(navController: NavController, item: MediaItem) {
    navController.currentBackStackEntry?.savedStateHandle?.set("item", item)
    navController.navigate(Routes.BOOKS_DETAIL)
}
