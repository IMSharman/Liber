package com.imsharman.liber

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.ui.navigation.LiberNavHost
import com.imsharman.liber.ui.theme.LiberTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var externalBookIdToOpen by mutableStateOf<Long?>(null)
    private var interceptVolumeKeysForReader: Boolean = false
    private val _volumeTurnEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as LiberApp).container
        handleIncomingBookIntent(intent, appContainer)

        setContent {
            val settings by appContainer.preferencesRepository.readerSettingsFlow.collectAsState(
                initial = ReaderSettings()
            )
            LiberTheme(themeMode = settings.appThemeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LiberNavHost(
                        container = appContainer,
                        externalOpenBookId = externalBookIdToOpen,
                        onConsumedExternalBook = { externalBookIdToOpen = null },
                        volumeKeyTurnFlow = _volumeTurnEvents.asSharedFlow(),
                        onSetInterceptVolumeKeys = { interceptVolumeKeysForReader = it }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val appContainer = (application as LiberApp).container
        handleIncomingBookIntent(intent, appContainer)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (interceptVolumeKeysForReader) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    _volumeTurnEvents.tryEmit(+1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    _volumeTurnEvents.tryEmit(-1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (interceptVolumeKeysForReader &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleIncomingBookIntent(intent: Intent?, container: AppContainer) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            lifecycleScope.launch {
                val bookId = container.libraryRepository.importOrOpenSingleUri(uri)
                if (bookId != null) {
                    externalBookIdToOpen = bookId
                }
            }
        }
    }
}
