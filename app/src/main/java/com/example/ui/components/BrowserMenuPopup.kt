package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.ui.icons.NeoIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.local.BrowserPreferencesState
import com.example.data.model.BrowserTab
import com.example.data.model.SearchEngine
import com.example.data.model.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserMenuPopup(
  expanded: Boolean,
  activeTab: BrowserTab,
  preferences: BrowserPreferencesState,
  userEmail: String?,
  cloudSyncAvailable: Boolean,
  onOpenBookmarks: () -> Unit,
  onOpenPasswords: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenShield: () -> Unit,
  onNewTab: (Boolean) -> Unit,
  onToggleDesktopMode: () -> Unit,
  onToggleReaderMode: () -> Unit,
  onAutofillActiveSite: () -> Unit,
  onSelectSearchEngine: (SearchEngine) -> Unit,
  onSetShowWeather: (Boolean) -> Unit,
  onSetShowSpeedDial: (Boolean) -> Unit,
  onSetAppTheme: (AppTheme) -> Unit,
  onRefreshWeather: () -> Unit,
  onRequestLocation: () -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  val transition = remember { MutableTransitionState(false) }
  transition.targetState = expanded
  var settings by remember { mutableStateOf(false) }
  LaunchedEffect(expanded) { if (expanded) settings = false }
  if (!transition.currentState && !transition.targetState) return

  val configuration = LocalConfiguration.current
  val density = LocalDensity.current
  val bottomInset = WindowInsets.navigationBars.getBottom(density)
  val offset = with(density) { IntOffset((-12).dp.roundToPx(), -64.dp.roundToPx() - bottomInset) }
  fun choose(action: () -> Unit) {
    onDismiss()
    action()
  }
  Popup(
    alignment = Alignment.BottomEnd,
    offset = offset,
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = true)
  ) {
    AnimatedVisibility(
      visibleState = transition,
      enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.92f,
        transformOrigin = TransformOrigin(1f, 1f)),
      exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.96f,
        transformOrigin = TransformOrigin(1f, 1f))
    ) {
      Surface(
        modifier = Modifier.width(minOf(304, configuration.screenWidthDp - 24).dp)
          .heightIn(max = (configuration.screenHeightDp * 0.64f).dp)
          .testTag("browser_overflow_menu"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 16.dp,
        tonalElevation = 0.dp
      ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
          if (settings) {
            MenuRow(NeoIcons.ArrowBack, "Настройки", "menu_back") { settings = false }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outline)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
              Text("Оформление", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppTheme.entries.forEach { theme ->
                  FilterChip(
                    selected = theme == preferences.appTheme,
                    onClick = { onSetAppTheme(theme) },
                    label = { Text(theme.title) },
                    leadingIcon = {
                      Icon(when (theme) {
                        AppTheme.SYSTEM -> NeoIcons.Device
                        AppTheme.LIGHT -> NeoIcons.WbSunny
                        AppTheme.DARK -> NeoIcons.NightsStay
                      }, null, Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("settings_theme_${theme.name}")
                  )
                }
              }
              Spacer(Modifier.height(10.dp))
              Text("Поисковая система", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchEngine.entries.forEach { engine ->
                  FilterChip(
                    selected = engine == preferences.searchEngine,
                    onClick = { onSelectSearchEngine(engine) },
                    label = { Text(engine.displayName) },
                    modifier = Modifier.testTag("settings_engine_${engine.id}")
                  )
                }
              }
              MenuToggle("Погода", preferences.showWeatherWidget, "settings_weather", onSetShowWeather)
              MenuToggle("Быстрый доступ", preferences.showSpeedDial, "settings_shortcuts", onSetShowSpeedDial)
            }
            MenuRow(NeoIcons.MyLocation, "Уточнить местоположение", "menu_location") {
              choose(onRequestLocation)
            }
            MenuRow(NeoIcons.Refresh, "Обновить погоду", "menu_weather_refresh") {
              choose(onRefreshWeather)
            }
            Text("Регион определяется по сети. Разрешите местоположение для более точной погоды.",
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cloudSyncAvailable) {
              userEmail?.let {
                Text(it, Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                  style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              MenuRow(NeoIcons.Logout, "Выйти из аккаунта", "sign_out_button") { choose(onSignOut) }
            }
          } else {
            MenuRow(NeoIcons.Add, "Новая вкладка", "menu_new_tab") { choose { onNewTab(false) } }
            MenuRow(NeoIcons.VisibilityOff, "Вкладка инкогнито", "menu_incognito") { choose { onNewTab(true) } }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outline)
            MenuRow(NeoIcons.BookmarkBorder, "Закладки", "menu_item_bookmarks") { choose(onOpenBookmarks) }
            MenuRow(NeoIcons.History, "История", "menu_item_history") { choose(onOpenHistory) }
            MenuRow(NeoIcons.Key, "Пароли", "menu_item_passwords") { choose(onOpenPasswords) }
            if (!activeTab.isStartPage) {
              MenuRow(NeoIcons.Password, "Заполнить пароль", "menu_item_autofill") { choose(onAutofillActiveSite) }
              MenuToggle("Версия для ПК", activeTab.isDesktopMode, "menu_switch_desktop") { choose(onToggleDesktopMode) }
              MenuToggle("Режим чтения", activeTab.isReaderMode, "menu_switch_reader") { choose(onToggleReaderMode) }
            }
            MenuRow(NeoIcons.Shield, "Защита", "menu_item_shield") { choose(onOpenShield) }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outline)
            MenuRow(NeoIcons.Tune, "Настройки", "menu_settings") { settings = true }
          }
        }
      }
    }
  }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, tag: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
      .heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 10.dp).testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(16.dp))
    Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun MenuToggle(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
  }
}
