package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BookmarkItem
import com.example.data.model.HistoryEntry
import com.example.data.model.PasswordEntry
import com.example.data.model.SpeedDialTile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class BrowserSyncRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    FirebaseAuth.getInstance()
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- BOOKMARKS ---

  fun observeBookmarks(userId: String): Flow<List<BookmarkItem>> = callbackFlow {
    val path = "users/$userId/bookmarks"
    val registration = db.collection("users").document(userId).collection("bookmarks")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, path)
          close(error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val items = snapshot.toObjects(
            BookmarkItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.createdAt?.seconds ?: 0L }
          trySend(items)
        }
      }
    awaitClose { registration.remove() }
  }

  suspend fun addBookmark(title: String, url: String, folder: String): Result<String> = runCatching {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("bookmarks")
    val docRef = colRef.document()
    val payload = mapOf(
      "userId" to uid,
      "title" to title.trim().take(200).ifEmpty { url.take(200) },
      "url" to url.trim().take(2048),
      "folder" to folder.trim().take(80).ifEmpty { "Общее" },
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun getBookmarkById(targetUserId: String, bookmarkId: String): Result<BookmarkItem?> = runCatching {
    val docRef = db.collection("users").document(targetUserId).collection("bookmarks").document(bookmarkId)
    try {
      docRef.get().await().toObject(BookmarkItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun deleteBookmark(userId: String, bookmarkId: String): Result<Unit> = runCatching {
    val docRef = db.collection("users").document(userId).collection("bookmarks").document(bookmarkId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- PASSWORDS ---

  fun observePasswords(userId: String): Flow<List<PasswordEntry>> = callbackFlow {
    val path = "users/$userId/passwords"
    val registration = db.collection("users").document(userId).collection("passwords")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, path)
          close(error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val items = snapshot.toObjects(
            PasswordEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
          trySend(items)
        }
      }
    awaitClose { registration.remove() }
  }

  suspend fun savePasswordEntry(
    siteDomain: String,
    siteTitle: String,
    username: String,
    encryptedPassword: String,
    notes: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("passwords").document()
    val payload = mapOf(
      "userId" to uid,
      "siteDomain" to siteDomain.trim().take(255).ifEmpty { "neoserf.local" },
      "siteTitle" to siteTitle.trim().take(200).ifEmpty { siteDomain.take(200) },
      "username" to username.trim().take(200),
      "encryptedPassword" to encryptedPassword.take(1024),
      "notes" to notes.trim().take(500),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deletePasswordEntry(userId: String, passwordId: String): Result<Unit> = runCatching {
    val docRef = db.collection("users").document(userId).collection("passwords").document(passwordId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- HISTORY ---

  fun observeHistory(userId: String): Flow<List<HistoryEntry>> = callbackFlow {
    val path = "users/$userId/history"
    val registration = db.collection("users").document(userId).collection("history")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, path)
          close(error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val items = snapshot.toObjects(
            HistoryEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.createdAt?.seconds ?: 0L }
          trySend(items)
        }
      }
    awaitClose { registration.remove() }
  }

  suspend fun recordHistoryVisit(
    title: String,
    url: String,
    searchEngine: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("history").document()
    val payload = mapOf(
      "userId" to uid,
      "title" to title.trim().take(300).ifEmpty { url.take(300) },
      "url" to url.trim().take(2048),
      "searchEngine" to searchEngine.trim().take(50).ifEmpty { "Google" },
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteHistoryEntry(userId: String, historyId: String): Result<Unit> = runCatching {
    val docRef = db.collection("users").document(userId).collection("history").document(historyId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun clearAllHistory(userId: String, entries: List<HistoryEntry>): Result<Unit> = runCatching {
    entries.forEach { item ->
      if (item.id.isNotBlank()) {
        deleteHistoryEntry(userId, item.id).getOrThrow()
      }
    }
  }

  // --- SPEED DIAL ---

  fun observeSpeedDial(userId: String): Flow<List<SpeedDialTile>> = callbackFlow {
    val path = "users/$userId/speed_dial"
    val registration = db.collection("users").document(userId).collection("speed_dial")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, path)
          close(error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val items = snapshot.toObjects(
            SpeedDialTile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedBy { it.position }
          trySend(items)
        }
      }
    awaitClose { registration.remove() }
  }

  suspend fun addSpeedDialTile(
    title: String,
    url: String,
    category: String,
    position: Int,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("speed_dial").document()
    val payload = mapOf(
      "userId" to uid,
      "title" to title.trim().take(100).ifEmpty { "Сайт" },
      "url" to url.trim().take(2048),
      "category" to category.trim().take(60).ifEmpty { "Быстрый доступ" },
      "position" to position.coerceIn(0, 100),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteSpeedDialTile(userId: String, tileId: String): Result<Unit> = runCatching {
    val docRef = db.collection("users").document(userId).collection("speed_dial").document(tileId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }
}
