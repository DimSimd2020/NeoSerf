package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AdBlockerEngine
import com.example.domain.BlockCategory
import com.example.ui.auth.signOutUser
import com.example.ui.components.AdBlockShieldSheet
import com.example.ui.components.BookmarksSheet
import com.example.ui.components.BottomBrowserBar
import com.example.ui.components.BrowserMenuAndCustomizeSheet
import com.example.ui.components.HistorySheet
import com.example.ui.components.PasswordVaultSheet
import com.example.ui.components.ReaderModeOverlay
import com.example.ui.components.StartPageContent
import com.example.ui.components.TabsModalSheet
import com.example.ui.components.TopOmniboxBar
import com.example.ui.viewmodel.BrowserViewModel
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WebViewCommand

private const val DESKTOP_USER_AGENT =
  "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 NeoSerf/1.0"
private const val MOBILE_USER_AGENT =
  "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 NeoSerf/1.0"

private enum class ActiveModal {
  NONE,
  TABS,
  SHIELD,
  MENU_CUSTOMIZE,
  BOOKMARKS,
  PASSWORDS,
  HISTORY
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
  viewModel: BrowserViewModel,
  userEmail: String?,
  onSignedOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val tabs by viewModel.tabs.collectAsStateWithLifecycle()
  val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
  val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
  val speedDialState by viewModel.speedDialState.collectAsStateWithLifecycle()
  val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
  val passwordsState by viewModel.passwordsState.collectAsStateWithLifecycle()
  val historyState by viewModel.historyState.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

  val activeTab = remember(tabs, activeTabId) {
    tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
  }

  val currentPrefsState by rememberUpdatedState(preferences)
  val currentTabState by rememberUpdatedState(activeTab)

  var activeModal by remember { mutableStateOf(ActiveModal.NONE) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(statusMessage) {
    val msg = statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  val webView = remember {
    WebView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
      }
    }
  }

  DisposableEffect(webView) {
    webView.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
      ): WebResourceResponse? {
        val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
        val category = AdBlockerEngine.classifyRequest(
          requestUrl = reqUrl,
          currentPageUrl = currentTabState.url,
          isAdBlockEnabled = currentPrefsState.adBlockEnabled,
          whitelistedDomains = currentPrefsState.whitelistedDomains
        )
        if (category != BlockCategory.ALLOWED) {
          viewModel.onRequestBlocked(currentTabState.id, category)
          return AdBlockerEngine.createEmptyResourceResponse()
        }
        return super.shouldInterceptRequest(view, request)
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank()) {
          viewModel.onWebPageStarted(currentTabState.id, url)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (currentPrefsState.adBlockEnabled && view != null) {
          view.evaluateJavascript(AdBlockerEngine.COSMETIC_ADBLOCK_JS, null)
        }
        if (view != null && !url.isNullOrBlank()) {
          viewModel.onWebPageFinished(
            tabId = currentTabState.id,
            url = url,
            title = view.title,
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward()
          )
        }
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        viewModel.onWebProgressChanged(currentTabState.id, newProgress)
      }
    }

    onDispose {
      webView.stopLoading()
    }
  }

  // Synchronize Desktop Mode & Incognito settings on active tab changes
  LaunchedEffect(activeTab.id, activeTab.isDesktopMode, activeTab.isIncognito) {
    webView.settings.apply {
      userAgentString = if (activeTab.isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
      useWideViewPort = activeTab.isDesktopMode
      loadWithOverviewMode = activeTab.isDesktopMode
      cacheMode = if (activeTab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
      saveFormData = !activeTab.isIncognito
    }
    CookieManager.getInstance().setAcceptCookie(!activeTab.isIncognito)
    if (!activeTab.isStartPage && webView.url != activeTab.url) {
      webView.loadUrl(activeTab.url)
    }
  }

  // Execute one-shot WebView commands from ViewModel
  LaunchedEffect(viewModel) {
    viewModel.webViewCommands.collect { command ->
      when (command) {
        is WebViewCommand.LoadUrl -> {
          webView.loadUrl(command.url)
        }

        is WebViewCommand.Reload -> {
          webView.reload()
        }

        is WebViewCommand.GoBack -> {
          if (webView.canGoBack()) {
            webView.goBack()
          } else {
            viewModel.navigateHome()
          }
        }

        is WebViewCommand.GoForward -> {
          if (webView.canGoForward()) {
            webView.goForward()
          }
        }

        is WebViewCommand.ExtractReaderContent -> {
          val tabSnapshot = currentTabState
          webView.evaluateJavascript(AdBlockerEngine.READER_EXTRACTION_JS) { rawJson ->
            val article = AdBlockerEngine.parseReaderArticleJson(
              rawEvaluateResult = rawJson,
              fallbackTitle = tabSnapshot.title,
              fallbackUrl = tabSnapshot.url
            )
            viewModel.onReaderArticleExtracted(tabSnapshot.id, article)
          }
        }

        is WebViewCommand.AutofillCredentials -> {
          val js = AdBlockerEngine.buildAutofillJs(command.username, command.plainPassword)
          webView.evaluateJavascript(js, null)
        }
      }
    }
  }

  BackHandler(enabled = !activeTab.isStartPage || activeTab.isReaderMode || activeModal != ActiveModal.NONE) {
    when {
      activeModal != ActiveModal.NONE -> activeModal = ActiveModal.NONE
      activeTab.isReaderMode -> viewModel.toggleReaderMode()
      webView.canGoBack() -> webView.goBack()
      else -> viewModel.navigateHome()
    }
  }

  val isCurrentUrlBookmarked = remember(bookmarksState, activeTab.url) {
    val list = (bookmarksState as? UiState.Success)?.data ?: emptyList()
    activeTab.url.isNotBlank() && list.any { it.url.equals(activeTab.url, ignoreCase = true) }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopOmniboxBar(
        activeTab = activeTab,
        selectedEngine = preferences.searchEngine,
        isBookmarked = isCurrentUrlBookmarked,
        adBlockEnabled = preferences.adBlockEnabled,
        onSelectSearchEngine = viewModel::selectSearchEngine,
        onSubmitInput = viewModel::submitAddressOrQuery,
        onToggleReaderMode = viewModel::toggleReaderMode,
        onToggleDesktopMode = viewModel::toggleDesktopMode,
        onToggleBookmark = { viewModel.toggleBookmarkForActiveTab() },
        onReload = { viewModel.reloadOrGoBack(goBack = false) },
        onOpenShieldSheet = { activeModal = ActiveModal.SHIELD }
      )
    },
    bottomBar = {
      BottomBrowserBar(
        activeTab = activeTab,
        tabCount = tabs.size,
        totalBlockedCount = preferences.totalAdsBlocked + preferences.totalTrackersBlocked,
        onBack = { viewModel.reloadOrGoBack(goBack = true) },
        onForward = viewModel::goForward,
        onHome = viewModel::navigateHome,
        onOpenTabs = { activeModal = ActiveModal.TABS },
        onOpenShield = { activeModal = ActiveModal.SHIELD },
        onOpenMenu = { activeModal = ActiveModal.MENU_CUSTOMIZE }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      when {
        activeTab.isStartPage -> {
          StartPageContent(
            isIncognito = activeTab.isIncognito,
            preferences = preferences,
            weatherState = weatherState,
            speedDialState = speedDialState,
            onSelectSearchEngine = viewModel::selectSearchEngine,
            onOpenUrl = viewModel::submitAddressOrQuery,
            onRefreshWeather = { viewModel.refreshWeather() },
            onChangeWeatherCity = viewModel::updateWeatherCity,
            onAddSpeedDial = viewModel::addSpeedDial,
            onDeleteSpeedDial = viewModel::deleteSpeedDial,
            onOpenCustomizationSheet = { activeModal = ActiveModal.MENU_CUSTOMIZE },
            onOpenShieldSheet = { activeModal = ActiveModal.SHIELD }
          )
        }

        activeTab.isReaderMode -> {
          ReaderModeOverlay(
            article = activeTab.readerArticle,
            fontSizeSp = preferences.readerFontSizeSp,
            readerTheme = preferences.readerTheme,
            onChangeFontSize = viewModel::setReaderFontSize,
            onSelectTheme = viewModel::setReaderTheme,
            onCloseReader = viewModel::toggleReaderMode
          )
        }

        else -> {
          AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }

  when (activeModal) {
    ActiveModal.NONE -> Unit

    ActiveModal.TABS -> {
      TabsModalSheet(
        tabs = tabs,
        activeTabId = activeTab.id,
        onSelectTab = viewModel::selectTab,
        onCloseTab = viewModel::closeTab,
        onNewRegularTab = { viewModel.createNewTab(isIncognito = false) },
        onNewIncognitoTab = { viewModel.createNewTab(isIncognito = true) },
        onCloseAllIncognito = viewModel::closeAllIncognitoTabs,
        onDismiss = { activeModal = ActiveModal.NONE }
      )
    }

    ActiveModal.SHIELD -> {
      AdBlockShieldSheet(
        activeTab = activeTab,
        preferences = preferences,
        onToggleAdBlock = viewModel::setAdBlockEnabled,
        onToggleCurrentDomainWhitelist = viewModel::toggleCurrentDomainWhitelist,
        onResetStats = viewModel::resetAdBlockStats,
        onDismiss = { activeModal = ActiveModal.NONE }
      )
    }

    ActiveModal.MENU_CUSTOMIZE -> {
      BrowserMenuAndCustomizeSheet(
        activeTab = activeTab,
        preferences = preferences,
        userEmail = userEmail,
        onOpenBookmarks = { activeModal = ActiveModal.BOOKMARKS },
        onOpenPasswords = { activeModal = ActiveModal.PASSWORDS },
        onOpenHistory = { activeModal = ActiveModal.HISTORY },
        onToggleDesktopMode = viewModel::toggleDesktopMode,
        onToggleReaderMode = viewModel::toggleReaderMode,
        onAutofillActiveSite = viewModel::autofillMatchingSiteForActiveTab,
        onSelectSearchEngine = viewModel::selectSearchEngine,
        onSetShowWeather = viewModel::setShowWeatherWidget,
        onSetShowSpeedDial = viewModel::setShowSpeedDial,
        onSetShowShieldSummary = viewModel::setShowShieldSummary,
        onSignOut = {
          signOutUser(
            credentialManager = credentialManager,
            onSignOutComplete = onSignedOut,
            scope = scope
          )
        },
        onDismiss = { activeModal = ActiveModal.NONE }
      )
    }

    ActiveModal.BOOKMARKS -> {
      BookmarksSheet(
        bookmarksState = bookmarksState,
        currentUrl = activeTab.url,
        currentTitle = activeTab.title,
        onOpenUrl = viewModel::submitAddressOrQuery,
        onAddBookmark = viewModel::addCustomBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onDismiss = { activeModal = ActiveModal.NONE }
      )
    }

    ActiveModal.PASSWORDS -> {
      PasswordVaultSheet(
        passwordsState = passwordsState,
        currentDomain = AdBlockerEngine.extractDomain(activeTab.url),
        onDecryptPassword = viewModel::decryptPassword,
        onSavePassword = viewModel::savePasswordToVault,
        onDeletePassword = viewModel::deletePasswordEntry,
        onAutofillEntry = viewModel::autofillPasswordOnActiveTab,
        onDismiss = { activeModal = ActiveModal.NONE }
      )
    }

    ActiveModal.HISTORY -> {
      HistorySheet(
        historyState = historyState,
        onOpenUrl = viewModel::submitAddressOrQuery,
        onDeleteEntry = viewModel::deleteHistoryEntry,
        onClearAll = viewModel::clearAllHistory,
        onDismiss = { activeModal = ActiveModal.NONE }
      )
    }
  }
}
