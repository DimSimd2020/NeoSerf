package com.example

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w360dp-h720dp-xhdpi")
class BrowserUiRegressionTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun hardwareEnterDoesNotOpenSearchEngineDropdown() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").performClick()
    composeRule.onNodeWithTag("omnibox_input").performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").performKeyInput {
      pressKey(Key.Enter)
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Универсальный поиск Google").assertDoesNotExist()
    composeRule.onNodeWithTag("search_engine_selector").assertIsDisplayed()
  }

  @Test
  fun imeGoDoesNotOpenSearchEngineDropdown() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").performImeAction()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Универсальный поиск Google").assertDoesNotExist()
  }

  @Test
  fun tabsSheetTitleAndIncognitoChipFitNarrowWidth() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("nav_tabs_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Открытые вкладки").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_incognito").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_regular").assertIsDisplayed()
    composeRule.onNodeWithText("Инкогнито (0)", substring = true).assertIsDisplayed()
  }

  @Test
  fun shieldCardTitleDoesNotBreakMidWord() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("shield_summary_card").performScrollTo()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("shield_summary_title", useUnmergedTree = true).assertIsDisplayed()
    composeRule.onNodeWithTag("shield_summary_title", useUnmergedTree = true)
      .assertTextEquals("Щит приватности NeoSerf")

    val layouts = mutableListOf<TextLayoutResult>()
    val getter = composeRule.onNodeWithTag("shield_summary_title", useUnmergedTree = true)
      .fetchSemanticsNode()
      .config[SemanticsActions.GetTextLayoutResult]
    assertTrue(getter.action!!.invoke(layouts))
    val layout = layouts.first()
    val text = layout.layoutInput.text.text
    val word = "приватности"
    val start = text.indexOf(word)
    assertTrue(start >= 0)
    val startLine = layout.getLineForOffset(start)
    val endLine = layout.getLineForOffset(start + word.length - 1)
    assertEquals("Title must not break inside '$word'", startLine, endLine)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w720dp-h1280dp-mdpi")
class BrowserUiRegressionWideTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun tabsSheetFits720DpWidth() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("nav_tabs_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Открытые вкладки").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_incognito").assertIsDisplayed()
    composeRule.onNodeWithText("Инкогнито (0)", substring = true).assertIsDisplayed()
  }
}
