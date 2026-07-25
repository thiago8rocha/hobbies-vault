package com.hobbiesvault.model

import java.util.Date

data class MangaReview(
    val rating: Double?,
    val reviewTitle: String?,
    val reviewText: String?,
    val completedAt: Date,
)
