package com.example

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.example.data.model.BrowserTab
import com.example.ui.components.TopOmniboxBar
import com.example.ui.theme.NeoSerfTheme
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
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test fun hardwareEnterNavigatesWithoutOpeningSearchSettings() {
    composeRule.onNodeWithTag("omnibox_input").performClick().performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").assertTextContains("example.com", substring = true)
    composeRule.onNodeWithTag("omnibox_input").performKeyInput { pressKey(Key.Enter) }
    composeRule.onNodeWithTag("start_page").assertDoesNotExist()
    composeRule.onNodeWithTag("search_engine_selector").assertDoesNotExist()
    composeRule.onNodeWithTag("browser_overflow_menu").assertDoesNotExist()
    composeRule.onNodeWithTag("adblock_master_switch").assertDoesNotExist()
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

  @Test
  fun imeGoSubmitsQuery() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").performClick()
    composeRule.onNodeWithTag("omnibox_input").performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").performImeAction()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").assertTextEquals("https://example.com")
    composeRule.onNodeWithText("Универсальный поиск Google").assertDoesNotExist()
    composeRule.onNodeWithTag("adblock_master_switch").assertDoesNotExist()
  }

  @Test fun tabsSheetFitsNarrowWidth() {
    composeRule.onNodeWithTag("nav_tabs_button").performClick()
    composeRule.onNodeWithText("Открытые вкладки").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_incognito").assertIsDisplayed()
    composeRule.onNodeWithTag("tabs_filter_regular").assertIsDisplayed()
  }

  @Test fun newTabStartsWithAnEmptySearchField() {
    composeRule.onNodeWithTag("omnibox_input").performTextInput("unfinished query")
    composeRule.onNodeWithTag("nav_menu_button").performClick()
    composeRule.onNodeWithTag("menu_new_tab").performClick()
    composeRule.onNodeWithTag("omnibox_input").assertTextEquals("")
  }

  @Test fun shieldSheetToggleLabelDoesNotOverlapSwitch() {
    composeRule.onNodeWithTag("nav_menu_button").performClick()
    composeRule.onNodeWithTag("menu_item_shield").performClick()
    composeRule.onNodeWithTag("adblock_master_switch").assertIsDisplayed()
    val label = composeRule.onNodeWithText("Блокировка рекламы и трекеров").fetchSemanticsNode().boundsInRoot
    val toggle = composeRule.onNodeWithTag("adblock_master_switch").fetchSemanticsNode().boundsInRoot
    assertTrue(label.right <= toggle.left)
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

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w360dp-h720dp-xhdpi")
class OmniboxSubmitGuardTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun imeGoSubmitsOnce() {
    val submits = submitFromOmnibox { node ->
      node.performImeAction()
    }
    assertEquals(1, submits)
  }

  @Test
  fun hardwareEnterSubmitsOnce() {
    val submits = submitFromOmnibox { node ->
      node.performKeyInput { pressKey(Key.Enter) }
    }
    assertEquals(1, submits)
  }

  private fun submitFromOmnibox(
    submit: (SemanticsNodeInteraction) -> Unit,
  ): Int {
    var submits = 0
    composeRule.setContent {
      NeoSerfTheme {
        TopOmniboxBar(
          activeTab = BrowserTab(),
          isBookmarked = false,
          adBlockEnabled = true,
          onSubmitInput = { submits++ },
          onToggleBookmark = {},
          onReload = {},
          onOpenShieldSheet = {},
        )
      }
    }
    val node = composeRule.onNodeWithTag("omnibox_input")
    node.performClick()
    node.performTextInput("example.com")
    submit(node)
    composeRule.waitForIdle()
    return submits
  }
}
