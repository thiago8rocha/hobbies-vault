package com.hobbiesvault.ui.theme

import androidx.compose.ui.graphics.Color

// ── Cores de mídia ────────────────────────────────────────────────────────────
val ColorJogo    = Color(0xFF7B1FA2)
val ColorManga   = Color(0xFFE91E63)
val ColorWebtoon = Color(0xFF00BCD4)
val ColorSerie   = Color(0xFF1976D2)
val ColorFilme   = Color(0xFFFF6F00)
val ColorLivro   = Color(0xFF388E3C)

// ── Plataformas ───────────────────────────────────────────────────────────────
val ColorSteam       = Color(0xFF1B2838)
val ColorPlayStation = Color(0xFF00439C)
val ColorNintendo    = Color(0xFFE4000F)
val ColorXbox        = Color(0xFF107C10)

// ── Temas disponíveis ─────────────────────────────────────────────────────────
data class AppThemeDefinition(
    val id: String,
    val label: String,
    val seedDark: Color,
    val seedLight: Color,
    val bgDark: Color,
    val surfaceDark: Color,
    val bgLight: Color,
    val surfaceLight: Color,
)

val appThemes = listOf(
    AppThemeDefinition("neko",     "Neko",       Color(0xFF4CAF7D), Color(0xFF2E7D52), Color(0xFF1A1A1A), Color(0xFF242424), Color(0xFFF5F5F5), Color(0xFFFFFFFF)),
    AppThemeDefinition("tako",     "Tako",       Color(0xFFFF9800), Color(0xFFE65100), Color(0xFF1A1A1A), Color(0xFF242424), Color(0xFFF5F5F5), Color(0xFFFFFFFF)),
    AppThemeDefinition("yin",      "Yin",        Color(0xFFE0E0E0), Color(0xFF212121), Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFFFFFFF), Color(0xFFF5F5F5)),
    AppThemeDefinition("doki",     "Doki",       Color(0xFFF06292), Color(0xFFAD1457), Color(0xFF1A1218), Color(0xFF241E22), Color(0xFFFFF0F5), Color(0xFFFFFFFF)),
    AppThemeDefinition("ocean",    "Oceano",     Color(0xFF42A5F5), Color(0xFF1565C0), Color(0xFF111820), Color(0xFF1A2333), Color(0xFFE3F2FD), Color(0xFFFFFFFF)),
    AppThemeDefinition("midnight", "Meia-noite", Color(0xFF7986CB), Color(0xFF3949AB), Color(0xFF0D0D14), Color(0xFF16161F), Color(0xFFEEEEFF), Color(0xFFFFFFFF)),
)

fun appThemeById(id: String) = appThemes.firstOrNull { it.id == id } ?: appThemes.first()
