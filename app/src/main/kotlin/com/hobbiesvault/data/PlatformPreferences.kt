package com.hobbiesvault.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hobbiesvault.model.GameConsole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.platformDataStore by preferencesDataStore(name = "platform_prefs")

/**
 * Quais plataformas de jogo aparecem como opção de filtro na tela de Jogos. Por padrão todas as
 * plataformas do enum [GameConsole] estão habilitadas; o usuário personaliza em
 * Configurações → Plataformas.
 */
object PlatformPreferences {
    private val VISIBLE_CONSOLES_KEY = stringSetPreferencesKey("visible_consoles")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    private val _visibleConsoles = MutableStateFlow(GameConsole.entries.toSet())
    val visibleConsoles: StateFlow<Set<GameConsole>> = _visibleConsoles

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            appContext.platformDataStore.data
                .map { prefs ->
                    prefs[VISIBLE_CONSOLES_KEY]
                        ?.mapNotNull { GameConsole.fromDb(it) }
                        ?.toSet()
                        ?: GameConsole.entries.toSet()
                }
                .collect { _visibleConsoles.value = it }
        }
    }

    fun setVisible(console: GameConsole, visible: Boolean) {
        val updated = if (visible) _visibleConsoles.value + console else _visibleConsoles.value - console
        _visibleConsoles.value = updated
        scope.launch {
            appContext.platformDataStore.edit { prefs ->
                prefs[VISIBLE_CONSOLES_KEY] = updated.map { it.dbValue }.toSet()
            }
        }
    }
}
