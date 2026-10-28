package com.rpmgauge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rpmgauge.app.ui.MainScreen
import com.rpmgauge.app.ui.RpmGaugeTheme
import com.rpmgauge.app.ui.SessionDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RpmGaugeTheme {
                val viewModel: MainViewModel = viewModel()
                val sessions by viewModel.sessions.collectAsStateWithLifecycle()
                var openSessionId by rememberSaveable { mutableStateOf<String?>(null) }

                // Android silences the mic for apps in the background, so end the
                // measurement instead of logging stale readings (but not on rotation).
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    if (!isChangingConfigurations) viewModel.stop()
                }

                val openSession = sessions.find { it.id == openSessionId }
                if (openSession != null) {
                    BackHandler { openSessionId = null }
                    SessionDetailScreen(session = openSession, onBack = { openSessionId = null })
                } else {
                    MainScreen(viewModel = viewModel, onOpenSession = { openSessionId = it })
                }
            }
        }
    }
}
