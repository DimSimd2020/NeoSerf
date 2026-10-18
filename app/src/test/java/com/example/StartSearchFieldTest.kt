package com.example

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.example.ui.components.StartSearchField
import com.example.ui.theme.NeoSerfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class StartSearchFieldTest {
  @get:Rule val composeRule = createComposeRule()
  @Test fun hardwareEnterSubmitsTheCurrentTextExactlyOnce() {
    val submissions = mutableListOf<String>()
    composeRule.setContent { NeoSerfTheme { StartSearchField { submissions += it } } }
    composeRule.onNodeWithTag("omnibox_input").performClick().performTextInput("example.com")
    composeRule.onNodeWithTag("omnibox_input").assertTextContains("example.com", substring = true)
    composeRule.onNodeWithTag("omnibox_input").performKeyInput { pressKey(Key.Enter) }
    composeRule.runOnIdle { assertEquals(listOf("example.com"), submissions) }
  }
}
