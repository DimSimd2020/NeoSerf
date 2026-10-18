package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.remote.FirebaseAvailability
import com.example.data.remote.WeatherRepository
import com.example.data.repository.FirestoreBrowserSyncRepository
import com.example.data.repository.LocalBrowserSyncRepository
import com.example.ui.BrowserScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.NeoSerfTheme
import com.example.ui.viewmodel.BrowserViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    setContent {
      NeoSerfTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation() {
  val context = LocalContext.current
  val firebaseReady = remember(context) { FirebaseAvailability.isInitialized(context) }

  if (!firebaseReady) {
    GuestBrowserScreen()
  } else {
    AuthenticatedBrowserNavigation()
  }
}

@Composable
private fun GuestBrowserScreen() {
  val browserViewModel: BrowserViewModel = viewModel(
    key = FirebaseAvailability.LOCAL_GUEST_USER_ID,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        BrowserViewModel(
          syncRepository = LocalBrowserSyncRepository(),
          prefsRepository = BrowserPreferencesRepository(app),
          weatherRepository = WeatherRepository(app),
          currentUserId = FirebaseAvailability.LOCAL_GUEST_USER_ID
        )
      }
    }
  )

  BrowserScreen(
    viewModel = browserViewModel,
    userEmail = null,
    cloudSyncAvailable = false,
    onSignedOut = {}
  )
}

@Composable
private fun AuthenticatedBrowserNavigation() {
  val auth = remember {
    FirebaseAuth.getInstance()
  }
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen()
  } else {
    val currentUserId = user.uid
    val browserViewModel: BrowserViewModel = viewModel(
      key = currentUserId,
      factory = viewModelFactory {
        initializer {
          val app = checkNotNull(this[APPLICATION_KEY]) {
            "APPLICATION_KEY missing from CreationExtras"
          }
          val databaseId = app.getString(R.string.firestore_database_id)
          val firestore = FirebaseFirestore.getInstance(databaseId)
          BrowserViewModel(
            syncRepository = FirestoreBrowserSyncRepository(firestore),
            prefsRepository = BrowserPreferencesRepository(app),
            weatherRepository = WeatherRepository(app),
            currentUserId = currentUserId
          )
        }
      }
    )

    BrowserScreen(
      viewModel = browserViewModel,
      userEmail = user.email,
      cloudSyncAvailable = true,
      onSignedOut = {}
    )
  }
}
