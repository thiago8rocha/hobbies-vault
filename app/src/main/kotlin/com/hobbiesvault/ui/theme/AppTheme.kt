package com.hobbiesvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Controller global de tema ────────────────────────────────────────────────

enum class ThemeMode { DARK, LIGHT, SYSTEM }

object AppThemeController {
    // Tema claro e escuro são escolhidos de forma independente (cada um pode ser
    // qualquer uma das paletas disponíveis) — o modo de cor decide qual dos dois
    // está ativo no momento, não qual paleta usar.
    var lightThemeId by mutableStateOf("neko")
    var darkThemeId  by mutableStateOf("neko")
    var themeMode    by mutableStateOf(ThemeMode.DARK)

    val darkMode: Boolean
        @Composable get() = when (themeMode) {
            ThemeMode.DARK   -> true
            ThemeMode.LIGHT  -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

    fun setLightTheme(id: String) { lightThemeId = id }
    fun setDarkTheme(id: String)  { darkThemeId = id }
}

// ── Composable principal ──────────────────────────────────────────────────────

@Composable
fun HobbiesVaultTheme(
    lightThemeId: String = AppThemeController.lightThemeId,
    darkThemeId: String = AppThemeController.darkThemeId,
    darkTheme: Boolean = AppThemeController.darkMode,
    content: @Composable () -> Unit,
) {
    val def = appThemeById(if (darkTheme) darkThemeId else lightThemeId)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary          = def.seedDark,
            background       = def.bgDark,
            surface          = def.surfaceDark,
            onPrimary        = Color.White,
            onBackground     = Color.White,
            onSurface        = Color.White,
        )
    } else {
        lightColorScheme(
            primary          = def.seedLight,
            background       = def.bgLight,
            surface          = def.surfaceLight,
            onPrimary        = Color.White,
            onBackground     = Color.Black,
            onSurface        = Color.Black,
        )
    }

    // Escala de cantos M3: capas de mídia seguem retas (extraSmall/small), containers
    // de card e chips ganham arredondamento (medium/large) para uma leitura mais atual.
    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        small      = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        medium     = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        large      = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes      = shapes,
        content     = content,
    )
}
