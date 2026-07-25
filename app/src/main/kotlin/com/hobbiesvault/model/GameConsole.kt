package com.hobbiesvault.model

import androidx.compose.ui.graphics.Color

enum class GameConsole(val label: String, val dbValue: String) {
    STEAM("Steam", "steam"),
    PS1("PS1", "ps1"), PS2("PS2", "ps2"), PS3("PS3", "ps3"),
    PS4("PS4", "ps4"), PS5("PS5", "ps5"), PSP("PSP", "psp"), PS_VITA("PS Vita", "psVita"),
    NES("NES", "nes"), SNES("SNES", "snes"), N64("N64", "n64"),
    GCN("GCN", "gcn"), WII("Wii", "wii"), WII_U("Wii U", "wiiU"),
    GBA("GBA", "gba"), DS("DS", "ds"), N3DS("3DS", "n3ds"),
    NS("NS", "ns"), NS2("NS2", "ns2"),
    XBOX("Xbox", "xbox"), X360("X360", "x360"), X_ONE("XOne", "xOne"), XSX("XSX", "xsx"),
    PC("PC", "pc"), MOBILE("Mobile", "mobile"), OUTRO("Outro", "outro");

    val isSteam get()       = this == STEAM
    val isPlayStation get() = this in listOf(PS1, PS2, PS3, PS4, PS5, PSP, PS_VITA)
    val isNintendo get()    = this in listOf(NES, SNES, N64, GCN, WII, WII_U, GBA, DS, N3DS, NS, NS2)
    val isXbox get()        = this in listOf(XBOX, X360, X_ONE, XSX)

    val color: Color get() = when {
        isSteam       -> Color(0xFF1B2838)
        isPlayStation -> Color(0xFF00439C)
        isNintendo    -> Color(0xFFE4000F)
        isXbox        -> Color(0xFF107C10)
        else          -> Color(0xFF37474F)
    }

    val familyLabel: String get() = when {
        isSteam       -> "Steam"
        isPlayStation -> "PlayStation"
        isNintendo    -> "Nintendo"
        isXbox        -> "Xbox"
        this == PC    -> "PC"
        this == MOBILE -> "Mobile"
        else          -> "Outro"
    }

    companion object {
        fun fromDb(value: String): GameConsole? =
            entries.firstOrNull { it.dbValue == value }
    }
}
