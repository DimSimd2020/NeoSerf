package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AdBlockerEngine
import com.example.domain.BlockCategory
import com.example.ui.auth.signOutUser
import com.example.ui.components.AdBlockShieldSheet
import com.example.ui.components.BookmarksSheet
import com.example.ui.components.BottomBrowserBar
import com.example.ui.components.BrowserMenuPopup
import com.example.ui.components.HistorySheet
import com.example.ui.components.PasswordVaultSheet
import com.example.ui.components.ReaderModeOverlay
import com.example.ui.components.StartPageContent
import com.example.ui.components.SettingsModalSheet
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
  SETTINGS,
  BOOKMARKS,
  PASSWORDS,
  HISTORY
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
  viewModel: BrowserViewModel,
  userEmail: String?,
  cloudSyncAvailable: Boolean = true,
  onSignedOut: () -> Unit,
) {
  val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
  ) { viewModel.refreshWeather() }
  val context = LocalContext.current
  // Ask once for the device location so the weather follows it instead of the network region.
  LaunchedEffect(Unit) {
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
      context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted && viewModel.claimLocationPrompt()) {
      locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }
  }
  val scope = rememberCoroutineScope()
  val credentialManager = remember(cloudSyncAvailable) {
    if (cloudSyncAvailable) CredentialManager.create(context) else null
  }

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

  var webView by remember { mutableStateOf<WebView?>(null) }
  val contentFocusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  var pendingContentFocus by remember { mutableStateOf(false) }

  fun moveFocusToContent() {
    keyboardController?.hide()
    pendingContentFocus = true
    val wv = webView
    if (wv != null) {
      wv.isFocusable = true
      wv.isFocusableInTouchMode = true
      wv.requestFocus()
    }
    try {
      contentFocusRequester.requestFocus()
    } catch (_: IllegalStateException) {
      // Content requester is attached after first layout; retry below.
    }
  }

  LaunchedEffect(pendingContentFocus, webView, activeTab.isStartPage) {
    if (!pendingContentFocus) return@LaunchedEffect
    keyboardController?.hide()
    val wv = webView
    if (wv != null && !activeTab.isStartPage) {
      wv.isFocusable = true
      wv.isFocusableInTouchMode = true
      wv.requestFocus()
    }
    try {
      contentFocusRequester.requestFocus()
    } catch (_: IllegalStateException) {
      return@LaunchedEffect
    }
    pendingContentFocus = false
  }

  fun attachBrowserClients(target: WebView) {
    target.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
      ): WebResourceResponse? {
        return try {
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
          super.shouldInterceptRequest(view, request)
        } catch (_: Throwable) {
          super.shouldInterceptRequest(view, request)
        }
      }

      @Deprecated("Deprecated in Java")
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        // Old system WebView (API 29 / Chromium 74) still dispatches this overload.
        return false
      }

      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return false
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
          view.evaluateJavascriptCompat(AdBlockerEngine.COSMETIC_ADBLOCK_JS, null)
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

    target.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        viewModel.onWebProgressChanged(currentTabState.id, newProgress)
      }
    }
  }

  fun obtainWebView(ctx: android.content.Context): WebView {
    val existing = webView
    if (existing != null) {
      (existing.parent as? ViewGroup)?.removeView(existing)
      return existing
    }
    val created = WebView(ctx).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      isFocusable = true
      isFocusableInTouchMode = true
      applyNeoSerfWebSettings()
    }
    attachBrowserClients(created)
    webView = created
    // The first navigation can precede the SharedFlow collector while WebView is
    // being created. Start that pending URL as soon as the view is created.
    if (currentTabState.url.isNotBlank()) created.loadUrl(currentTabState.url)
    return created
  }

  DisposableEffect(Unit) {
    onDispose {
      webView?.stopLoading()
    }
  }

  val currentWebView = webView

  // Synchronize Desktop Mode & Incognito settings on active tab changes
  LaunchedEffect(currentWebView, activeTab.id, activeTab.isDesktopMode, activeTab.isIncognito) {
    val wv = currentWebView ?: return@LaunchedEffect
    wv.settings.apply {
      userAgentString = if (activeTab.isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
      useWideViewPort = activeTab.isDesktopMode
      loadWithOverviewMode = activeTab.isDesktopMode
      cacheMode = if (activeTab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
      try {
        @Suppress("DEPRECATION")
        saveFormData = !activeTab.isIncognito
      } catch (_: Throwable) {
        // Removed on some WebView builds newer than 74; ignore.
      }
    }
    try {
      CookieManager.getInstance().setAcceptCookie(!activeTab.isIncognito)
    } catch (_: Throwable) {
      // CookieManager can throw on a stub / locked WebView process.
    }
    if (!activeTab.isStartPage && wv.url != activeTab.url) {
      wv.loadUrl(activeTab.url)
    }
  }

  // Execute one-shot WebView commands from ViewModel
  LaunchedEffect(viewModel, currentWebView) {
    val wv = currentWebView ?: return@LaunchedEffect
    viewModel.webViewCommands.collect { command ->
      when (command) {
        is WebViewCommand.LoadUrl -> {
          wv.loadUrl(command.url)
        }

        is WebViewCommand.Reload -> {
          wv.reload()
        }

        is WebViewCommand.GoBack -> {
          if (wv.canGoBack()) {
            wv.goBack()
          } else {
            viewModel.navigateHome()
          }
        }

        is WebViewCommand.GoForward -> {
          if (wv.canGoForward()) {
            wv.goForward()
          }
        }

        is WebViewCommand.ExtractReaderContent -> {
          val tabSnapshot = currentTabState
          wv.evaluateJavascriptCompat(AdBlockerEngine.READER_EXTRACTION_JS) { rawJson ->
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
          wv.evaluateJavascriptCompat(js, null)
        }
      }
    }
  }

  BackHandler(enabled = !activeTab.isStartPage || activeTab.isReaderMode || activeModal != ActiveModal.NONE) {
    when {
      activeModal != ActiveModal.NONE -> activeModal = ActiveModal.NONE
      activeTab.isReaderMode -> viewModel.toggleReaderMode()
      currentWebView?.canGoBack() == true -> currentWebView.goBack()
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
      if (!activeTab.isStartPage) TopOmniboxBar(
        activeTab = activeTab,
        isBookmarked = isCurrentUrlBookmarked,
        adBlockEnabled = preferences.adBlockEnabled,
        onSubmitInput = viewModel::submitAddressOrQuery,
        onToggleBookmark = { viewModel.toggleBookmarkForActiveTab() },
        onReload = { viewModel.reloadOrGoBack(goBack = false) },
        onOpenShieldSheet = { activeModal = ActiveModal.SHIELD },
        onMoveFocusToContent = { moveFocusToContent() }
      )
    },
    bottomBar = {
      BottomBrowserBar(
        activeTab = activeTab,
        tabCount = tabs.size,
        onBack = { viewModel.reloadOrGoBack(goBack = true) },
        onForward = viewModel::goForward,
        onHome = viewModel::navigateHome,
        onOpenTabs = { activeModal = ActiveModal.TABS },
        onOpenMenu = { activeModal = ActiveModal.MENU_CUSTOMIZE }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .then(if (activeTab.isStartPage) Modifier.statusBarsPadding() else Modifier)
        .focusRequester(contentFocusRequester)
        .focusable()
        .testTag("browser_content")
    ) {
      when {
        activeTab.isStartPage -> {
          key(activeTab.id) {
            StartPageContent(
              isIncognito = activeTab.isIncognito,
              preferences = preferences,
              weatherState = weatherState,
              speedDialState = speedDialState,
              onOpenUrl = viewModel::submitAddressOrQuery,
              onRefreshWeather = { viewModel.refreshWeather() },
              onAddSpeedDial = viewModel::addSpeedDial,
              onDeleteSpeedDial = viewModel::deleteSpeedDial,
            )
          }
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
            factory = { ctx -> obtainWebView(ctx) },
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }

  BrowserMenuPopup(
    expanded = activeModal == ActiveModal.MENU_CUSTOMIZE,
    activeTab = activeTab,
    onOpenBookmarks = { activeModal = ActiveModal.BOOKMARKS },
    onOpenPasswords = { activeModal = ActiveModal.PASSWORDS },
    onOpenHistory = { activeModal = ActiveModal.HISTORY },
    onToggleDesktopMode = viewModel::toggleDesktopMode,
    onToggleReaderMode = viewModel::toggleReaderMode,
    onAutofillActiveSite = viewModel::autofillMatchingSiteForActiveTab,
    onOpenShield = { activeModal = ActiveModal.SHIELD },
    onOpenSettings = { activeModal = ActiveModal.SETTINGS },
    onNewTab = { incognito -> viewModel.createNewTab(isIncognito = incognito) },
    onDismiss = { activeModal = ActiveModal.NONE }
  )

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

    ActiveModal.MENU_CUSTOMIZE -> Unit

    ActiveModal.SETTINGS -> {
      SettingsModalSheet(
        preferences = preferences,
        userEmail = userEmail,
        cloudSyncAvailable = cloudSyncAvailable,
        onSelectSearchEngine = viewModel::selectSearchEngine,
        onSetShowWeather = viewModel::setShowWeatherWidget,
        onSetShowSpeedDial = viewModel::setShowSpeedDial,
        onSetAppTheme = viewModel::setAppTheme,
        onRefreshWeather = { viewModel.refreshWeather() },
        onRequestLocation = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
        onSignOut = {
          val manager = credentialManager
          if (manager != null) {
            signOutUser(
              credentialManager = manager,
              onSignOutComplete = onSignedOut,
              scope = scope
            )
          } else {
            onSignedOut()
          }
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

/**
 * Settings that exist on system WebView 74 (API 29). Do not call setForceDark,
 * setAlgorithmicDarkeningAllowed, or androidx.webkit helpers without feature checks —
 * those APIs are missing or crash on Chromium 74.
 */
private fun WebView.applyNeoSerfWebSettings() {
  settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    loadsImagesAutomatically = true
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
    try {
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    } catch (_: Throwable) {
      // MIXED_CONTENT_* landed in API 21; some vendor WebView stubs still throw.
    }
    try {
      @Suppress("DEPRECATION")
      databaseEnabled = true
    } catch (_: Throwable) {
      // Removed or no-op on some Chromium builds.
    }
  }
}

private fun WebView.evaluateJavascriptCompat(script: String, callback: ValueCallback<String>?) {
  try {
    evaluateJavascript(script, callback)
  } catch (_: Throwable) {
    callback?.onReceiveValue(null)
  }
}
