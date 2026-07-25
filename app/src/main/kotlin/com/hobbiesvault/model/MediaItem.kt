package com.hobbiesvault.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.util.Date

object NonNullDateParceler : Parceler<Date> {
    override fun create(parcel: Parcel): Date = Date(parcel.readLong())
    override fun Date.write(parcel: Parcel, flags: Int) { parcel.writeLong(time) }
}

object NullableDateParceler : Parceler<Date?> {
    override fun create(parcel: Parcel): Date? = parcel.readLong().let { if (it == -1L) null else Date(it) }
    override fun Date?.write(parcel: Parcel, flags: Int) { parcel.writeLong(this?.time ?: -1L) }
}

@Parcelize
@TypeParceler<Date, NonNullDateParceler>()
@TypeParceler<Date?, NullableDateParceler>()
data class MediaItem(
    val id: Int? = null,
    val type: MediaType,
    val title: String,
    val status: MediaStatus,
    val rating: Double? = null,
    val reviewTitle: String? = null,
    val notes: String? = null,
    /** Anotação livre do usuário, independente de resenha/comentários — editável via o menu "...". */
    val personalNotes: String? = null,
    /** Resenha do livro — separada do log de comentários de leitura (`notes`). */
    val bookReviewText: String? = null,
    val coverUrl: String? = null,
    val completionDate: Date? = null,
    val addedDate: Date = Date(),
    val favorite: Boolean = false,
    val console: GameConsole? = null,
    val currentProgress: Int? = null,
    val totalProgress: Int? = null,
    val streamingPlatform: String? = null,
    val releaseDate: Date? = null,
    val playedMinutes: Int? = null,
    val achievementsUnlocked: Int? = null,
    val totalAchievements: Int? = null,
    val goldTrophies: Int? = null,
    val silverTrophies: Int? = null,
    val bronzeTrophies: Int? = null,
    val platinumTrophy: Boolean? = null,
    val genre: String? = null,
    val developer: String? = null,
    val externalId: String? = null,
    val apiSource: String? = null,
    val readingStartDate: Date? = null,
    val rereadingDate: Date? = null,
    val historyCompletionDate: Date? = null,
    val extrasCompletionDate: Date? = null,
    val platinumCompletionDate: Date? = null,
) : Parcelable {
    val playedTimeLabel: String? get() {
        val min = playedMinutes ?: return null
        val h = min / 60; val m = min % 60
        return when { h == 0 -> "${m}min"; m == 0 -> "${h}h"; else -> "${h}h ${m}min" }
    }

    val hasTrophies get() =
        goldTrophies != null || silverTrophies != null || bronzeTrophies != null || platinumTrophy != null

    val availableStatuses: List<MediaStatus> get() = when (type) {
        MediaType.GAME -> when {
            console?.isSteam == true || console == GameConsole.PC -> MediaStatus.forSteam()
            console?.isPlayStation == true                         -> MediaStatus.forPlayStation()
            console?.isNintendo == true                            -> MediaStatus.forNintendo()
            else                                                   -> MediaStatus.forOtherGames()
        }
        MediaType.MOVIE              -> MediaStatus.forMovie()
        MediaType.SERIES             -> MediaStatus.forSeries()
        MediaType.MANGA, MediaType.WEBTOON -> MediaStatus.forManga()
        MediaType.BOOK               -> MediaStatus.forBook()
    }
}
