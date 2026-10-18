package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BrowserPreferencesState
import com.example.data.model.BrowserTab
import com.example.data.model.SearchEngine
import com.example.domain.AdBlockerEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsModalSheet(
  tabs: List<BrowserTab>,
  activeTabId: String,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onNewRegularTab: () -> Unit,
  onNewIncognitoTab: () -> Unit,
  onCloseAllIncognito: () -> Unit,
  onDismiss: () -> Unit,
) {
  var showIncognitoOnly by remember {
    mutableStateOf(tabs.firstOrNull { it.id == activeTabId }?.isIncognito == true)
  }
  val filteredTabs = tabs.filter { it.isIncognito == showIncognitoOnly }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.tabs_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = !showIncognitoOnly,
            onClick = { showIncognitoOnly = false },
            label = {
              Text("${stringResource(R.string.tabs_regular)} (${tabs.count { !it.isIncognito }})")
            },
            modifier = Modifier.testTag("tabs_filter_regular")
          )
          FilterChip(
            selected = showIncognitoOnly,
            onClick = { showIncognitoOnly = true },
            label = {
              Text("${stringResource(R.string.tabs_incognito)} (${tabs.count { it.isIncognito }})")
            },
            modifier = Modifier.testTag("tabs_filter_incognito")
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(filteredTabs, key = { it.id }) { tab ->
          val isSelected = tab.id == activeTabId
          val accent = if (tab.isIncognito) {
            MaterialTheme.colorScheme.tertiary
          } else {
            MaterialTheme.colorScheme.primary
          }
          Card(
            colors = CardDefaults.cardColors(
              containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant
              } else {
                MaterialTheme.colorScheme.background
              }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp)
              )
              .clickable {
                onSelectTab(tab.id)
                onDismiss()
              }
              .testTag("tab_item_${tab.id}")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Icon(
                  imageVector = if (tab.isIncognito) Icons.Default.VisibilityOff else Icons.Default.Public,
                  contentDescription = null,
                  tint = accent,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = tab.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = if (tab.url.isBlank()) "neoserf://start" else tab.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              IconButton(
                onClick = { onCloseTab(tab.id) },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = stringResource(R.string.action_close),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = {
            onNewRegularTab()
            onDismiss()
          },
          modifier = Modifier
            .weight(1f)
            .testTag("new_regular_tab_button")
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = stringResource(R.string.tabs_new_regular))
        }

        OutlinedButton(
          onClick = {
            onNewIncognitoTab()
            onDismiss()
          },
          modifier = Modifier
            .weight(1f)
            .testTag("new_incognito_tab_button")
        ) {
          Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.tabs_incognito),
            color = MaterialTheme.colorScheme.tertiary
          )
        }
      }

      if (tabs.any { it.isIncognito }) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = {
            onCloseAllIncognito()
            onDismiss()
          },
          modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
          Text(
            text = stringResource(R.string.tabs_close_incognito),
            color = MaterialTheme.colorScheme.tertiary
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockShieldSheet(
  activeTab: BrowserTab,
  preferences: BrowserPreferencesState,
  onToggleAdBlock: (Boolean) -> Unit,
  onToggleCurrentDomainWhitelist: () -> Unit,
  onResetStats: () -> Unit,
  onDismiss: () -> Unit,
) {
  val currentDomain = AdBlockerEngine.extractDomain(activeTab.url)
  val isDomainWhitelisted = currentDomain.isNotEmpty() &&
    preferences.whitelistedDomains.contains(currentDomain)
  val savedKb = preferences.totalAdsBlocked * 38 + preferences.totalTrackersBlocked * 14

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = stringResource(R.string.adblock_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.adblock_enabled),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
          checked = preferences.adBlockEnabled,
          onCheckedChange = onToggleAdBlock,
          modifier = Modifier.testTag("adblock_master_switch")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        ShieldMetricBox(
          value = preferences.totalAdsBlocked.toString(),
          label = stringResource(R.string.adblock_ads_blocked),
          modifier = Modifier.weight(1f)
        )
        ShieldMetricBox(
          value = preferences.totalTrackersBlocked.toString(),
          label = stringResource(R.string.adblock_trackers_blocked),
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      ShieldMetricBox(
        value = if (savedKb >= 1024) String.format("%.2f МБ", savedKb / 1024f) else "$savedKb КБ",
        label = stringResource(R.string.adblock_data_saved),
        modifier = Modifier.fillMaxWidth()
      )

      if (currentDomain.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.adblock_whitelist_domain),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = currentDomain,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
          Switch(
            checked = isDomainWhitelisted,
            onCheckedChange = { onToggleCurrentDomainWhitelist() }
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        TextButton(onClick = onResetStats) {
          Text(text = stringResource(R.string.adblock_reset_stats))
        }
        Button(onClick = onDismiss) {
          Text(text = stringResource(R.string.action_close))
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun ShieldMetricBox(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(MaterialTheme.colorScheme.background)
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
      .padding(14.dp)
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserMenuAndCustomizeSheet(
  activeTab: BrowserTab,
  preferences: BrowserPreferencesState,
  userEmail: String?,
  onOpenBookmarks: () -> Unit,
  onOpenPasswords: () -> Unit,
  onOpenHistory: () -> Unit,
  onToggleDesktopMode: () -> Unit,
  onToggleReaderMode: () -> Unit,
  onAutofillActiveSite: () -> Unit,
  onSelectSearchEngine: (SearchEngine) -> Unit,
  onSetShowWeather: (Boolean) -> Unit,
  onSetShowSpeedDial: (Boolean) -> Unit,
  onSetShowShieldSummary: (Boolean) -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary
        )
        if (!userEmail.isNullOrBlank()) {
          Text(
            text = "Аккаунт Google: $userEmail",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
          )
        }
      }

      // Primary Navigation Sections: Bookmarks, Passwords, History, Autofill
      item {
        MenuActionRow(
          icon = Icons.Default.Bookmark,
          title = stringResource(R.string.menu_bookmarks),
          testTag = "menu_item_bookmarks",
          onClick = {
            onDismiss()
            onOpenBookmarks()
          }
        )
      }

      item {
        MenuActionRow(
          icon = Icons.Default.Lock,
          title = stringResource(R.string.menu_passwords),
          testTag = "menu_item_passwords",
          onClick = {
            onDismiss()
            onOpenPasswords()
          }
        )
      }

      item {
        MenuActionRow(
          icon = Icons.Default.History,
          title = stringResource(R.string.menu_history),
          testTag = "menu_item_history",
          onClick = {
            onDismiss()
            onOpenHistory()
          }
        )
      }

      item {
        MenuActionRow(
          icon = Icons.Default.Key,
          title = stringResource(R.string.menu_autofill_password),
          testTag = "menu_item_autofill",
          onClick = {
            onDismiss()
            onAutofillActiveSite()
          }
        )
      }

      item {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      }

      // Page Mode Switches: PC Mode & Reader Mode
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Computer,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_desktop_mode),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Switch(
            checked = activeTab.isDesktopMode,
            onCheckedChange = {
              onToggleDesktopMode()
              onDismiss()
            },
            modifier = Modifier.testTag("menu_switch_desktop")
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.MenuBook,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.menu_reader_mode),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Switch(
            checked = activeTab.isReaderMode,
            onCheckedChange = {
              onToggleReaderMode()
              onDismiss()
            },
            modifier = Modifier.testTag("menu_switch_reader")
          )
        }
      }

      item {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      }

      // Start Page Widget Customization
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.customize_start_page),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      item {
        ToggleSettingRow(
          label = stringResource(R.string.customize_show_weather),
          checked = preferences.showWeatherWidget,
          onCheckedChange = onSetShowWeather
        )
        ToggleSettingRow(
          label = stringResource(R.string.customize_show_speed_dial),
          checked = preferences.showSpeedDial,
          onCheckedChange = onSetShowSpeedDial
        )
        ToggleSettingRow(
          label = stringResource(R.string.customize_show_shield_summary),
          checked = preferences.showShieldSummary,
          onCheckedChange = onSetShowShieldSummary
        )
      }

      item {
        Text(
          text = stringResource(R.string.customize_default_engine),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          SearchEngine.entries.forEach { engine ->
            FilterChip(
              selected = engine == preferences.searchEngine,
              onClick = { onSelectSearchEngine(engine) },
              label = { Text(engine.displayName) }
            )
          }
        }
      }

      item {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
          onClick = {
            onDismiss()
            onSignOut()
          },
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sign_out_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(R.string.auth_sign_out))
        }
        Spacer(modifier = Modifier.height(18.dp))
      }
    }
  }
}

@Composable
private fun MenuActionRow(
  icon: ImageVector,
  title: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .clickable { onClick() }
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
      )
    }
    Spacer(modifier = Modifier.width(14.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun ToggleSettingRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f)
    )
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
