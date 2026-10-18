package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BrowserPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LocationPromptTest {
  @Test fun locationIsRequestedOnceAndOnlyForTheWeather() = runBlocking {
    val repository = BrowserPreferencesRepository(ApplicationProvider.getApplicationContext())
    try {
      repository.resetLocationPrompt()
      repository.setShowWeatherWidget(false)
      assertFalse(repository.claimLocationPrompt())
      repository.setShowWeatherWidget(true)
      assertTrue(repository.claimLocationPrompt())
      assertFalse(repository.claimLocationPrompt())
    } finally {
      repository.setShowWeatherWidget(true)
    }
  }
}
