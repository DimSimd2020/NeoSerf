package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class BookmarkItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val url: String = "",
  val folder: String = "Общее",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PasswordEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val siteDomain: String = "",
  val siteTitle: String = "",
  val username: String = "",
  val encryptedPassword: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HistoryEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val url: String = "",
  val searchEngine: String = "Google",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SpeedDialTile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val url: String = "",
  val category: String = "Быстрый доступ",
  val position: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
