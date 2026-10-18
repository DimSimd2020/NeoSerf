package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BrowserSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun addAndObserveBookmark_authenticatedOwner_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = FirestoreBrowserSyncRepository(firestore, auth)

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.addBookmark(
        title = "Kotlin Official",
        url = "https://kotlinlang.org",
        folder = "Разработка"
      )
    }
    assertTrue(createResult.isSuccess)
    val bookmarkId = createResult.getOrThrow()

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeBookmarks(aliceUid).first { list -> list.any { it.id == bookmarkId } }
    }
    assertTrue(emitted.any { it.id == bookmarkId && it.title == "Kotlin Official" })
  }

  @Test
  fun saveAndObservePassword_authenticatedOwner_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = FirestoreBrowserSyncRepository(firestore, auth)

    val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.savePasswordEntry(
        siteDomain = "github.com",
        siteTitle = "GitHub",
        username = "alice@example.com",
        encryptedPassword = "AES_GCM_ENCRYPTED_PAYLOAD",
        notes = "Рабочий аккаунт"
      )
    }
    assertTrue(saveResult.isSuccess)
    val passwordId = saveResult.getOrThrow()

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observePasswords(aliceUid).first { list -> list.any { it.id == passwordId } }
    }
    assertTrue(emitted.any { it.id == passwordId && it.siteDomain == "github.com" })
  }

  @Test
  fun getBookmarkById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = FirestoreBrowserSyncRepository(firestore, auth)
    val bookmarkId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.addBookmark("Private Doc", "https://secret.example.com", "Личное").getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = FirestoreBrowserSyncRepository(firestore, auth)
    val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getBookmarkById(aliceUid, bookmarkId)
    }
    assertTrue(crossReadResult.isFailure)
    val ex = crossReadResult.exceptionOrNull() as? FirebaseFirestoreException
    assertNotNull(ex)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun observeBookmarks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repository = FirestoreBrowserSyncRepository(firestore, auth)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeBookmarks(aliceUid).first()
      }
      fail("Expected FirebaseFirestoreException PERMISSION_DENIED for unauthenticated caller")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice@neoserf.test"
    const val BOB_EMAIL = "bob@neoserf.test"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
