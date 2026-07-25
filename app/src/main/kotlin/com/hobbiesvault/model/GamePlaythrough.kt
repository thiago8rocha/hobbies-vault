package com.hobbiesvault.model

import java.util.Date

data class GamePlaythrough(
    val id: Int = 0,
    val title: String,
    val startDate: Date? = null,
    val endDate: Date? = null,
    val hoursPlayed: Int? = null,
    val notes: String? = null,
)
