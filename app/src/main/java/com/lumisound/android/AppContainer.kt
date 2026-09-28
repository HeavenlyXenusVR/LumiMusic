package com.lumisound.android

import android.content.Context
import com.lumisound.android.bridge.AccountRepository
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.LoudnessGainStore
import com.lumisound.android.bridge.TokenStore
import com.lumisound.android.cloud.CloudImportService
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.playback.PlayHistoryLogger
import com.lumisound.android.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency graph, deliberately: one app-scoped object with lazy
 * singletons is the whole requirement here, and an annotation processor buys
 * nothing at this size while costing a build-time dependency on every module.
 */
class AppContainer(private val context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val config by lazy { BridgeConfig(context) }
    val tokenStore by lazy { TokenStore(context) }
    val loudnessGains by lazy { LoudnessGainStore() }
    val http by lazy { BridgeHttp(config, tokenStore, loudnessGains) }
    val database by lazy { LumiDatabase.build(context) }

    val account by lazy { AccountRepository(http, tokenStore) }
    val cloudImport by lazy { CloudImportService(http, database) }
    val player by lazy { PlayerController(context, config, scope) }

    private val historyLogger by lazy { PlayHistoryLogger(http, tokenStore, player, scope) }

    fun onAppStart() {
        player.connect()
        historyLogger.start()
        scope.launch { account.restoreSession() }
    }
}
