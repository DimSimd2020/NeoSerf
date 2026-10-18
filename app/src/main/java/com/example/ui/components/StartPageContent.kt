package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BrowserPreferencesState
import com.example.data.model.SearchEngine
import com.example.data.model.SpeedDialTile
import com.example.data.model.WeatherForecast
import com.example.domain.AdBlockerEngine
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartPageContent(
  isIncognito: Boolean,
  preferences: BrowserPreferencesState,
  weatherState: UiState<WeatherForecast>,
  speedDialState: UiState<List<SpeedDialTile>>,
  onSelectSearchEngine: (SearchEngine) -> Unit,
  onOpenUrl: (String) -> Unit,
  onRefreshWeather: () -> Unit,
  onChangeWeatherCity: (String) -> Unit,
  onAddSpeedDial: (title: String, url: String, category: String) -> Unit,
  onDeleteSpeedDial: (String) -> Unit,
  onOpenCustomizationSheet: () -> Unit,
  onOpenShieldSheet: () -> Unit,
) {
  var showCityDialog by remember { mutableStateOf(false) }
  var showAddTileDialog by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // Subtle Cyber-Graphite Ambient Glow
    Canvas(modifier = Modifier.fillMaxSize()) {
      val accent = if (isIncognito) Color(0xFF7B61FF) else Color(0xFF3898EC)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent),
          center = Offset(size.width * 0.85f, size.height * 0.12f),
          radius = size.minDimension * 0.65f
        ),
        center = Offset(size.width * 0.85f, size.height * 0.12f),
        radius = size.minDimension * 0.65f
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Brand Header & Search Engine Switcher
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
          horizontalAlignment = Alignment.Start
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = if (isIncognito) {
                  MaterialTheme.colorScheme.tertiary
                } else {
                  MaterialTheme.colorScheme.primary
                }
              )
              Text(
                text = stringResource(R.string.start_greeting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            OutlinedButton(
              onClick = onOpenCustomizationSheet,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("customize_start_button")
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(R.string.customize_start_page),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.nav_menu),
                style = MaterialTheme.typography.labelLarge
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Search Engine Selector Chips
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            SearchEngine.entries.forEach { engine ->
              val isSelected = engine == preferences.searchEngine
              FilterChip(
                selected = isSelected,
                onClick = { onSelectSearchEngine(engine) },
                label = {
                  Text(
                    text = engine.displayName,
                    style = MaterialTheme.typography.labelLarge
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("engine_chip_${engine.id.lowercase()}")
              )
            }
          }
        }
      }

      // Incognito Mode Banner if active
      if (isIncognito) {
        item {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp)
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.tabs_incognito),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = stringResource(R.string.start_incognito_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
          }
        }
      }

      // Weather Widget
      if (preferences.showWeatherWidget) {
        item {
          WeatherWidgetCard(
            cityQuery = preferences.weatherCity,
            weatherState = weatherState,
            onRefresh = onRefreshWeather,
            onEditCity = { showCityDialog = true },
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp)
          )
        }
      }

      // Privacy & AdBlocker Summary Card
      if (preferences.showShieldSummary) {
        item {
          ShieldSummaryCard(
            adBlockEnabled = preferences.adBlockEnabled,
            adsBlocked = preferences.totalAdsBlocked,
            trackersBlocked = preferences.totalTrackersBlocked,
            onClick = onOpenShieldSheet,
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp)
          )
        }
      }

      // Speed Dial Grid
      if (preferences.showSpeedDial) {
        item {
          SpeedDialSection(
            speedDialState = speedDialState,
            onOpenUrl = onOpenUrl,
            onAddClick = { showAddTileDialog = true },
            onDeleteTile = onDeleteSpeedDial,
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp)
          )
        }
      }
    }
  }

  if (showCityDialog) {
    WeatherCityDialog(
      currentCity = preferences.weatherCity,
      onDismiss = { showCityDialog = false },
      onConfirm = { newCity ->
        showCityDialog = false
        onChangeWeatherCity(newCity)
      }
    )
  }

  if (showAddTileDialog) {
    AddSpeedDialDialog(
      onDismiss = { showAddTileDialog = false },
      onConfirm = { title, url, category ->
        showAddTileDialog = false
        onAddSpeedDial(title, url, category)
      }
    )
  }
}

@Composable
private fun WeatherWidgetCard(
  cityQuery: String,
  weatherState: UiState<WeatherForecast>,
  onRefresh: () -> Unit,
  onEditCity: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
      .testTag("weather_widget_card")
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.WbSunny,
            contentDescription = stringResource(R.string.weather_title),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.weather_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Row {
          IconButton(
            onClick = onEditCity,
            modifier = Modifier
              .size(36.dp)
              .testTag("weather_change_city_button")
          ) {
            Icon(
              imageVector = Icons.Default.EditLocationAlt,
              contentDescription = stringResource(R.string.weather_change_city),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
          }
          IconButton(
            onClick = onRefresh,
            modifier = Modifier
              .size(36.dp)
              .testTag("weather_refresh_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = stringResource(R.string.weather_refresh),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      when (weatherState) {
        is UiState.Loading -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "${stringResource(R.string.weather_loading)} ($cityQuery)",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        is UiState.Error -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = cityQuery,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = weatherState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            TextButton(onClick = onRefresh) {
              Text(text = stringResource(R.string.weather_refresh))
            }
          }
        }

        is UiState.Success -> {
          val forecast = weatherState.data
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${forecast.cityName}, ${forecast.country}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = forecast.conditionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
              )
              Spacer(modifier = Modifier.height(6.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Air,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = stringResource(
                    R.string.weather_wind_humidity,
                    forecast.windSpeedKmh.roundToInt().toString(),
                    forecast.humidityPercent
                  ),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Column(horizontalAlignment = Alignment.End) {
              val tempSign = if (forecast.temperatureC > 0) "+" else ""
              Text(
                text = "$tempSign${forecast.temperatureC.roundToInt()}°C",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(
                  R.string.weather_feels_like,
                  forecast.apparentTempC.roundToInt().toString()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "Макс ${forecast.dailyMaxC.roundToInt()}° / Мин ${forecast.dailyMinC.roundToInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ShieldSummaryCard(
  adBlockEnabled: Boolean,
  adsBlocked: Int,
  trackersBlocked: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val savedKb = (adsBlocked * 38 + trackersBlocked * 14)
  val savedLabel = if (savedKb >= 1024) {
    String.format("%.1f МБ", savedKb / 1024f)
  } else {
    "$savedKb КБ"
  }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
      .clickable { onClick() }
      .testTag("shield_summary_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
              if (adBlockEnabled) {
                MaterialTheme.colorScheme.secondaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant
              }
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = stringResource(R.string.adblock_title),
            tint = if (adBlockEnabled) {
              MaterialTheme.colorScheme.secondary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.adblock_title),
            style = MaterialTheme.typography.titleMedium.copy(
              hyphens = Hyphens.None,
              lineBreak = LineBreak.Simple
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("shield_summary_title")
          )
          Text(
            text = if (adBlockEnabled) "Активна фильтрация рекламы и трекеров" else "Фильтрация отключена",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        StatMetricColumn(value = adsBlocked.toString(), label = "Реклама")
        StatMetricColumn(value = trackersBlocked.toString(), label = "Трекеры")
        StatMetricColumn(value = savedLabel, label = "Трафик")
      }
    }
  }
}

@Composable
private fun StatMetricColumn(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedDialSection(
  speedDialState: UiState<List<SpeedDialTile>>,
  onOpenUrl: (String) -> Unit,
  onAddClick: () -> Unit,
  onDeleteTile: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.speed_dial_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
      )
      TextButton(
        onClick = onAddClick,
        modifier = Modifier.testTag("add_speed_dial_button")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = stringResource(R.string.speed_dial_add),
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = stringResource(R.string.speed_dial_add))
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (speedDialState) {
      is UiState.Loading -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      is UiState.Error -> {
        Text(
          text = speedDialState.message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error
        )
      }

      is UiState.Success -> {
        val tiles = speedDialState.data
        if (tiles.isEmpty()) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = stringResource(R.string.speed_dial_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp)
            )
          }
        } else {
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            tiles.forEach { tile ->
              SpeedDialCard(
                tile = tile,
                onClick = { onOpenUrl(tile.url) },
                onDelete = { onDeleteTile(tile.id) },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SpeedDialCard(
  tile: SpeedDialTile,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val domain = AdBlockerEngine.extractDomain(tile.url).ifEmpty { tile.url }
  val initial = tile.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "N"

  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(16.dp),
    modifier = modifier
      .widthIn(min = 100.dp)
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .testTag("speed_dial_tile_${tile.id}")
  ) {
    Column(
      modifier = Modifier.padding(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.action_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = tile.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Text(
        text = domain,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun WeatherCityDialog(
  currentCity: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var cityInput by remember { mutableStateOf(currentCity) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.weather_city_dialog_title)) },
    text = {
      Column {
        OutlinedTextField(
          value = cityInput,
          onValueChange = { cityInput = it },
          label = { Text(text = stringResource(R.string.weather_city_input_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_city_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(cityInput) },
        modifier = Modifier.testTag("weather_city_save_button")
      ) {
        Text(text = stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(R.string.action_cancel))
      }
    }
  )
}

@Composable
private fun AddSpeedDialDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, url: String, category: String) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Быстрый доступ") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.speed_dial_dialog_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(text = stringResource(R.string.speed_dial_name_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("speed_dial_title_input")
        )
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text(text = stringResource(R.string.speed_dial_url_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("speed_dial_url_input")
        )
        OutlinedTextField(
          value = category,
          onValueChange = { category = it },
          label = { Text(text = stringResource(R.string.speed_dial_category_label)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (url.isNotBlank()) {
            onConfirm(title.ifBlank { url }, url, category)
          }
        },
        modifier = Modifier.testTag("speed_dial_save_button")
      ) {
        Text(text = stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(R.string.action_cancel))
      }
    }
  )
}
