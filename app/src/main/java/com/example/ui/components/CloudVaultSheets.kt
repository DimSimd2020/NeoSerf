package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BookmarkItem
import com.example.data.model.HistoryEntry
import com.example.data.model.PasswordEntry
import com.example.domain.PasswordCryptoManager
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookmarksSheet(
  bookmarksState: UiState<List<BookmarkItem>>,
  currentUrl: String,
  currentTitle: String,
  onOpenUrl: (String) -> Unit,
  onAddBookmark: (title: String, url: String, folder: String) -> Unit,
  onDeleteBookmark: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedFolder by remember { mutableStateOf("Все") }
  var showAddDialog by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.bookmarks_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Button(
          onClick = { showAddDialog = true },
          modifier = Modifier.testTag("add_bookmark_dialog_button")
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = stringResource(R.string.action_save))
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      when (bookmarksState) {
        is UiState.Loading -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }

        is UiState.Error -> {
          Text(
            text = bookmarksState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
          )
        }

        is UiState.Success -> {
          val allItems = bookmarksState.data
          val folders = listOf("Все") + allItems.map { it.folder }.distinct()

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            folders.forEach { folder ->
              FilterChip(
                selected = selectedFolder == folder,
                onClick = { selectedFolder = folder },
                label = { Text(folder) }
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          val filtered = if (selectedFolder == "Все") {
            allItems
          } else {
            allItems.filter { it.folder == selectedFolder }
          }

          if (filtered.isEmpty()) {
            Text(
              text = stringResource(R.string.bookmarks_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 24.dp)
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(filtered, key = { it.id }) { bm ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable {
                      onDismiss()
                      onOpenUrl(bm.url)
                    }
                    .testTag("bookmark_item_${bm.id}")
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = bm.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = bm.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = bm.folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }

                    IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))
    }
  }

  if (showAddDialog) {
    var titleInput by remember { mutableStateOf(currentTitle.takeIf { currentUrl.isNotBlank() } ?: "") }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var folderInput by remember { mutableStateOf("Избранное") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text(stringResource(R.string.bookmarks_add_current)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            label = { Text(stringResource(R.string.speed_dial_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text(stringResource(R.string.speed_dial_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = folderInput,
            onValueChange = { folderInput = it },
            label = { Text(stringResource(R.string.bookmarks_folder_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (urlInput.isNotBlank()) {
              showAddDialog = false
              onAddBookmark(titleInput.ifBlank { urlInput }, urlInput, folderInput)
            }
          }
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordVaultSheet(
  passwordsState: UiState<List<PasswordEntry>>,
  currentDomain: String,
  onDecryptPassword: (PasswordEntry) -> String,
  onSavePassword: (siteDomain: String, siteTitle: String, username: String, plainPassword: String, notes: String) -> Unit,
  onDeletePassword: (String) -> Unit,
  onAutofillEntry: (PasswordEntry) -> Unit,
  onDismiss: () -> Unit,
) {
  val clipboardManager = LocalClipboardManager.current
  val revealedIds = remember { mutableStateMapOf<String, Boolean>() }
  var showAddPasswordDialog by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = stringResource(R.string.passwords_title),
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(R.string.passwords_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary
            )
          }
        }

        Button(
          onClick = { showAddPasswordDialog = true },
          modifier = Modifier.testTag("add_password_button")
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = stringResource(R.string.action_save))
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      when (passwordsState) {
        is UiState.Loading -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }

        is UiState.Error -> {
          Text(
            text = passwordsState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
          )
        }

        is UiState.Success -> {
          val entries = passwordsState.data
          if (entries.isEmpty()) {
            Text(
              text = stringResource(R.string.passwords_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 24.dp)
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(entries, key = { it.id }) { entry ->
                val isRevealed = revealedIds[entry.id] == true
                val decrypted = remember(entry.id, entry.encryptedPassword, isRevealed) {
                  if (isRevealed) onDecryptPassword(entry) else "••••••••••••"
                }

                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .testTag("password_item_${entry.id}")
                ) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = entry.siteTitle,
                          style = MaterialTheme.typography.titleMedium,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = entry.siteDomain,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.primary
                        )
                      }

                      IconButton(onClick = { onDeletePassword(entry.id) }) {
                        Icon(
                          imageVector = Icons.Default.Delete,
                          contentDescription = stringResource(R.string.action_delete),
                          tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                      text = "Логин: ${entry.username}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = "Пароль: $decrypted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                      )

                      Row {
                        IconButton(
                          onClick = { revealedIds[entry.id] = !isRevealed }
                        ) {
                          Icon(
                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.passwords_show_hide),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                          )
                        }
                        IconButton(
                          onClick = {
                            clipboardManager.setText(AnnotatedString(onDecryptPassword(entry)))
                          }
                        ) {
                          Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.passwords_copy_action),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                          )
                        }
                      }
                    }

                    OutlinedButton(
                      onClick = {
                        onDismiss()
                        onAutofillEntry(entry)
                      },
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(text = stringResource(R.string.passwords_autofill_action))
                    }
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))
    }
  }

  if (showAddPasswordDialog) {
    var domain by remember { mutableStateOf(currentDomain) }
    var title by remember { mutableStateOf(currentDomain) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAddPasswordDialog = false },
      title = { Text(stringResource(R.string.passwords_add)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text(stringResource(R.string.passwords_site_domain)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.passwords_site_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.passwords_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.passwords_password)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedButton(
            onClick = { password = PasswordCryptoManager.generateStrongPassword(16, true) },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(
              imageVector = Icons.Default.AutoFixHigh,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.passwords_generate))
          }
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.passwords_notes)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (username.isNotBlank() && password.isNotBlank()) {
              showAddPasswordDialog = false
              onSavePassword(
                domain.ifBlank { "neoserf.app" },
                title.ifBlank { domain.ifBlank { "NeoSerf" } },
                username,
                password,
                notes
              )
            }
          }
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddPasswordDialog = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
  historyState: UiState<List<HistoryEntry>>,
  onOpenUrl: (String) -> Unit,
  onDeleteEntry: (String) -> Unit,
  onClearAll: () -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        TextButton(
          onClick = onClearAll,
          modifier = Modifier.testTag("clear_history_button")
        ) {
          Text(
            text = stringResource(R.string.history_clear_all),
            color = MaterialTheme.colorScheme.error
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text(stringResource(R.string.history_search_hint)) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("history_search_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      when (historyState) {
        is UiState.Loading -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }

        is UiState.Error -> {
          Text(
            text = historyState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
          )
        }

        is UiState.Success -> {
          val filtered = historyState.data.filter {
            searchQuery.isBlank() ||
              it.title.contains(searchQuery, ignoreCase = true) ||
              it.url.contains(searchQuery, ignoreCase = true)
          }

          if (filtered.isEmpty()) {
            Text(
              text = stringResource(R.string.history_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 24.dp)
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(filtered, key = { it.id }) { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable {
                      onDismiss()
                      onOpenUrl(item.url)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.title,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = item.url,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  IconButton(onClick = { onDeleteEntry(item.id) }) {
                    Icon(
                      imageVector = Icons.Default.Delete,
                      contentDescription = stringResource(R.string.action_delete),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))
    }
  }
}
