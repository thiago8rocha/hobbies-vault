package com.hobbiesvault.ui.screens.stats

import androidx.compose.ui.graphics.Color
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaStatus
import com.hobbiesvault.model.MediaType
import kotlin.math.roundToInt

val concludedStatuses = setOf(
    MediaStatus.COMPLETED, MediaStatus.PLATINUM, MediaStatus.FINISHED,
    MediaStatus.WATCHED, MediaStatus.CONCLUDED, MediaStatus.READ, MediaStatus.HISTORY,
)

/** Webtoon não é um hobby separado na prática — é sempre tratado como Mangá nas telas de estatísticas. */
val hobbySections = listOf(MediaType.GAME, MediaType.MANGA, MediaType.SERIES, MediaType.MOVIE, MediaType.BOOK)

fun MediaItem.matchesHobby(hobby: MediaType): Boolean =
    type == hobby || (hobby == MediaType.MANGA && type == MediaType.WEBTOON)

enum class StatDimension(val label: String) {
    STATUS("Status"),
    RATING("Nota"),
}

data class StatGroup(
    val key: String,
    val label: String,
    val color: Color,
    val items: List<MediaItem>,
) {
    val count: Int get() = items.size
    val avgRating: Double? get() {
        val rated = items.mapNotNull { it.rating }
        return if (rated.isEmpty()) null else rated.average()
    }
    val totalHours: Int get() = items.sumOf { it.playedMinutes ?: 0 } / 60
}

val ratingColor = Color(0xFFFFC107)

/** Cor de uma nota (1 a 5 estrelas), consistente em qualquer hobby. */
fun ratingStarColor(star: Int): Color = ratingColor.copy(alpha = 0.4f + 0.12f * star)

fun groupItems(items: List<MediaItem>, dimension: StatDimension): List<StatGroup> = when (dimension) {
    StatDimension.STATUS -> MediaStatus.entries.mapNotNull { status ->
        val inGroup = items.filter { it.status == status }
        if (inGroup.isEmpty()) null else StatGroup(status.dbValue, status.label, status.color, inGroup)
    }.sortedByDescending { it.count }

    StatDimension.RATING -> {
        val byRating = items
            .filter { it.rating != null }
            .groupBy { it.rating!!.roundToInt().coerceIn(1, 5) }
        (5 downTo 1).mapNotNull { star ->
            val inGroup = byRating[star]
            if (inGroup.isNullOrEmpty()) null
            else StatGroup(
                key   = star.toString(),
                label = "$star★",
                color = ratingStarColor(star),
                items = inGroup,
            )
        }
    }
}

/**
 * Paleta categórica fixa, na mesma linha da referência do Rokku (`StatsHelper.PIE_CHART_COLOR_LIST`
 * / `STATUS_COLOR_MAP`): cores distintas e curadas, atribuídas sempre na mesma ordem — nunca
 * derivadas algoritmicamente de uma única cor-base. Gerar cores por rotação de matiz a partir da
 * cor do hobby (testado antes) produz tons de baixo contraste (amarelo, água, magenta claros) que
 * prejudicam a leitura; uma paleta fixa e validada para contraste/daltonismo lê muito melhor.
 */
val categoricalChartPalette = listOf(
    Color(0xFF2A78D6), // azul
    Color(0xFFEB6834), // laranja
    Color(0xFF1BAF7A), // água
    Color(0xFFEDA100), // amarelo
    Color(0xFFE87BA4), // magenta
    Color(0xFF008300), // verde
    Color(0xFF4A3AA7), // violeta
    Color(0xFFE34948), // vermelho
)

/** Retorna [count] cores da paleta categórica fixa, ciclando se precisar de mais que 8. */
fun categoricalColors(count: Int): List<Color> =
    List(count) { i -> categoricalChartPalette[i % categoricalChartPalette.size] }
