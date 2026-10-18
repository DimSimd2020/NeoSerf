package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.icons.NeoIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BrowserPreferencesState
import com.example.data.model.SpeedDialTile
import com.example.data.model.WeatherForecast
import com.example.domain.AdBlockerEngine
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun StartPageContent(
  isIncognito: Boolean,
  preferences: BrowserPreferencesState,
  weatherState: UiState<WeatherForecast>,
  speedDialState: UiState<List<SpeedDialTile>>,
  onOpenUrl: (String) -> Unit,
  onRefreshWeather: () -> Unit,
  onAddSpeedDial: (title: String, url: String, category: String) -> Unit,
  onDeleteSpeedDial: (String) -> Unit,
) {
  var showAddTileDialog by remember { mutableStateOf(false) }
  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
      .testTag("start_page")
  ) {
    val pageHeight = maxHeight
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .heightIn(min = pageHeight).padding(horizontal = 24.dp, vertical = 40.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          painter = painterResource(R.drawable.neoserf_mark),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )
        if (isIncognito) {
          Spacer(Modifier.height(8.dp))
          Text(
            "Инкогнито",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary
          )
        }
        Spacer(Modifier.height(32.dp))
        StartSearchField(onSubmit = onOpenUrl)
        if (preferences.showWeatherWidget) {
          Spacer(Modifier.height(28.dp))
          WeatherWidget(weatherState, onRefreshWeather)
        }
        if (preferences.showSpeedDial) {
          Spacer(Modifier.height(32.dp))
          SpeedDialSection(
            speedDialState = speedDialState,
            onOpenUrl = onOpenUrl,
            onAddClick = { showAddTileDialog = true },
            onDeleteTile = onDeleteSpeedDial,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
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
private fun WeatherWidget(state: UiState<WeatherForecast>, onRefresh: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("weather_widget_card"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    when (state) {
      UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
        Spacer(Modifier.width(10.dp))
        Text("Определяем погоду…", color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium)
      }
      is UiState.Error -> {
        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        TextButton(onClick = onRefresh, modifier = Modifier.testTag("weather_refresh_button")) {
          Text("Повторить")
        }
      }
      is UiState.Success -> {
        val weather = state.data
        val icon = when (weather.weatherCode) {
          0, 1 -> if (weather.isDay) NeoIcons.WbSunny else NeoIcons.NightsStay
          2, 3 -> NeoIcons.Cloud
          45, 48 -> NeoIcons.BlurOn
          71, 73, 75, 77, 85, 86 -> NeoIcons.AcUnit
          95, 96, 99 -> NeoIcons.Thunderstorm
          else -> NeoIcons.WaterDrop
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
          Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(10.dp))
          Text("${weather.temperatureC.roundToInt()}°", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface)
          Spacer(Modifier.width(12.dp))
          Text(weather.cityName, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.height(6.dp))
        Text("${weather.conditionText} · ощущается как ${weather.apparentTempC.roundToInt()}°",
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("${weather.locationSource} · Open-Meteo",
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center)
      }
    }
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
          imageVector = NeoIcons.Add,
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
            imageVector = NeoIcons.Close,
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
