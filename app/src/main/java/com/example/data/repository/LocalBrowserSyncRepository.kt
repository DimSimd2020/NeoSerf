package com.example.data.repository

import com.example.data.model.BookmarkItem
import com.example.data.model.HistoryEntry
import com.example.data.model.PasswordEntry
import com.example.data.model.SpeedDialTile
import com.example.data.remote.FirebaseAvailability
import com.google.firebase.Timestamp
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory bookmarks, history, passwords, and speed dial for guest mode when Firebase
 * is not initialized (no google-services.json / Default FirebaseApp).
 */
class LocalBrowserSyncRepository(
  private val guestUserId: String = FirebaseAvailability.LOCAL_GUEST_USER_ID,
) : BrowserSyncRepository {

  private val bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
  private val passwords = MutableStateFlow<List<PasswordEntry>>(emptyList())
  private val history = MutableStateFlow<List<HistoryEntry>>(emptyList())
  private val speedDial = MutableStateFlow<List<SpeedDialTile>>(emptyList())

  override fun observeBookmarks(userId: String): Flow<List<BookmarkItem>> = bookmarks.asStateFlow()

  override suspend fun addBookmark(title: String, url: String, folder: String): Result<String> {
    val id = UUID.randomUUID().toString()
    val now = Timestamp.now()
    bookmarks.update { current ->
      current + BookmarkItem(
        id = id,
        userId = guestUserId,
        title = title.trim().take(200).ifEmpty { url.take(200) },
        url = url.trim().take(2048),
        folder = folder.trim().take(80).ifEmpty { "Общее" },
        createdAt = now,
        updatedAt = now,
      )
    }
    return Result.success(id)
  }

  override suspend fun getBookmarkById(targetUserId: String, bookmarkId: String): Result<BookmarkItem?> {
    return Result.success(bookmarks.value.firstOrNull { it.id == bookmarkId })
  }

  override suspend fun deleteBookmark(userId: String, bookmarkId: String): Result<Unit> {
    bookmarks.update { current -> current.filterNot { it.id == bookmarkId } }
    return Result.success(Unit)
  }

  override fun observePasswords(userId: String): Flow<List<PasswordEntry>> = passwords.asStateFlow()

  override suspend fun savePasswordEntry(
    siteDomain: String,
    siteTitle: String,
    username: String,
    encryptedPassword: String,
    notes: String,
  ): Result<String> {
    val id = UUID.randomUUID().toString()
    val now = Timestamp.now()
    passwords.update { current ->
      current + PasswordEntry(
        id = id,
        userId = guestUserId,
        siteDomain = siteDomain.trim().take(255).ifEmpty { "neoserf.local" },
        siteTitle = siteTitle.trim().take(200).ifEmpty { siteDomain.take(200) },
        username = username.trim().take(200),
        encryptedPassword = encryptedPassword.take(1024),
        notes = notes.trim().take(500),
        createdAt = now,
        updatedAt = now,
      )
    }
    return Result.success(id)
  }

  override suspend fun deletePasswordEntry(userId: String, passwordId: String): Result<Unit> {
    passwords.update { current -> current.filterNot { it.id == passwordId } }
    return Result.success(Unit)
  }

  override fun observeHistory(userId: String): Flow<List<HistoryEntry>> = history.asStateFlow()

  override suspend fun recordHistoryVisit(
    title: String,
    url: String,
    searchEngine: String,
  ): Result<String> {
    val id = UUID.randomUUID().toString()
    val now = Timestamp.now()
    history.update { current ->
      current + HistoryEntry(
        id = id,
        userId = guestUserId,
        title = title.trim().take(300).ifEmpty { url.take(300) },
        url = url.trim().take(2048),
        searchEngine = searchEngine.trim().take(50).ifEmpty { "Google" },
        createdAt = now,
        updatedAt = now,
      )
    }
    return Result.success(id)
  }

  override suspend fun deleteHistoryEntry(userId: String, historyId: String): Result<Unit> {
    history.update { current -> current.filterNot { it.id == historyId } }
    return Result.success(Unit)
  }

  override suspend fun clearAllHistory(userId: String, entries: List<HistoryEntry>): Result<Unit> {
    history.value = emptyList()
    return Result.success(Unit)
  }

  override fun observeSpeedDial(userId: String): Flow<List<SpeedDialTile>> = speedDial.asStateFlow()

  override suspend fun addSpeedDialTile(
    title: String,
    url: String,
    category: String,
    position: Int,
  ): Result<String> {
    val id = UUID.randomUUID().toString()
    val now = Timestamp.now()
    speedDial.update { current ->
      current + SpeedDialTile(
        id = id,
        userId = guestUserId,
        title = title.trim().take(100).ifEmpty { "Сайт" },
        url = url.trim().take(2048),
        category = category.trim().take(60).ifEmpty { "Быстрый доступ" },
        position = position.coerceIn(0, 100).toLong(),
        createdAt = now,
        updatedAt = now,
      )
    }
    return Result.success(id)
  }

  override suspend fun deleteSpeedDialTile(userId: String, tileId: String): Result<Unit> {
    speedDial.update { current -> current.filterNot { it.id == tileId } }
    return Result.success(Unit)
  }
}
