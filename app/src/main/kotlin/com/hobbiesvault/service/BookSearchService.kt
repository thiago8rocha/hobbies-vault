package com.hobbiesvault.service

import com.hobbiesvault.model.ApiSearchResult

class BookSearchService(
    private val googleBooks: GoogleBooksService,
    private val openLibrary: OpenLibraryService,
) {
    fun searchBooks(query: String): List<ApiSearchResult> {
        val results = runCatching { googleBooks.searchBooks(query) }.getOrElse { emptyList() }
        if (results.isNotEmpty()) return results
        return runCatching { openLibrary.searchBooks(query) }.getOrElse { emptyList() }
    }

    fun searchByAuthor(author: String): List<ApiSearchResult> {
        val results = runCatching { googleBooks.searchByAuthor(author) }.getOrElse { emptyList() }
        if (results.isNotEmpty()) return results
        return runCatching { openLibrary.searchBooks(author) }.getOrElse { emptyList() }
    }

    fun searchByPublisher(publisher: String): List<ApiSearchResult> =
        runCatching { googleBooks.searchByPublisher(publisher) }.getOrElse { emptyList() }
}
