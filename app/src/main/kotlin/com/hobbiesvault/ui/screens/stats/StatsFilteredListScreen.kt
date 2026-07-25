package com.hobbiesvault.ui.screens.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaType
import com.hobbiesvault.ui.components.EmptyState
import com.hobbiesvault.ui.components.MediaGridCard
import com.hobbiesvault.ui.navigation.Routes

private fun detailRouteFor(type: MediaType): String = when (type) {
    MediaType.GAME               -> Routes.GAMES_DETAIL
    MediaType.MOVIE               -> Routes.FILMS_DETAIL
    MediaType.SERIES               -> Routes.SERIES_DETAIL
    MediaType.MANGA, MediaType.WEBTOON -> Routes.MANGA_DETAIL
    MediaType.BOOK               -> Routes.BOOKS_DETAIL
}

private fun navigateToItemDetail(navController: NavController, item: MediaItem) {
    navController.currentBackStackEntry?.savedStateHandle?.set("item", item)
    navController.navigate(detailRouteFor(item.type))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsFilteredListScreen(navController: NavController) {
    val title = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<String>("filteredTitle") ?: "Itens"
    }
    val items = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<ArrayList<MediaItem>>("filteredItems") ?: arrayListOf()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                title    = "Nenhum item encontrado",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id ?: it.hashCode() }) { item ->
                    MediaGridCard(
                        title       = item.title,
                        coverUrl    = item.coverUrl,
                        accentColor = item.type.color,
                        modifier    = Modifier.clickable { navigateToItemDetail(navController, item) },
                    )
                }
            }
        }
    }
}
