package com.imsharman.liber.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imsharman.liber.data.local.entity.BookWithCollections
import com.imsharman.liber.data.local.entity.CollectionEntity

@Composable
fun ManageBookCollectionsDialog(
    bookWithCollections: BookWithCollections,
    allCollections: List<CollectionEntity>,
    onToggleCollection: (collectionId: Long, assigned: Boolean) -> Unit,
    onCreateAndAssignCollection: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    val assignedIds = remember(bookWithCollections.collections) {
        bookWithCollections.collections.map { it.id }.toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collections for “${bookWithCollections.book.title}”") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New collection shelf") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            onCreateAndAssignCollection(newName.trim())
                            newName = ""
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()

                if (allCollections.isEmpty()) {
                    Text(
                        text = "No custom collections yet. Create shelves like “Sci-Fi”, “Study”, or “Favorites 2026” above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    allCollections.forEach { col ->
                        val checked = col.id in assignedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCollection(col.id, !checked) }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleCollection(col.id, it) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = col.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
