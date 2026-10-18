package com.example.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp

object FirebaseAvailability {
  const val LOCAL_GUEST_USER_ID = "local-guest"

  fun isInitialized(context: Context): Boolean {
    return try {
      FirebaseApp.getApps(context.applicationContext).isNotEmpty()
    } catch (_: Exception) {
      false
    }
  }
}
