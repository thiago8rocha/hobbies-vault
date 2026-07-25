package com.hobbiesvault.data.repository

import com.hobbiesvault.data.db.dao.BookQuoteDao
import com.hobbiesvault.data.db.dao.GamePlaythroughDao
import com.hobbiesvault.data.db.dao.MovieListDao
import com.hobbiesvault.data.db.dao.MediaItemDao
import com.hobbiesvault.data.db.dao.MangaReviewDao
import com.hobbiesvault.data.db.dao.SeriesEpisodeDao
import com.hobbiesvault.data.db.entity.BookQuoteEntity
import com.hobbiesvault.data.db.entity.GamePlaythroughEntity
import com.hobbiesvault.data.db.entity.MovieListEntity
import com.hobbiesvault.data.db.entity.MovieListItemEntity
import com.hobbiesvault.data.db.entity.MediaItemEntity
import com.hobbiesvault.data.db.entity.MangaReviewEntity
import com.hobbiesvault.data.db.entity.SeriesEpisodeEntity
import com.hobbiesvault.model.BookQuote
import com.hobbiesvault.model.GamePlaythrough
import com.hobbiesvault.model.MangaReview
import com.hobbiesvault.model.MediaItem
import com.hobbiesvault.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

class MediaRepository(
    private val itemDao: MediaItemDao,
    private val listDao: MovieListDao,
    private val episodeDao: SeriesEpisodeDao,
    private val mangaReviewDao: MangaReviewDao,
    private val bookQuoteDao: BookQuoteDao,
    private val gamePlaythroughDao: GamePlaythroughDao,
) {
    // ── MediaItems ─────────────────────────────────────────────────────────────

    suspend fun getAll(): List<MediaItem> =
        itemDao.getAll().map { it.toDomain() }

    suspend fun getByType(type: MediaType): List<MediaItem> =
        itemDao.getByType(type.dbValue).map { it.toDomain() }

    suspend fun getByTypes(types: List<MediaType>): List<MediaItem> =
        itemDao.getByTypes(types.map { it.dbValue }).map { it.toDomain() }

    suspend fun getById(id: Int): MediaItem? =
        itemDao.getById(id)?.toDomain()

    fun watchByType(type: MediaType): Flow<List<MediaItem>> =
        itemDao.watchByType(type.dbValue).map { list -> list.map { it.toDomain() } }

    fun watchByTypes(types: List<MediaType>): Flow<List<MediaItem>> =
        itemDao.watchByTypes(types.map { it.dbValue }).map { list -> list.map { it.toDomain() } }

    fun watchAll(): Flow<List<MediaItem>> =
        itemDao.watchAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(item: MediaItem): Int =
        itemDao.insert(MediaItemEntity.fromDomain(item)).toInt()

    suspend fun update(item: MediaItem) =
        itemDao.update(MediaItemEntity.fromDomain(item))

    suspend fun delete(id: Int) =
        itemDao.delete(id)

    suspend fun clearAll() {
        itemDao.deleteAll()
        episodeDao.deleteAll()
        listDao.deleteAllLists()
    }

    // ── Movie lists ────────────────────────────────────────────────────────────

    fun watchLists() = listDao.watchLists()

    suspend fun getAllLists() = listDao.getAll()

    suspend fun createList(name: String, description: String? = null) {
        listDao.create(MovieListEntity(name = name, description = description, createdAtMs = Date().time))
    }

    suspend fun updateList(id: Int, name: String, description: String?) = listDao.update(id, name, description)

    suspend fun renameList(id: Int, newName: String) = listDao.rename(id, newName)

    suspend fun deleteList(id: Int) = listDao.delete(id)

    suspend fun addToList(listId: Int, mediaItemId: Int) =
        listDao.addItem(MovieListItemEntity(listId, mediaItemId))

    suspend fun removeFromList(listId: Int, mediaItemId: Int) =
        listDao.removeItem(listId, mediaItemId)

    suspend fun removeItemFromAllLists(mediaItemId: Int) =
        listDao.removeItemFromAll(mediaItemId)

    suspend fun mediaItemIdsOfList(listId: Int) = listDao.mediaItemIdsOfList(listId)

    suspend fun listIdsOfItem(mediaItemId: Int) = listDao.listIdsOfItem(mediaItemId)

    fun watchListItems(listId: Int) = listDao.watchListItems(listId)

    fun watchAllListItems() = listDao.watchAllListItems()

    // ── Series episodes ────────────────────────────────────────────────────────

    suspend fun episodesBySeries(mediaItemId: Int) = episodeDao.getBySeries(mediaItemId)

    fun watchEpisodesBySeries(mediaItemId: Int) = episodeDao.watchBySeries(mediaItemId)

    suspend fun getAllEpisodes() = episodeDao.getAll()

    fun watchAllEpisodes() = episodeDao.watchAll()

    suspend fun markEpisode(
        mediaItemId: Int, season: Int, episode: Int,
        episodeName: String? = null, seriesName: String? = null, coverUrl: String? = null,
    ) {
        episodeDao.mark(
            SeriesEpisodeEntity(
                mediaItemId = mediaItemId,
                season      = season,
                episode     = episode,
                watchedAtMs = Date().time,
                episodeName = episodeName,
                seriesName  = seriesName,
                coverUrl    = coverUrl,
            )
        )
    }

    suspend fun unmarkEpisode(mediaItemId: Int, season: Int, episode: Int) =
        episodeDao.unmark(mediaItemId, season, episode)

    suspend fun deleteSeriesEpisodes(mediaItemId: Int) =
        episodeDao.deleteBySeries(mediaItemId)

    // ── Manga review history ──────────────────────────────────────────────────

    suspend fun mangaReviewHistory(mediaItemId: Int): List<MangaReview> =
        mangaReviewDao.getByItem(mediaItemId).map { it.toDomain() }

    suspend fun archiveMangaReview(mediaItemId: Int, rating: Double?, reviewTitle: String?, reviewText: String?, completedAt: Date) =
        mangaReviewDao.insert(
            MangaReviewEntity(
                mediaItemId   = mediaItemId,
                rating        = rating,
                reviewTitle   = reviewTitle,
                reviewText    = reviewText,
                completedAtMs = completedAt.time,
            )
        )

    // ── Book quotes ────────────────────────────────────────────────────────────

    fun watchBookQuotes(mediaItemId: Int): Flow<List<BookQuote>> =
        bookQuoteDao.watchByItem(mediaItemId).map { list -> list.map { it.toDomain() } }

    suspend fun addBookQuote(mediaItemId: Int, quote: String, comment: String?) =
        bookQuoteDao.insert(
            BookQuoteEntity(
                mediaItemId = mediaItemId,
                quote       = quote,
                comment     = comment,
                createdAtMs = Date().time,
            )
        )

    suspend fun deleteBookQuote(id: Int) = bookQuoteDao.delete(id)

    // ── Game playthroughs ────────────────────────────────────────────────────

    fun watchPlaythroughs(mediaItemId: Int): Flow<List<GamePlaythrough>> =
        gamePlaythroughDao.watchByItem(mediaItemId).map { list -> list.map { it.toDomain() } }

    suspend fun savePlaythrough(mediaItemId: Int, playthrough: GamePlaythrough) {
        val entity = GamePlaythroughEntity(
            id           = playthrough.id,
            mediaItemId  = mediaItemId,
            title        = playthrough.title,
            startDateMs  = playthrough.startDate?.time,
            endDateMs    = playthrough.endDate?.time,
            hoursPlayed  = playthrough.hoursPlayed,
            notes        = playthrough.notes,
        )
        if (playthrough.id == 0) gamePlaythroughDao.insert(entity) else gamePlaythroughDao.update(entity)
    }

    suspend fun deletePlaythrough(id: Int) = gamePlaythroughDao.delete(id)
}
