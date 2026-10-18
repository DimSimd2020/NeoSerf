package com.example.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBrowserSyncRepositoryTest {

  @Test
  fun addAndObserveBookmark_withoutFirebase() = runBlocking {
    val repository = LocalBrowserSyncRepository()

    val result = repository.addBookmark(
      title = "Kotlin Official",
      url = "https://kotlinlang.org",
      folder = "Разработка"
    )
    assertTrue(result.isSuccess)
    val bookmarkId = result.getOrThrow()

    val emitted = repository.observeBookmarks("local-guest").first()
    assertTrue(emitted.any { it.id == bookmarkId && it.title == "Kotlin Official" })
  }

  @Test
  fun recordHistoryAndClear_withoutFirebase() = runBlocking {
    val repository = LocalBrowserSyncRepository()
    val visit = repository.recordHistoryVisit(
      title = "Example",
      url = "https://example.com",
      searchEngine = "Google"
    )
    assertTrue(visit.isSuccess)
    assertEquals(1, repository.observeHistory("local-guest").first().size)

    val cleared = repository.clearAllHistory("local-guest", emptyList())
    assertTrue(cleared.isSuccess)
    assertEquals(0, repository.observeHistory("local-guest").first().size)
  }

  @Test
  fun saveAndDeletePassword_withoutFirebase() = runBlocking {
    val repository = LocalBrowserSyncRepository()
    val saved = repository.savePasswordEntry(
      siteDomain = "github.com",
      siteTitle = "GitHub",
      username = "guest",
      encryptedPassword = "ciphertext",
      notes = ""
    )
    assertTrue(saved.isSuccess)
    val id = saved.getOrThrow()
    assertEquals(1, repository.observePasswords("local-guest").first().size)

    repository.deletePasswordEntry("local-guest", id)
    assertEquals(0, repository.observePasswords("local-guest").first().size)
  }
}
