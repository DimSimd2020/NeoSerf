package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ReaderTheme
import com.example.data.model.AppTheme
import com.example.data.model.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore by preferencesDataStore(name = "neoserf_preferences")

data class BrowserPreferencesState(
  val appTheme: AppTheme = AppTheme.SYSTEM,
  val searchEngine: SearchEngine = SearchEngine.GOOGLE,
  val showWeatherWidget: Boolean = true,
  val showSpeedDial: Boolean = false,
  val adBlockEnabled: Boolean = true,
  val totalAdsBlocked: Int = 0,
  val totalTrackersBlocked: Int = 0,
  val whitelistedDomains: Set<String> = emptySet(),
  val readerFontSizeSp: Int = 18,
  val readerTheme: ReaderTheme = ReaderTheme.GRAPHITE,
  val onboardingCompleted: Boolean = false,
)

class BrowserPreferencesRepository(private val context: Context) {

  private object Keys {
    val APP_THEME = stringPreferencesKey("app_theme")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val SHOW_WEATHER = booleanPreferencesKey("show_weather_widget")
    val SHOW_SPEED_DIAL = booleanPreferencesKey("show_speed_dial")
    val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
    val TOTAL_ADS_BLOCKED = intPreferencesKey("total_ads_blocked")
    val TOTAL_TRACKERS_BLOCKED = intPreferencesKey("total_trackers_blocked")
    val WHITELISTED_DOMAINS = stringSetPreferencesKey("whitelisted_domains")
    val READER_FONT_SIZE = intPreferencesKey("reader_font_size_sp")
    val READER_THEME = stringPreferencesKey("reader_theme")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
  }

  val preferencesFlow: Flow<BrowserPreferencesState> = context.browserDataStore.data.map { prefs ->
    BrowserPreferencesState(
      appTheme = AppTheme.entries.firstOrNull { it.name == prefs[Keys.APP_THEME] } ?: AppTheme.SYSTEM,
      searchEngine = SearchEngine.fromId(prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.GOOGLE.id),
      showWeatherWidget = prefs[Keys.SHOW_WEATHER] ?: true,
      showSpeedDial = prefs[Keys.SHOW_SPEED_DIAL] ?: false,
      adBlockEnabled = prefs[Keys.ADBLOCK_ENABLED] ?: true,
      totalAdsBlocked = prefs[Keys.TOTAL_ADS_BLOCKED] ?: 0,
      totalTrackersBlocked = prefs[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0,
      whitelistedDomains = prefs[Keys.WHITELISTED_DOMAINS] ?: emptySet(),
      readerFontSizeSp = (prefs[Keys.READER_FONT_SIZE] ?: 18).coerceIn(14, 28),
      readerTheme = ReaderTheme.fromId(prefs[Keys.READER_THEME] ?: ReaderTheme.GRAPHITE.id),
      onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false
    )
  }

  suspend fun setSearchEngine(engine: SearchEngine) {
    context.browserDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.id }
  }

  suspend fun setAppTheme(theme: AppTheme) {
    context.browserDataStore.edit { it[Keys.APP_THEME] = theme.name }
  }

  suspend fun setShowWeatherWidget(show: Boolean) {
    context.browserDataStore.edit { it[Keys.SHOW_WEATHER] = show }
  }

  suspend fun setShowSpeedDial(show: Boolean) {
    context.browserDataStore.edit { it[Keys.SHOW_SPEED_DIAL] = show }
  }

  suspend fun setAdBlockEnabled(enabled: Boolean) {
    context.browserDataStore.edit { it[Keys.ADBLOCK_ENABLED] = enabled }
  }

  suspend fun incrementBlockedCounters(adsDelta: Int, trackersDelta: Int) {
    context.browserDataStore.edit { prefs ->
      if (adsDelta > 0) {
        prefs[Keys.TOTAL_ADS_BLOCKED] = (prefs[Keys.TOTAL_ADS_BLOCKED] ?: 0) + adsDelta
      }
      if (trackersDelta > 0) {
        prefs[Keys.TOTAL_TRACKERS_BLOCKED] = (prefs[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0) + trackersDelta
      }
    }
  }

  suspend fun resetBlockedCounters() {
    context.browserDataStore.edit { prefs ->
      prefs[Keys.TOTAL_ADS_BLOCKED] = 0
      prefs[Keys.TOTAL_TRACKERS_BLOCKED] = 0
    }
  }

  suspend fun toggleDomainWhitelist(domain: String) {
    val clean = domain.trim().lowercase()
    if (clean.isEmpty()) return
    context.browserDataStore.edit { prefs ->
      val current = prefs[Keys.WHITELISTED_DOMAINS]?.toMutableSet() ?: mutableSetOf()
      if (current.contains(clean)) {
        current.remove(clean)
      } else {
        current.add(clean)
      }
      prefs[Keys.WHITELISTED_DOMAINS] = current
    }
  }

  suspend fun setReaderFontSize(sizeSp: Int) {
    context.browserDataStore.edit { it[Keys.READER_FONT_SIZE] = sizeSp.coerceIn(14, 28) }
  }

  suspend fun setOnboardingCompleted() {
    context.browserDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
  }

  suspend fun setReaderTheme(theme: ReaderTheme) {
    context.browserDataStore.edit { it[Keys.READER_THEME] = theme.id }
  }
}
