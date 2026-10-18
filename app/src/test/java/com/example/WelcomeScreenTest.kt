package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.model.AppTheme
import com.example.data.model.SearchEngine
import com.example.ui.WelcomeScreen
import com.example.ui.theme.NeoSerfTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w360dp-h720dp-xhdpi")
class WelcomeScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun twoStepsSaveThemeAndSearchEngineThenFinish() {
    var theme by mutableStateOf(AppTheme.SYSTEM)
    var engine by mutableStateOf(SearchEngine.GOOGLE)
    var finished = 0
    composeRule.setContent {
      NeoSerfTheme(theme) {
        WelcomeScreen(theme, engine, { theme = it }, { engine = it }, { finished++ })
      }
    }
    composeRule.onNodeWithTag("welcome_back").assertDoesNotExist()
    composeRule.onNodeWithTag("welcome_next").assertTextContains("Далее")
    composeRule.onNodeWithTag("welcome_theme_DARK").performScrollTo().performClick()
    assertEquals(AppTheme.DARK, theme)
    composeRule.onNodeWithTag("welcome_theme_DARK").assertIsSelected()

    composeRule.onNodeWithTag("welcome_next").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("welcome_theme_DARK").assertDoesNotExist()
    composeRule.onNodeWithTag("welcome_next").assertTextContains("Начать")
    composeRule.onNodeWithTag("welcome_engine_Yandex").performScrollTo().performClick()
    assertEquals(SearchEngine.YANDEX, engine)

    composeRule.onNodeWithTag("welcome_back").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("welcome_theme_DARK").assertIsDisplayed()
    composeRule.onNodeWithTag("welcome_next").performClick()
    composeRule.waitForIdle()
    assertEquals(0, finished)
    composeRule.onNodeWithTag("welcome_next").performClick()
    assertEquals(1, finished)
  }

  @Test fun onboardingFlagIsStored() = runBlocking {
    val repository = BrowserPreferencesRepository(ApplicationProvider.getApplicationContext())
    repository.setOnboardingCompleted()
    assertTrue(repository.preferencesFlow.first().onboardingCompleted)
  }
}
