package com.example

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
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
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test fun hardwareEnterNavigatesWithoutOpeningSearchSettings() {
    composeRule.onNodeWithTag("omnibox_input").performClick().performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").assertTextContains("example.com", substring = true)
    composeRule.onNodeWithTag("omnibox_input").performKeyInput { pressKey(Key.Enter) }
    composeRule.onNodeWithTag("start_page").assertDoesNotExist()
    composeRule.onNodeWithTag("search_engine_selector").assertDoesNotExist()
    composeRule.onNodeWithTag("browser_overflow_menu").assertDoesNotExist()
    composeRule.onNodeWithTag("omnibox_input").assertTextContains("example.com", substring = true)
  }

  @Test fun imeGoNavigatesWithoutOpeningMenu() {
    composeRule.onNodeWithTag("omnibox_input").performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").performImeAction()
    composeRule.onNodeWithTag("browser_overflow_menu").assertDoesNotExist()
    composeRule.onNodeWithTag("omnibox_input").assertTextContains("example.com", substring = true)
  }

  @Test fun homeOnlyShowsPrimaryContent() {
    composeRule.onNodeWithText("NeoSerf").assertIsDisplayed()
    composeRule.onNodeWithTag("omnibox_input").assertIsDisplayed()
    listOf("shield_summary_card", "customize_start_button", "search_engine_selector", "engine_chip_google",
      "weather_change_city_button", "nav_shield_button").forEach {
      composeRule.onNodeWithTag(it).assertDoesNotExist()
    }
    composeRule.onNodeWithText("Стартовая панель NeoSerf").assertDoesNotExist()
  }

  @Test fun overflowIsCompactAndSearchEngineLivesInSettings() {
    composeRule.onNodeWithTag("nav_menu_button").performClick()
    composeRule.onNodeWithTag("menu_settings").assertIsDisplayed()
    val popup = composeRule.onNodeWithTag("browser_overflow_menu").fetchSemanticsNode().boundsInRoot
    val home = composeRule.onNodeWithTag("start_page").fetchSemanticsNode().boundsInRoot
    assertTrue(popup.width < home.width)
    assertTrue(popup.height < home.height)
    composeRule.onNodeWithTag("menu_settings").performClick()
    composeRule.onNodeWithTag("settings_engine_DuckDuckGo").performClick()
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodes(hasTestTag("settings_engine_DuckDuckGo") and isSelected()).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("settings_engine_DuckDuckGo").assertIsSelected()
    composeRule.onNodeWithTag("settings_weather").assertIsDisplayed()
  }

  @Test fun tabsSheetFitsNarrowWidth() {
    composeRule.onNodeWithTag("nav_tabs_button").performClick()
    composeRule.onNodeWithText("Открытые вкладки").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_incognito").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_regular").assertIsDisplayed()
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w720dp-h1280dp-mdpi")
class BrowserUiRegressionWideTest {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
  @Test fun homeAndTabsFitWideWidth() {
    composeRule.onNodeWithTag("omnibox_input").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_tabs_button").performClick()
    composeRule.onNodeWithText("Открытые вкладки").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_incognito").assertIsDisplayed()
  }
}
