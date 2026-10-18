package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.remote.FirebaseAvailability
import com.example.data.remote.WeatherRepository
import com.example.data.repository.LocalBrowserSyncRepository
import com.example.ui.viewmodel.BrowserViewModel
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GuestModeWithoutFirebaseTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun firebaseIsNotInitializedInThisProcess() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(FirebaseApp.getApps(context).isEmpty())
    assertFalse(FirebaseAvailability.isInitialized(context))
  }

  @Test
  fun mainActivityOpensBrowserInsteadOfCrashing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(FirebaseApp.getApps(context).isEmpty())

    composeRule.waitForIdle()
    assertFalse(composeRule.activity.isFinishing)
    assertFalse(composeRule.activity.isDestroyed)

    composeRule.onNodeWithTag("omnibox_input").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_back_button").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_forward_button").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_home_button").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_tabs_button").assertIsDisplayed()
    composeRule.onNodeWithTag("google_sign_in_button").assertDoesNotExist()
  }

  @Test
  fun activityControllerSetupDoesNotFinishWithoutFirebase() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertFalse(activity.isFinishing)
    controller.pause().stop().destroy()
  }

  @Test
  fun viewModelCoreNavigationWorksWithLocalRepository() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = BrowserViewModel(
      syncRepository = LocalBrowserSyncRepository(),
      prefsRepository = BrowserPreferencesRepository(context),
      weatherRepository = WeatherRepository(),
      currentUserId = FirebaseAvailability.LOCAL_GUEST_USER_ID
    )

    viewModel.createNewTab(isIncognito = false)
    assertEquals(2, viewModel.tabs.value.size)

    viewModel.submitAddressOrQuery("https://example.com")
    assertEquals("https://example.com", viewModel.getActiveTab().url)

    viewModel.navigateHome()
    assertTrue(viewModel.getActiveTab().url.isBlank())
  }

  @Test
  fun omniboxAcceptsInputWithoutFirebase() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").performTextInput("example.com")
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("omnibox_input").assertIsDisplayed()
  }
}
