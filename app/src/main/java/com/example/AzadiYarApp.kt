package com.example

import android.app.Application
import com.example.config.collector.ConfigSourceManager
import com.example.config.collector.ProxyManager
import com.example.connection.ConnectionManager
import com.example.connection.SmartConfigSelector
import com.example.diagnostics.NetworkDiagnostics
import com.example.storage.AzadiDatabase
import com.example.storage.AzadiRepository
import com.example.testing.ConfigTester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AzadiYarApp : Application() {

    lateinit var database: AzadiDatabase
        private set

    lateinit var repository: AzadiRepository
        private set

    lateinit var configSourceManager: ConfigSourceManager
        private set

    lateinit var configTester: ConfigTester
        private set

    lateinit var smartConfigSelector: SmartConfigSelector
        private set

    lateinit var connectionManager: ConnectionManager
        private set

    lateinit var networkDiagnostics: NetworkDiagnostics
        private set

    lateinit var proxyManager: ProxyManager
        private set

    lateinit var v2raySettingsManager: com.example.config.settings.V2raySettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        v2raySettingsManager = com.example.config.settings.V2raySettingsManager(this)
        database = AzadiDatabase.getInstance(this)
        repository = AzadiRepository(database)
        configSourceManager = ConfigSourceManager(repository)
        configTester = ConfigTester(repository)
        smartConfigSelector = SmartConfigSelector(repository)
        connectionManager = ConnectionManager(this, repository, smartConfigSelector, configTester)
        networkDiagnostics = NetworkDiagnostics(this, repository)
        proxyManager = ProxyManager(repository)

        CoroutineScope(Dispatchers.IO).launch {
            repository.ensureDefaultSourcesPopulated()
            repository.ensureBaseConfigsAndProxiesPopulated()
        }
    }

    companion object {
        lateinit var instance: AzadiYarApp
            private set
    }
}
