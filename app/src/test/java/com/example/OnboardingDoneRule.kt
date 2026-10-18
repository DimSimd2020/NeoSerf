package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BrowserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** Marks the first-launch screen as done before MainActivity starts, so browser tests land on the browser. */
class OnboardingDoneRule : ExternalResource() {
  override fun before() {
    runBlocking { BrowserPreferencesRepository(ApplicationProvider.getApplicationContext()).setOnboardingCompleted() }
  }
}
