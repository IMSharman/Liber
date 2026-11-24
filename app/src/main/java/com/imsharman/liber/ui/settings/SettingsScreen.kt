package com.imsharman.liber.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.imsharman.liber.AppContainer
import com.imsharman.liber.data.storage.SandboxAuditInfo
import com.imsharman.liber.domain.model.AppThemeMode
import com.imsharman.liber.domain.model.ReaderSettings
import com.imsharman.liber.ui.components.formatFileSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val prefs = container.preferencesRepository
    val settings by prefs.readerSettingsFlow.collectAsState(initial = ReaderSettings())
    val linkedFolders by container.libraryRepository.observeLinkedFolders().collectAsState(initial = emptyList())

    var auditInfo by remember { mutableStateOf(SandboxAuditInfo(0, 0L, 0, 0L)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSyncBusy by remember { mutableStateOf(false) }

    var webDavUrl by remember(settings.webDavUrl) { mutableStateOf(settings.webDavUrl) }
    var webDavUser by remember(settings.webDavUsername) { mutableStateOf(settings.webDavUsername) }
    var webDavPass by remember(settings.webDavPassword) { mutableStateOf(settings.webDavPassword) }

    LaunchedEffect(Unit) {
        auditInfo = container.sandboxManager.getAuditInfo()
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isSyncBusy = true
                container.backupAndSyncManager.exportLocalBackupZip(uri)
                    .onSuccess { count ->
                        statusMessage = "Saved backup archive ($count books, shelves, highlights & covers)."
                    }
                    .onFailure { err ->
                        statusMessage = "Backup failed: ${err.message}"
                    }
                isSyncBusy = false
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isSyncBusy = true
                container.backupAndSyncManager.restoreLocalBackupZip(uri)
                    .onSuccess { msg ->
                        statusMessage = msg
                        auditInfo = container.sandboxManager.getAuditInfo()
                    }
                    .onFailure { err ->
                        statusMessage = "Restore failed: ${err.message}"
                    }
                isSyncBusy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings, Privacy & Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            statusMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 1. App Appearance & Daily Goal
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Appearance & Goals", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.appThemeMode == mode,
                                    onClick = {
                                        scope.launch {
                                            prefs.updateReaderSettings { it.copy(appThemeMode = mode) }
                                        }
                                    },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Daily Reading Goal: ${settings.dailyGoalMinutes} minutes",
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = settings.dailyGoalMinutes.toFloat(),
                            onValueChange = { v ->
                                scope.launch {
                                    prefs.updateReaderSettings { it.copy(dailyGoalMinutes = v.toInt()) }
                                }
                            },
                            valueRange = 5f..120f,
                            steps = 22
                        )
                    }
                }
            }

            // 2. Strict Privacy & Internal Sandbox Audit
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Strict File Isolation & Privacy", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "• Zero device-wide storage or photo permissions declared.\n" +
                                "• Liber NEVER creates public `Covers` folders on your storage or touches images you didn't give it.\n" +
                                "• Book covers are extracted strictly from inside book files into Liber's private app sandbox (`filesDir/covers`).",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Scoped Folders Linked: ${linkedFolders.size}  •  " +
                                "Internal Covers: ${auditInfo.coverCount} (${formatFileSize(auditInfo.coverBytes)})  •  " +
                                "Custom Fonts: ${auditInfo.customFontCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Strict Offline-Only Mode", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Blocks all network calls (Open Library lookup & WebDAV sync)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.offlineOnlyMode,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        prefs.updateReaderSettings { it.copy(offlineOnlyMode = enabled) }
                                    }
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    container.sandboxManager.clearAllInternalCovers()
                                    auditInfo = container.sandboxManager.getAuditInfo()
                                    statusMessage = "Cleared internal cover & chapter illustration cache."
                                }
                            }
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Internal Cover Cache")
                        }
                    }
                }
            }

            // 3. Local Backup & Restore (.liberbackup)
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Local Archive Backup & Restore", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Export your reading progress, shelves, collections, multi-color highlights, notes, bookmarks, stats, and covers to a portable `.liberbackup.zip` file.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = !isSyncBusy,
                                onClick = { createBackupLauncher.launch("liber_backup.liberbackup.zip") }
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Backup")
                            }
                            FilledTonalButton(
                                enabled = !isSyncBusy,
                                onClick = { restoreBackupLauncher.launch(arrayOf("application/zip", "*/*")) }
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore Backup")
                            }
                        }
                    }
                }
            }

            // 4. WebDAV Cross-Device Sync
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("WebDAV Cross-Device Sync", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sync reading positions, shelves, collections, highlights, notes, and reading sessions across devices using Nextcloud, Koofr, Synology, or any WebDAV server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = webDavUrl,
                            onValueChange = { webDavUrl = it },
                            label = { Text("WebDAV Folder URL") },
                            placeholder = { Text("https://cloud.example.com/remote.php/dav/files/user/Liber") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = webDavUser,
                                onValueChange = { webDavUser = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = webDavPass,
                                onValueChange = { webDavPass = it },
                                label = { Text("App Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            enabled = !isSyncBusy,
                            onClick = {
                                scope.launch {
                                    isSyncBusy = true
                                    prefs.updateReaderSettings {
                                        it.copy(
                                            webDavUrl = webDavUrl.trim(),
                                            webDavUsername = webDavUser.trim(),
                                            webDavPassword = webDavPass
                                        )
                                    }
                                    container.backupAndSyncManager.syncWithWebDav()
                                        .onSuccess { msg -> statusMessage = msg }
                                        .onFailure { err -> statusMessage = "Sync error: ${err.message}" }
                                    isSyncBusy = false
                                }
                            }
                        ) {
                            if (isSyncBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save & Sync Now")
                        }
                    }
                }
            }
        }
    }
}
