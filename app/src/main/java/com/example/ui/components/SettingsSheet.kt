package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.BrowserPreferencesState
import com.example.data.model.AppTheme
import com.example.data.model.SearchEngine
import com.example.ui.icons.NeoIcons

/**
 * Settings as a bottom sheet, in the same style as the open tabs sheet.
 * Test tags are kept from the former in-menu settings page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
  preferences: BrowserPreferencesState,
  userEmail: String?,
  cloudSyncAvailable: Boolean,
  onSelectSearchEngine: (SearchEngine) -> Unit,
  onSetShowWeather: (Boolean) -> Unit,
  onSetShowSpeedDial: (Boolean) -> Unit,
  onSetAppTheme: (AppTheme) -> Unit,
  onRefreshWeather: () -> Unit,
  onRequestLocation: () -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  fun choose(action: () -> Unit) {
    onDismiss()
    action()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .testTag("settings_sheet")
    ) {
      Text(
        text = "Настройки",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
      )

      SectionLabel("Оформление")
      // Three equal tiles in one row; IntrinsicSize keeps them the same height
      // even when one label wraps onto a second line.
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        AppTheme.entries.forEach { theme ->
          ThemeTile(
            theme = theme,
            selected = theme == preferences.appTheme,
            onClick = { onSetAppTheme(theme) },
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .testTag("settings_theme_${theme.name}")
          )
        }
      }

      SectionLabel("Поисковая система")
      SettingsGroup {
        SearchEngine.entries.forEachIndexed { index, engine ->
          if (index > 0) GroupDivider()
          EngineRow(
            label = engine.displayName,
            selected = engine == preferences.searchEngine,
            onClick = { onSelectSearchEngine(engine) },
            modifier = Modifier.testTag("settings_engine_${engine.id}")
          )
        }
      }

      SectionLabel("Стартовая страница")
      SettingsGroup {
        SwitchRow("Погода", preferences.showWeatherWidget, "settings_weather", onSetShowWeather)
        GroupDivider()
        SwitchRow("Быстрый доступ", preferences.showSpeedDial, "settings_shortcuts", onSetShowSpeedDial)
      }

      Spacer(Modifier.height(12.dp))
      SettingsGroup {
        ActionRow(NeoIcons.MyLocation, "Уточнить местоположение", "menu_location") {
          choose(onRequestLocation)
        }
        GroupDivider()
        ActionRow(NeoIcons.Refresh, "Обновить погоду", "menu_weather_refresh") {
          choose(onRefreshWeather)
        }
      }
      Text(
        text = "Регион определяется по сети. Разрешите местоположение для более точной погоды.",
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (cloudSyncAvailable) {
        userEmail?.let {
          Text(
            text = it,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(Modifier.height(4.dp))
        SettingsGroup {
          ActionRow(NeoIcons.Logout, "Выйти из аккаунта", "sign_out_button") { choose(onSignOut) }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

/**
 * The default unchecked switch blends into the dark surface, so the thumb and the outline
 * use the secondary text colour to stay visible.
 */
@Composable
internal fun neoSwitchColors(): SwitchColors {
  val colors = MaterialTheme.colorScheme
  return SwitchDefaults.colors(
    uncheckedThumbColor = colors.onSurfaceVariant,
    uncheckedTrackColor = colors.surfaceContainerHighest,
    uncheckedBorderColor = colors.onSurfaceVariant
  )
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    tonalElevation = 0.dp
  ) {
    Column(content = content)
  }
}

@Composable
private fun GroupDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(horizontal = 16.dp),
    color = MaterialTheme.colorScheme.outline
  )
}

@Composable
private fun ThemeTile(
  theme: AppTheme,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(16.dp)
  val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
  Column(
    modifier = modifier
      .clip(shape)
      .background(if (selected) colors.primaryContainer else Color.Transparent)
      .border(1.dp, if (selected) colors.primary else colors.outline, shape)
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .padding(horizontal = 6.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
  ) {
    Icon(
      imageVector = when (theme) {
        AppTheme.SYSTEM -> NeoIcons.Device
        AppTheme.LIGHT -> NeoIcons.WbSunny
        AppTheme.DARK -> NeoIcons.NightsStay
      },
      contentDescription = null,
      modifier = Modifier.size(22.dp),
      tint = contentColor
    )
    Text(
      text = theme.title,
      style = MaterialTheme.typography.labelLarge,
      color = contentColor,
      textAlign = TextAlign.Center,
      maxLines = 2
    )
  }
}

@Composable
private fun EngineRow(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
    RadioButton(selected = selected, onClick = null)
  }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
      .padding(horizontal = 16.dp)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
    Switch(checked = checked, onCheckedChange = null, colors = neoSwitchColors())
  }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, tag: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(16.dp))
    Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
  }
}
