package com.hobbiesvault

import android.app.Application
import com.hobbiesvault.data.db.DB
import com.hobbiesvault.debug.DebugSeeder
import com.hobbiesvault.service.ApiServices
import com.hobbiesvault.service.GameDatasetImporter
import com.hobbiesvault.service.NotificationHelper
import com.hobbiesvault.worker.CacheUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HobbiesVaultApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DB.init(this)
        NotificationHelper.init(this)
        appScope.launch {
            ApiServices.init(this@HobbiesVaultApp)
            // Import GiantBomb dataset on first run (no-op if already done)
            GameDatasetImporter.importIfNeeded(this@HobbiesVaultApp)
            // Dados fake para testes de usabilidade — só roda em build de debug e só se a
            // biblioteca estiver vazia.
            if (BuildConfig.DEBUG) DebugSeeder.seedIfEmpty(DB.repo)
        }
        CacheUpdateWorker.schedule(this)
    }
}
