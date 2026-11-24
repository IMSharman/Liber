package com.imsharman.liber.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.imsharman.liber.domain.model.LibrarySortOrder
import com.imsharman.liber.domain.model.LibraryViewMode
import com.imsharman.liber.domain.model.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.liberDataStore: DataStore<Preferences> by preferencesDataStore(name = "liber_preferences")

class ReaderPreferencesRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val READER_SETTINGS_JSON = stringPreferencesKey("reader_settings_json")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val LIBRARY_SORT_ORDER = stringPreferencesKey("library_sort_order")
    }

    val readerSettingsFlow: Flow<ReaderSettings> = context.liberDataStore.data.map { prefs ->
        val raw = prefs[Keys.READER_SETTINGS_JSON]
        if (raw.isNullOrBlank()) {
            ReaderSettings()
        } else {
            runCatching { json.decodeFromString<ReaderSettings>(raw) }.getOrDefault(ReaderSettings())
        }
    }

    val libraryViewModeFlow: Flow<LibraryViewMode> = context.liberDataStore.data.map { prefs ->
        val raw = prefs[Keys.LIBRARY_VIEW_MODE]
        runCatching { LibraryViewMode.valueOf(raw ?: "") }.getOrDefault(LibraryViewMode.DETAILED_LIST)
    }

    val librarySortOrderFlow: Flow<LibrarySortOrder> = context.liberDataStore.data.map { prefs ->
        val raw = prefs[Keys.LIBRARY_SORT_ORDER]
        runCatching { LibrarySortOrder.valueOf(raw ?: "") }.getOrDefault(LibrarySortOrder.LAST_READ)
    }

    suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        context.liberDataStore.edit { prefs ->
            val current = prefs[Keys.READER_SETTINGS_JSON]?.let {
                runCatching { json.decodeFromString<ReaderSettings>(it) }.getOrNull()
            } ?: ReaderSettings()
            val updated = transform(current)
            prefs[Keys.READER_SETTINGS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun replaceReaderSettings(settings: ReaderSettings) {
        context.liberDataStore.edit { prefs ->
            prefs[Keys.READER_SETTINGS_JSON] = json.encodeToString(settings)
        }
    }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        context.liberDataStore.edit { prefs ->
            prefs[Keys.LIBRARY_VIEW_MODE] = mode.name
        }
    }

    suspend fun setLibrarySortOrder(order: LibrarySortOrder) {
        context.liberDataStore.edit { prefs ->
            prefs[Keys.LIBRARY_SORT_ORDER] = order.name
        }
    }
}
