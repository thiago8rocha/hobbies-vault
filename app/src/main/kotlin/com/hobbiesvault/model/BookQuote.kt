package com.hobbiesvault.model

import java.util.Date

data class BookQuote(
    val id: Int = 0,
    val quote: String,
    val comment: String? = null,
    val createdAt: Date = Date(),
)
