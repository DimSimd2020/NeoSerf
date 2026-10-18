package com.example

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.model.AppTheme
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
@Config(sdk = [29])
class AppThemeTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun systemChangesApplyLiveAndManualChoicesOverrideThem() {
    val mode = mutableStateOf(AppTheme.SYSTEM)
    val night = mutableStateOf(false)
    var background = Color.Unspecified
    var foreground = Color.Unspecified
    var accent = Color.Unspecified
    composeRule.setContent {
      val configuration = Configuration(LocalConfiguration.current).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
          if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      }
      CompositionLocalProvider(LocalConfiguration provides configuration) {
        NeoSerfTheme(mode.value) {
          val colors = MaterialTheme.colorScheme
          SideEffect {
            background = colors.background
            foreground = colors.onBackground
            accent = colors.primary
          }
        }
      }
    }
    fun checkDark(dark: Boolean) = composeRule.runOnIdle {
      assertEquals(dark, background.luminance() < 0.5f)
      val high = maxOf(background.luminance(), foreground.luminance())
      val low = minOf(background.luminance(), foreground.luminance())
      assertTrue((high + 0.05f) / (low + 0.05f) >= 7f)
      val accentHigh = maxOf(background.luminance(), accent.luminance())
      val accentLow = minOf(background.luminance(), accent.luminance())
      assertTrue((accentHigh + 0.05f) / (accentLow + 0.05f) >= 4.5f)
    }
    checkDark(false)
    composeRule.runOnIdle { night.value = true }
    checkDark(true)
    composeRule.runOnIdle { mode.value = AppTheme.LIGHT }
    checkDark(false)
    composeRule.runOnIdle { night.value = false }
    checkDark(false)
    composeRule.runOnIdle { mode.value = AppTheme.DARK }
    checkDark(true)
    composeRule.runOnIdle { mode.value = AppTheme.SYSTEM }
    checkDark(false)
  }

  @Test fun selectedThemeIsPersisted() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val repository = BrowserPreferencesRepository(context)
    try {
      repository.setAppTheme(AppTheme.DARK)
      assertEquals(AppTheme.DARK, BrowserPreferencesRepository(context).preferencesFlow.first().appTheme)
      repository.setAppTheme(AppTheme.LIGHT)
      assertEquals(AppTheme.LIGHT, BrowserPreferencesRepository(context).preferencesFlow.first().appTheme)
    } finally {
      repository.setAppTheme(AppTheme.SYSTEM)
    }
  }
}
