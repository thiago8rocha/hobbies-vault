package com.hobbiesvault.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import kotlin.math.abs
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.ui.screens.games.AddGameScreen
import com.hobbiesvault.ui.screens.games.GameDetailScreen
import com.hobbiesvault.ui.screens.games.GamesScreen
import com.hobbiesvault.ui.screens.films.AddFilmScreen
import com.hobbiesvault.ui.screens.films.FilmDetailScreen
import com.hobbiesvault.ui.screens.films.FilmsScreen
import com.hobbiesvault.ui.screens.series.AddSeriesScreen
import com.hobbiesvault.ui.screens.series.SeriesDetailScreen
import com.hobbiesvault.ui.screens.series.SeriesScreen
import com.hobbiesvault.ui.screens.manga.AddMangaScreen
import com.hobbiesvault.ui.screens.manga.MangaDetailScreen
import com.hobbiesvault.ui.screens.manga.MangaScreen
import com.hobbiesvault.ui.screens.books.AddBookScreen
import com.hobbiesvault.ui.screens.books.BookDetailScreen
import com.hobbiesvault.ui.screens.books.BooksScreen
import com.hobbiesvault.ui.screens.HomeScreen
import com.hobbiesvault.ui.screens.SearchScreen
import com.hobbiesvault.ui.screens.settings.SettingsScreen
import com.hobbiesvault.ui.screens.settings.SettingsAppearanceScreen
import com.hobbiesvault.ui.screens.settings.SettingsNotificationsScreen
import com.hobbiesvault.ui.screens.settings.SettingsIntegrationsScreen
import com.hobbiesvault.ui.screens.settings.SettingsDataScreen
import com.hobbiesvault.ui.screens.settings.SettingsPlatformsScreen
import com.hobbiesvault.ui.screens.HistoryScreen
import com.hobbiesvault.ui.screens.stats.StatsScreen
import com.hobbiesvault.ui.screens.stats.StatsDetailsScreen
import com.hobbiesvault.ui.screens.stats.StatsFilteredListScreen
import com.hobbiesvault.ui.screens.CalendarScreen
import com.hobbiesvault.ui.screens.AboutScreen

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.GAMES,  "Jogos",  Icons.Outlined.SportsEsports, Icons.Filled.SportsEsports),
    BottomNavItem(Routes.FILMS,  "Filmes", Icons.Outlined.Movie,          Icons.Filled.Movie),
    BottomNavItem(Routes.SERIES, "Séries", Icons.Outlined.Tv,             Icons.Filled.Tv),
    BottomNavItem(Routes.MANGA,  "Mangás", Icons.Outlined.MenuBook,      Icons.Filled.MenuBook),
    BottomNavItem(Routes.BOOKS,  "Livros", Icons.Outlined.Book,           Icons.Filled.Book),
)

@Composable
fun MainNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val shellRoutes = bottomNavItems.map { it.route }.toSet() + Routes.HOME
    val showBottomBar = currentRoute in shellRoutes

    // On the home route, no item is selected (selectedIndex = null)
    val selectedIndex = bottomNavItems.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(tonalElevation = 0.dp) {
                    bottomNavItems.forEachIndexed { index, item ->
                        val selected = selectedIndex == index
                        NavigationBarItem(
                            selected  = selected,
                            onClick   = { navController.navigateToHobbyTab(item.route) },
                            icon      = {
                                Icon(
                                    if (selected) item.activeIcon else item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label     = { Text(item.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.HOME,
            modifier         = Modifier
                .padding(bottom = innerPadding.calculateBottomPadding())
                .hobbySwipeNavigation(
                    enabled = selectedIndex != null,
                    onSwipeLeft  = {
                        val next = (selectedIndex ?: 0) + 1
                        if (next < bottomNavItems.size) navController.navigateToHobbyTab(bottomNavItems[next].route)
                    },
                    onSwipeRight = {
                        val prev = (selectedIndex ?: 0) - 1
                        if (prev >= 0) navController.navigateToHobbyTab(bottomNavItems[prev].route)
                    },
                ),
        ) {
            composable(Routes.HOME)    { HomeScreen(navController) }
            composable(Routes.GAMES)   { GamesScreen(navController) }
            composable(Routes.FILMS)   { FilmsScreen(navController) }
            composable(Routes.SERIES)  { SeriesScreen(navController) }
            composable(Routes.MANGA)   { MangaScreen(navController) }
            composable(Routes.BOOKS)   { BooksScreen(navController) }

            composable(Routes.GAMES_ADD)    { AddGameScreen(navController) }
            composable(Routes.GAMES_DETAIL) {
                val item = navController.previousBackStackEntry?.savedStateHandle?.get<MediaItem>("item")
                if (item != null) GameDetailScreen(navController, item)
            }

            composable(Routes.FILMS_ADD)    { AddFilmScreen(navController) }
            composable(Routes.FILMS_DETAIL) {
                val item = navController.previousBackStackEntry?.savedStateHandle?.get<MediaItem>("item")
                if (item != null) FilmDetailScreen(navController, item)
            }

            composable(Routes.SERIES_ADD)    { AddSeriesScreen(navController) }
            composable(Routes.SERIES_DETAIL) {
                val item = navController.previousBackStackEntry?.savedStateHandle?.get<MediaItem>("item")
                if (item != null) SeriesDetailScreen(navController, item)
            }

            composable(Routes.MANGA_ADD)    { AddMangaScreen(navController) }
            composable(Routes.MANGA_DETAIL) {
                val item = navController.previousBackStackEntry?.savedStateHandle?.get<MediaItem>("item")
                if (item != null) MangaDetailScreen(navController, item)
            }

            composable(Routes.BOOKS_ADD)    { AddBookScreen(navController) }
            composable(Routes.BOOKS_DETAIL) {
                val item = navController.previousBackStackEntry?.savedStateHandle?.get<MediaItem>("item")
                if (item != null) BookDetailScreen(navController, item)
            }

            composable(Routes.SEARCH)   { SearchScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.SETTINGS_APPEARANCE)    { SettingsAppearanceScreen(navController) }
            composable(Routes.SETTINGS_NOTIFICATIONS) { SettingsNotificationsScreen(navController) }
            composable(Routes.SETTINGS_INTEGRATIONS)  { SettingsIntegrationsScreen(navController) }
            composable(Routes.SETTINGS_DATA)          { SettingsDataScreen(navController) }
            composable(Routes.SETTINGS_PLATFORMS)     { SettingsPlatformsScreen(navController) }
            composable(Routes.HISTORY)  { HistoryScreen(navController) }
            composable(Routes.STATS)    { StatsScreen(navController) }
            composable(Routes.STATS_DETAILS)       { StatsDetailsScreen(navController) }
            composable(Routes.STATS_FILTERED_LIST) { StatsFilteredListScreen(navController) }
            composable(Routes.CALENDAR) { CalendarScreen(navController) }
            composable(Routes.ABOUT)    { AboutScreen(navController) }
        }
    }
}

private fun androidx.navigation.NavController.navigateToHobbyTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}

/**
 * Detecta um arraste predominantemente horizontal (com viés de 1.5x sobre o vertical antes de
 * decidir) para não competir com o scroll vertical das grades — só passa a consumir o gesto
 * depois de confirmar que é uma navegação por swipe, então listas verticais continuam intactas.
 */
private fun Modifier.hobbySwipeNavigation(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(onSwipeLeft, onSwipeRight) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var isHorizontal: Boolean? = null
            var totalDx = 0f
            var totalDy = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    if (isHorizontal == true) {
                        if (totalDx < -120f) onSwipeLeft()
                        else if (totalDx > 120f) onSwipeRight()
                    }
                    break
                }
                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y
                if (isHorizontal == null && (abs(totalDx) > 16f || abs(totalDy) > 16f)) {
                    isHorizontal = abs(totalDx) > abs(totalDy) * 1.5f
                }
                if (isHorizontal == true) change.consume()
            }
        }
    }
}
