package com.hobbiesvault.service

import com.hobbiesvault.model.MediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes das regras de negócio puras extraídas de [MediaCacheService], que originalmente
 * viviam embutidas em funções `suspend` dependentes dos singletons `DB`/`ApiServices`.
 * Não requerem Android/Robolectric — rodam como testes JVM puros.
 */
class MediaCacheServiceLogicTest {

    // ── determineSeriesStatusTransition ─────────────────────────────────────────

    @Test
    fun `series ended moves to history`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.WATCHING, "Ended")
        assertEquals(MediaStatus.HISTORY, result)
    }

    @Test
    fun `series canceled with american spelling moves to history`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.REWATCHING, "Canceled")
        assertEquals(MediaStatus.HISTORY, result)
    }

    @Test
    fun `series cancelled with british spelling moves to history`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.WATCHING, "Cancelled")
        assertEquals(MediaStatus.HISTORY, result)
    }

    @Test
    fun `series already in history stays unchanged when ended`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.HISTORY, "Ended")
        assertNull(result)
    }

    @Test
    fun `series queued stays unchanged when ended`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.QUEUED, "Ended")
        assertNull(result)
    }

    @Test
    fun `series dropped stays unchanged when ended`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.DROPPED, "Ended")
        assertNull(result)
    }

    @Test
    fun `returning series from history moves to waiting release`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.HISTORY, "Returning Series")
        assertEquals(MediaStatus.WAITING_RELEASE, result)
    }

    @Test
    fun `returning series from watching stays unchanged`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.WATCHING, "Returning Series")
        assertNull(result)
    }

    @Test
    fun `unknown tmdb status never triggers a transition`() {
        val result = MediaCacheService.determineSeriesStatusTransition(MediaStatus.WATCHING, "In Production")
        assertNull(result)
    }

    // ── hasCacheChanged ──────────────────────────────────────────────────────────

    @Test
    fun `identical maps are not considered changed`() {
        val current = mapOf("title" to "Foo", "episodes" to 12)
        val newData = mapOf("title" to "Foo", "episodes" to 12)
        assertEquals(false, MediaCacheService.hasCacheChanged(current, newData))
    }

    @Test
    fun `maps with same entries in different order are not considered changed`() {
        val current = mapOf("title" to "Foo", "episodes" to 12)
        val newData = mapOf("episodes" to 12, "title" to "Foo")
        assertEquals(false, MediaCacheService.hasCacheChanged(current, newData))
    }

    @Test
    fun `differing values are considered changed`() {
        val current = mapOf("title" to "Foo", "episodes" to 12)
        val newData = mapOf("title" to "Foo", "episodes" to 13)
        assertEquals(true, MediaCacheService.hasCacheChanged(current, newData))
    }

    @Test
    fun `null current cache is considered changed`() {
        val newData = mapOf("title" to "Foo")
        assertEquals(true, MediaCacheService.hasCacheChanged(null, newData))
    }

    // ── computeMergedProgress ────────────────────────────────────────────────────

    @Test
    fun `remote progress ahead of local advances progress`() {
        val result = MediaCacheService.computeMergedProgress(localProgress = 10, remoteProgress = 15)
        assertEquals(15, result)
    }

    @Test
    fun `remote progress equal to local does not update`() {
        val result = MediaCacheService.computeMergedProgress(localProgress = 10, remoteProgress = 10)
        assertNull(result)
    }

    @Test
    fun `remote progress behind local never regresses a manual edit`() {
        val result = MediaCacheService.computeMergedProgress(localProgress = 20, remoteProgress = 10)
        assertNull(result)
    }

    @Test
    fun `null local progress is treated as zero`() {
        val result = MediaCacheService.computeMergedProgress(localProgress = null, remoteProgress = 1)
        assertEquals(1, result)
    }

    @Test
    fun `null local progress with zero remote does not update`() {
        val result = MediaCacheService.computeMergedProgress(localProgress = null, remoteProgress = 0)
        assertNull(result)
    }
}
