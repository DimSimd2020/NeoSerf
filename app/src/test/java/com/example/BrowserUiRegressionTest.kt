package com.example

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.data.model.BrowserTab
import com.example.data.model.SearchEngine
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
  fun hardwareEnterDoesNotOpenShieldSheet() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").performClick()
    composeRule.onNodeWithTag("omnibox_input").performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").performKeyInput {
      pressKey(Key.Enter)
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("adblock_master_switch").assertDoesNotExist()
    composeRule.onNodeWithText("Блокировка рекламы и трекеров").assertDoesNotExist()
  }

  @Test
  fun shieldSheetToggleLabelDoesNotOverlapSwitch() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_shield_badge").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("adblock_master_switch").assertIsDisplayed()
    composeRule.onNodeWithText("Блокировка рекламы и трекеров").assertIsDisplayed()
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

  @Test
  fun hardwareEnterMovesFocusToContent() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").performClick()
    composeRule.onNodeWithTag("omnibox_input").performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").performKeyInput {
      pressKey(Key.Enter)
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").assertIsNotFocused()
    composeRule.onNodeWithTag("browser_content").assertIsFocused()
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

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
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

  @Test
  fun hardwareEnterInKeyboardModeMovesFocusToContent() {
    lateinit var inputModeManager: InputModeManager
    val contentFocus = FocusRequester()
    var submits = 0
    composeRule.setContent {
      inputModeManager = LocalInputModeManager.current
      NeoSerfTheme {
        Column {
          TopOmniboxBar(
            activeTab = BrowserTab(),
            selectedEngine = SearchEngine.GOOGLE,
            isBookmarked = false,
            adBlockEnabled = true,
            onSelectSearchEngine = {},
            onSubmitInput = { submits++ },
            onToggleReaderMode = {},
            onToggleDesktopMode = {},
            onToggleBookmark = {},
            onReload = {},
            onOpenShieldSheet = {},
            onMoveFocusToContent = { contentFocus.requestFocus() },
          )
          Box(
            modifier = Modifier
              .size(16.dp)
              .focusRequester(contentFocus)
              .focusable()
              .testTag("browser_content")
          )
        }
      }
    }
    composeRule.runOnIdle {
      assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard))
    }
    val node = composeRule.onNodeWithTag("omnibox_input")
    node.performClick()
    node.performTextInput("example.com")
    node.performKeyInput { pressKey(Key.Enter) }
    composeRule.waitForIdle()
    assertEquals(1, submits)
    composeRule.onNodeWithTag("omnibox_input").assertIsNotFocused()
    composeRule.onNodeWithTag("browser_content").assertIsFocused()
  }

  @Test
  fun imeGoInKeyboardModeMovesFocusToContent() {
    lateinit var inputModeManager: InputModeManager
    val contentFocus = FocusRequester()
    var submits = 0
    composeRule.setContent {
      inputModeManager = LocalInputModeManager.current
      NeoSerfTheme {
        Column {
          TopOmniboxBar(
            activeTab = BrowserTab(),
            selectedEngine = SearchEngine.GOOGLE,
            isBookmarked = false,
            adBlockEnabled = true,
            onSelectSearchEngine = {},
            onToggleReaderMode = {},
            onToggleDesktopMode = {},
            onToggleBookmark = {},
            onReload = {},
            onOpenShieldSheet = {},
            onSubmitInput = { submits++ },
            onMoveFocusToContent = { contentFocus.requestFocus() },
          )
          Box(
            modifier = Modifier
              .size(16.dp)
              .focusRequester(contentFocus)
              .focusable()
              .testTag("browser_content")
          )
        }
      }
    }
    composeRule.runOnIdle {
      assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard))
    }
    val node = composeRule.onNodeWithTag("omnibox_input")
    node.performClick()
    node.performTextInput("example.com")
    node.performImeAction()
    composeRule.waitForIdle()
    assertEquals(1, submits)
    composeRule.onNodeWithTag("omnibox_input").assertIsNotFocused()
    composeRule.onNodeWithTag("browser_content").assertIsFocused()
  }

  private fun submitFromOmnibox(
    submit: (SemanticsNodeInteraction) -> Unit,
  ): Int {
    var submits = 0
    val contentFocus = FocusRequester()
    composeRule.setContent {
      NeoSerfTheme {
        Column {
          TopOmniboxBar(
            activeTab = BrowserTab(),
            selectedEngine = SearchEngine.GOOGLE,
            isBookmarked = false,
            adBlockEnabled = true,
            onSelectSearchEngine = {},
            onSubmitInput = { submits++ },
            onToggleReaderMode = {},
            onToggleDesktopMode = {},
            onToggleBookmark = {},
            onReload = {},
            onOpenShieldSheet = {},
            onMoveFocusToContent = { contentFocus.requestFocus() },
          )
          Box(
            modifier = Modifier
              .size(16.dp)
              .focusRequester(contentFocus)
              .focusable()
              .testTag("browser_content")
          )
        }
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
