package com.imsharman.liber

import android.app.Application
import com.imsharman.liber.data.export.BackupAndSyncManager
import com.imsharman.liber.data.local.LiberDatabase
import com.imsharman.liber.data.parser.BookParserDispatcher
import com.imsharman.liber.data.preferences.ReaderPreferencesRepository
import com.imsharman.liber.data.remote.OpenLibraryService
import com.imsharman.liber.data.repository.LibraryRepository
import com.imsharman.liber.data.storage.InternalSandboxManager
import com.imsharman.liber.data.storage.ScopedFolderScanner

class AppContainer(application: Application) {
    val database: LiberDatabase = LiberDatabase.getInstance(application)
    val preferencesRepository = ReaderPreferencesRepository(application)
    val sandboxManager = InternalSandboxManager(application)
    val folderScanner = ScopedFolderScanner(application)
    val parserDispatcher = BookParserDispatcher(application, sandboxManager)
    val openLibraryService = OpenLibraryService()

    val libraryRepository = LibraryRepository(
        context = application,
        db = database,
        sandboxManager = sandboxManager,
        parserDispatcher = parserDispatcher,
        folderScanner = folderScanner
    )

    val backupAndSyncManager = BackupAndSyncManager(
        context = application,
        db = database,
        prefs = preferencesRepository,
        sandboxManager = sandboxManager
    )
}

class LiberApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
