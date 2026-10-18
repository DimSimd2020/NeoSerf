package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.local.BrowserPreferencesState
import com.example.data.model.BookmarkItem
import com.example.data.model.BrowserTab
import com.example.data.model.HistoryEntry
import com.example.data.model.PasswordEntry
import com.example.data.model.ReaderArticle
import com.example.data.model.ReaderTheme
import com.example.data.model.SearchEngine
import com.example.data.model.SpeedDialTile
import com.example.data.model.WeatherForecast
import com.example.data.remote.WeatherRepository
import com.example.data.repository.BrowserSyncRepository
import com.example.domain.AdBlockerEngine
import com.example.domain.BlockCategory
import com.example.domain.PasswordCryptoManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

sealed interface WebViewCommand {
  data class LoadUrl(val url: String) : WebViewCommand
  data object Reload : WebViewCommand
  data object GoBack : WebViewCommand
  data object GoForward : WebViewCommand
  data object ExtractReaderContent : WebViewCommand
  data class AutofillCredentials(val username: String, val plainPassword: String) : WebViewCommand
}

class BrowserViewModel(
  private val syncRepository: BrowserSyncRepository,
  private val prefsRepository: BrowserPreferencesRepository,
  private val weatherRepository: WeatherRepository,
  val currentUserId: String,
) : ViewModel() {

  val preferences: StateFlow<BrowserPreferencesState> = prefsRepository.preferencesFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = BrowserPreferencesState()
    )

  // --- CLOUD SYNC FLOWS (Two-tier error handling before stateIn) ---

  val bookmarksState: StateFlow<UiState<List<BookmarkItem>>> = syncRepository.observeBookmarks(currentUserId)
    .map<List<BookmarkItem>, UiState<List<BookmarkItem>>> { UiState.Success(it) }
    .catch { error ->
      Log.w(TAG, "Error observing bookmarks", error)
      emit(UiState.Error(error.localizedMessage ?: "Ошибка загрузки закладок"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading
    )

  val passwordsState: StateFlow<UiState<List<PasswordEntry>>> = syncRepository.observePasswords(currentUserId)
    .map<List<PasswordEntry>, UiState<List<PasswordEntry>>> { UiState.Success(it) }
    .catch { error ->
      Log.w(TAG, "Error observing passwords", error)
      emit(UiState.Error(error.localizedMessage ?: "Ошибка загрузки паролей"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading
    )

  val historyState: StateFlow<UiState<List<HistoryEntry>>> = syncRepository.observeHistory(currentUserId)
    .map<List<HistoryEntry>, UiState<List<HistoryEntry>>> { UiState.Success(it) }
    .catch { error ->
      Log.w(TAG, "Error observing history", error)
      emit(UiState.Error(error.localizedMessage ?: "Ошибка загрузки истории"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading
    )

  val speedDialState: StateFlow<UiState<List<SpeedDialTile>>> = syncRepository.observeSpeedDial(currentUserId)
    .map<List<SpeedDialTile>, UiState<List<SpeedDialTile>>> { UiState.Success(it) }
    .catch { error ->
      Log.w(TAG, "Error observing speed dial", error)
      emit(UiState.Error(error.localizedMessage ?: "Ошибка загрузки быстрого доступа"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading
    )

  // --- TABS & ACTIVE TAB STATE ---

  private val initialTab = BrowserTab(title = "Стартовая страница", url = "")
  private val _tabs = MutableStateFlow(listOf(initialTab))
  val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

  private val _activeTabId = MutableStateFlow(initialTab.id)
  val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

  private val _webViewCommands = MutableSharedFlow<WebViewCommand>(extraBufferCapacity = 8)
  val webViewCommands = _webViewCommands.asSharedFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  // --- WEATHER WIDGET STATE ---

  private val _weatherState = MutableStateFlow<UiState<WeatherForecast>>(UiState.Loading)
  val weatherState: StateFlow<UiState<WeatherForecast>> = _weatherState.asStateFlow()

  private var weatherJob: Job? = null
  private var defaultsSeeded = false

  init {
    viewModelScope.launch {
      preferences.map { it.showWeatherWidget }.distinctUntilChanged().collectLatest { enabled ->
        if (enabled) {
          while (isActive) {
            refreshWeather()
            delay(30 * 60 * 1000L)
          }
        } else {
          weatherJob?.cancel()
        }
      }
    }

    viewModelScope.launch {
      speedDialState.collect { state ->
        if (!defaultsSeeded && state is UiState.Success && state.data.isEmpty()) {
          defaultsSeeded = true
          seedDefaultSpeedDial()
        }
      }
    }
  }

  private suspend fun seedDefaultSpeedDial() {
    val defaults = listOf(
      Triple("Википедия", "https://ru.wikipedia.org", "Энциклопедия"),
      Triple("Хабр", "https://habr.com/ru/all/", "Технологии"),
      Triple("GitHub", "https://github.com", "Разработка"),
      Triple("Kotlin", "https://kotlinlang.org/docs/home.html", "Документация"),
      Triple("Погода", "https://open-meteo.com", "Метеоданные")
    )
    defaults.forEachIndexed { index, (title, url, category) ->
      syncRepository.addSpeedDialTile(title, url, category, index)
    }
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun showMessage(msg: String) {
    _statusMessage.value = msg
  }

  fun refreshWeather() {
    weatherJob?.cancel()
    _weatherState.value = UiState.Loading
    weatherJob = viewModelScope.launch {
      weatherRepository.fetchWeather().fold(
        onSuccess = { _weatherState.value = UiState.Success(it) },
        onFailure = { _weatherState.value = UiState.Error("Погода недоступна. Проверьте подключение к сети.") }
      )
    }
  }

  // --- TAB MANAGEMENT ---

  fun getActiveTab(): BrowserTab {
    val currentId = _activeTabId.value
    return _tabs.value.firstOrNull { it.id == currentId } ?: _tabs.value.first()
  }

  fun createNewTab(isIncognito: Boolean = false, initialUrl: String = "") {
    val newTab = BrowserTab(
      title = if (isIncognito) "Инкогнито" else "Стартовая страница",
      url = initialUrl,
      isIncognito = isIncognito
    )
    _tabs.update { it + newTab }
    _activeTabId.value = newTab.id
    if (initialUrl.isNotBlank()) {
      _webViewCommands.tryEmit(WebViewCommand.LoadUrl(initialUrl))
    }
  }

  fun selectTab(tabId: String) {
    if (_tabs.value.any { it.id == tabId }) {
      _activeTabId.value = tabId
    }
  }

  fun closeTab(tabId: String) {
    val currentList = _tabs.value
    if (currentList.size <= 1) {
      updateTab(currentList.first().id) {
        it.copy(
          title = "Стартовая страница",
          url = "",
          isReaderMode = false,
          readerArticle = null,
          isLoading = false,
          progress = 0
        )
      }
      return
    }
    val index = currentList.indexOfFirst { it.id == tabId }
    val updated = currentList.filterNot { it.id == tabId }
    _tabs.value = updated
    if (_activeTabId.value == tabId) {
      val nextIdx = (index - 1).coerceAtLeast(0).coerceAtMost(updated.lastIndex)
      _activeTabId.value = updated[nextIdx].id
    }
  }

  fun closeAllIncognitoTabs() {
    val remaining = _tabs.value.filterNot { it.isIncognito }
    if (remaining.isEmpty()) {
      val fresh = BrowserTab(title = "Стартовая страница", url = "")
      _tabs.value = listOf(fresh)
      _activeTabId.value = fresh.id
    } else {
      _tabs.value = remaining
      if (remaining.none { it.id == _activeTabId.value }) {
        _activeTabId.value = remaining.last().id
      }
    }
    showMessage("Все вкладки Инкогнито закрыты")
  }

  private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
    _tabs.update { list ->
      list.map { tab -> if (tab.id == tabId) transform(tab) else tab }
    }
  }

  // --- NAVIGATION & OMNIBOX ---

  fun submitAddressOrQuery(rawInput: String) {
    val input = rawInput.trim()
    if (input.isEmpty()) return

    val resolvedUrl = resolveInputToUrl(input, preferences.value.searchEngine)
    val tab = getActiveTab()
    updateTab(tab.id) {
      it.copy(
        url = resolvedUrl,
        title = input,
        isReaderMode = false,
        readerArticle = null,
        isLoading = true,
        progress = 15
      )
    }
    _webViewCommands.tryEmit(WebViewCommand.LoadUrl(resolvedUrl))
  }

  fun navigateHome() {
    val tab = getActiveTab()
    updateTab(tab.id) {
      it.copy(
        url = "",
        title = if (it.isIncognito) "Инкогнито" else "Стартовая страница",
        isReaderMode = false,
        readerArticle = null,
        isLoading = false,
        progress = 0
      )
    }
  }

  fun reloadOrGoBack(goBack: Boolean) {
    if (goBack) {
      val tab = getActiveTab()
      if (tab.isReaderMode) {
        updateTab(tab.id) { it.copy(isReaderMode = false) }
        return
      }
      if (!tab.isStartPage && !tab.canGoBack) {
        navigateHome()
        return
      }
      _webViewCommands.tryEmit(WebViewCommand.GoBack)
    } else {
      _webViewCommands.tryEmit(WebViewCommand.Reload)
    }
  }

  fun goForward() {
    _webViewCommands.tryEmit(WebViewCommand.GoForward)
  }

  fun onWebPageStarted(tabId: String, url: String) {
    updateTab(tabId) {
      it.copy(
        url = url,
        isLoading = true,
        progress = 20
      )
    }
  }

  fun onWebProgressChanged(tabId: String, newProgress: Int) {
    updateTab(tabId) {
      it.copy(
        progress = newProgress,
        isLoading = newProgress < 100
      )
    }
  }

  fun onWebPageFinished(
    tabId: String,
    url: String,
    title: String?,
    canGoBack: Boolean,
    canGoForward: Boolean,
  ) {
    val cleanTitle = title?.takeIf { it.isNotBlank() } ?: AdBlockerEngine.extractDomain(url).ifEmpty { url }
    var shouldRecordHistory = false
    updateTab(tabId) { current ->
      shouldRecordHistory = !current.isIncognito && url.startsWith("http")
      current.copy(
        url = url,
        title = cleanTitle,
        isLoading = false,
        progress = 100,
        canGoBack = canGoBack,
        canGoForward = canGoForward
      )
    }
    if (shouldRecordHistory) {
      viewModelScope.launch {
        syncRepository.recordHistoryVisit(
          title = cleanTitle,
          url = url,
          searchEngine = preferences.value.searchEngine.displayName
        )
      }
    }
  }

  // --- DESKTOP MODE & READER MODE ---

  fun toggleDesktopMode() {
    val tab = getActiveTab()
    val nextDesktop = !tab.isDesktopMode
    updateTab(tab.id) { it.copy(isDesktopMode = nextDesktop) }
    showMessage(if (nextDesktop) "Включен режим ПК" else "Включен мобильный режим")
    if (!tab.isStartPage) {
      _webViewCommands.tryEmit(WebViewCommand.Reload)
    }
  }

  fun toggleReaderMode() {
    val tab = getActiveTab()
    if (tab.isStartPage) {
      showMessage("Откройте веб-страницу или статью для включения режима чтения")
      return
    }
    if (tab.isReaderMode) {
      updateTab(tab.id) { it.copy(isReaderMode = false) }
    } else {
      _webViewCommands.tryEmit(WebViewCommand.ExtractReaderContent)
    }
  }

  fun onReaderArticleExtracted(tabId: String, article: ReaderArticle) {
    updateTab(tabId) {
      it.copy(
        isReaderMode = true,
        readerArticle = article
      )
    }
  }

  fun setReaderFontSize(sizeSp: Int) {
    viewModelScope.launch {
      prefsRepository.setReaderFontSize(sizeSp)
    }
  }

  fun setReaderTheme(theme: ReaderTheme) {
    viewModelScope.launch {
      prefsRepository.setReaderTheme(theme)
    }
  }

  // --- ADBLOCKER ENGINE CALLBACKS ---

  fun onRequestBlocked(tabId: String, category: BlockCategory) {
    if (category == BlockCategory.ALLOWED) return
    val adsDelta = if (category == BlockCategory.BLOCKED_AD) 1 else 0
    val trackersDelta = if (category == BlockCategory.BLOCKED_TRACKER) 1 else 0
    updateTab(tabId) {
      it.copy(
        adsBlockedInTab = it.adsBlockedInTab + adsDelta,
        trackersBlockedInTab = it.trackersBlockedInTab + trackersDelta
      )
    }
    viewModelScope.launch {
      prefsRepository.incrementBlockedCounters(adsDelta, trackersDelta)
    }
  }

  fun setAdBlockEnabled(enabled: Boolean) {
    viewModelScope.launch {
      prefsRepository.setAdBlockEnabled(enabled)
      if (!getActiveTab().isStartPage) {
        _webViewCommands.tryEmit(WebViewCommand.Reload)
      }
    }
  }

  fun toggleCurrentDomainWhitelist() {
    val domain = AdBlockerEngine.extractDomain(getActiveTab().url)
    if (domain.isNotEmpty()) {
      viewModelScope.launch {
        prefsRepository.toggleDomainWhitelist(domain)
        _webViewCommands.tryEmit(WebViewCommand.Reload)
      }
    }
  }

  fun resetAdBlockStats() {
    viewModelScope.launch {
      prefsRepository.resetBlockedCounters()
      showMessage("Статистика блокировщика сброшена")
    }
  }

  // --- PREFERENCES & CUSTOMIZATION ---

  fun selectSearchEngine(engine: SearchEngine) {
    viewModelScope.launch {
      prefsRepository.setSearchEngine(engine)
      showMessage("Поисковая система: ${engine.displayName}")
    }
  }

  fun setShowWeatherWidget(show: Boolean) {
    viewModelScope.launch { prefsRepository.setShowWeatherWidget(show) }
  }

  fun setShowSpeedDial(show: Boolean) {
    viewModelScope.launch { prefsRepository.setShowSpeedDial(show) }
  }

  // --- CLOUD BOOKMARKS, SPEED DIAL, HISTORY & PASSWORD VAULT ---

  fun toggleBookmarkForActiveTab(folder: String = "Избранное") {
    val tab = getActiveTab()
    if (tab.isStartPage) {
      showMessage("Откройте сайт, чтобы добавить его в закладки")
      return
    }
    val existingBookmarks = (bookmarksState.value as? UiState.Success)?.data ?: emptyList()
    val match = existingBookmarks.firstOrNull { it.url.equals(tab.url, ignoreCase = true) }
    viewModelScope.launch {
      if (match != null) {
        syncRepository.deleteBookmark(currentUserId, match.id)
        showMessage("Закладка удалена")
      } else {
        syncRepository.addBookmark(tab.title, tab.url, folder)
        showMessage("Сохранено в облачные закладки")
      }
    }
  }

  fun addCustomBookmark(title: String, rawUrl: String, folder: String) {
    val url = resolveInputToUrl(rawUrl, preferences.value.searchEngine)
    viewModelScope.launch {
      syncRepository.addBookmark(title, url, folder)
      showMessage("Закладка добавлена")
    }
  }

  fun deleteBookmark(bookmarkId: String) {
    viewModelScope.launch {
      syncRepository.deleteBookmark(currentUserId, bookmarkId)
    }
  }

  fun addSpeedDial(title: String, rawUrl: String, category: String) {
    val url = resolveInputToUrl(rawUrl, preferences.value.searchEngine)
    val currentCount = ((speedDialState.value as? UiState.Success)?.data?.size ?: 0)
    viewModelScope.launch {
      syncRepository.addSpeedDialTile(title, url, category, currentCount)
      showMessage("Добавлено на стартовую страницу")
    }
  }

  fun deleteSpeedDial(tileId: String) {
    viewModelScope.launch {
      syncRepository.deleteSpeedDialTile(currentUserId, tileId)
    }
  }

  fun deleteHistoryEntry(historyId: String) {
    viewModelScope.launch {
      syncRepository.deleteHistoryEntry(currentUserId, historyId)
    }
  }

  fun clearAllHistory() {
    val items = (historyState.value as? UiState.Success)?.data ?: emptyList()
    viewModelScope.launch {
      syncRepository.clearAllHistory(currentUserId, items)
      showMessage("История просмотров очищена")
    }
  }

  fun savePasswordToVault(
    siteDomain: String,
    siteTitle: String,
    username: String,
    plainPassword: String,
    notes: String,
  ) {
    if (username.isBlank() || plainPassword.isBlank()) return
    val encrypted = PasswordCryptoManager.encrypt(plainPassword, currentUserId)
    val cleanDomain = AdBlockerEngine.extractDomain(
      if (siteDomain.startsWith("http")) siteDomain else "https://$siteDomain"
    ).ifEmpty { siteDomain.trim() }
    viewModelScope.launch {
      syncRepository.savePasswordEntry(
        siteDomain = cleanDomain,
        siteTitle = siteTitle.ifBlank { cleanDomain },
        username = username,
        encryptedPassword = encrypted,
        notes = notes
      )
      showMessage("Пароль зашифрован и сохранен в облаке")
    }
  }

  fun decryptPassword(entry: PasswordEntry): String {
    return PasswordCryptoManager.decrypt(entry.encryptedPassword, currentUserId)
  }

  fun deletePasswordEntry(passwordId: String) {
    viewModelScope.launch {
      syncRepository.deletePasswordEntry(currentUserId, passwordId)
      showMessage("Запись удалена из менеджера паролей")
    }
  }

  fun autofillPasswordOnActiveTab(entry: PasswordEntry) {
    val plain = decryptPassword(entry)
    val tab = getActiveTab()
    if (tab.isStartPage) {
      submitAddressOrQuery("https://${entry.siteDomain}")
    }
    _webViewCommands.tryEmit(WebViewCommand.AutofillCredentials(entry.username, plain))
    showMessage("Данные для ${entry.siteDomain} подставлены")
  }

  fun autofillMatchingSiteForActiveTab() {
    val tab = getActiveTab()
    val domain = AdBlockerEngine.extractDomain(tab.url)
    val allPasswords = (passwordsState.value as? UiState.Success)?.data ?: emptyList()
    val match = allPasswords.firstOrNull {
      domain.isNotEmpty() && (domain.contains(it.siteDomain, ignoreCase = true) || it.siteDomain.contains(domain, ignoreCase = true))
    } ?: allPasswords.firstOrNull()

    if (match != null) {
      autofillPasswordOnActiveTab(match)
    } else {
      showMessage("Для текущего сайта нет сохраненных паролей")
    }
  }

  companion object {
    private const val TAG = "BrowserViewModel"
    private const val STOP_TIMEOUT_MILLIS = 5000L

    fun resolveInputToUrl(input: String, searchEngine: SearchEngine): String {
      val trimmed = input.trim()
      if (trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
      ) {
        return trimmed
      }
      val looksLikeDomain = !trimmed.contains(" ") &&
        trimmed.contains(".") &&
        trimmed.substringAfterLast(".").length >= 2
      return if (looksLikeDomain) {
        "https://$trimmed"
      } else {
        searchEngine.buildSearchUrl(trimmed)
      }
    }
  }
}
