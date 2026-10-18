package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.remote.WeatherRepository
import com.example.data.repository.BrowserSyncRepository
import com.example.ui.BrowserScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.NeoSerfTheme
import com.example.ui.viewmodel.BrowserViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
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
    enableEdgeToEdge()
    setContent {
      NeoSerfTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
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
            syncRepository = BrowserSyncRepository(firestore),
            prefsRepository = BrowserPreferencesRepository(app),
            weatherRepository = WeatherRepository(),
            currentUserId = currentUserId
          )
        }
      }
    )

    BrowserScreen(
      viewModel = browserViewModel,
      userEmail = user.email,
      onSignedOut = {}
    )
  }
}
