package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppTheme
import com.example.data.model.SearchEngine
import com.example.ui.icons.NeoIcons

private const val PAGE_COUNT = 2

/**
 * First-launch screen: step 1 picks the theme, step 2 the search engine. Choices are saved immediately,
 * so the theme previews live; [onFinish] marks onboarding as done.
 */
@Composable
fun WelcomeScreen(
  appTheme: AppTheme,
  searchEngine: SearchEngine,
  onThemeSelected: (AppTheme) -> Unit,
  onSearchEngineSelected: (SearchEngine) -> Unit,
  onFinish: () -> Unit,
) {
  var page by rememberSaveable { mutableIntStateOf(0) }
  BackHandler(enabled = page > 0) { page-- }
  Column(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
      .safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp).testTag("welcome_screen"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    AnimatedContent(
      targetState = page,
      transitionSpec = {
        val forward = targetState > initialState
        val enter = slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { width -> if (forward) width / 3 else -width / 3 } +
          fadeIn(tween(360, delayMillis = 60))
        val exit = slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { width -> if (forward) -width / 3 else width / 3 } +
          fadeOut(tween(220))
        enter togetherWith exit
      },
      modifier = Modifier.weight(1f).fillMaxWidth(),
      label = "welcome_page"
    ) { current ->
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          if (current == 0) ThemePage(appTheme, onThemeSelected) else SearchPage(searchEngine, onSearchEngineSelected)
        }
      }
    }
    Row(
      modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(Modifier.weight(1f)) {
        if (page > 0) {
          TextButton(onClick = { page-- }, modifier = Modifier.testTag("welcome_back")) {
            Icon(NeoIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Назад")
          }
        }
      }
      StepDots(page)
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
        Button(
          onClick = { if (page + 1 < PAGE_COUNT) page++ else onFinish() },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(48.dp).testTag("welcome_next")
        ) {
          Text(if (page + 1 < PAGE_COUNT) "Далее" else "Начать", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@Composable
private fun ThemePage(selected: AppTheme, onSelected: (AppTheme) -> Unit) {
  Icon(
    painter = painterResource(R.drawable.neoserf_mark),
    contentDescription = null,
    tint = Color.Unspecified,
    modifier = Modifier.size(88.dp)
  )
  Spacer(Modifier.height(18.dp))
  Heading("Добро пожаловать в NeoSerf", "Выберите, как будет выглядеть браузер. Это можно изменить в настройках.")
  Spacer(Modifier.height(28.dp))
  val icons = mapOf(AppTheme.SYSTEM to NeoIcons.Device, AppTheme.LIGHT to NeoIcons.WbSunny, AppTheme.DARK to NeoIcons.NightsStay)
  AppTheme.entries.forEach { theme ->
    ChoiceCard(
      title = theme.title,
      subtitle = null,
      icon = icons.getValue(theme),
      selected = theme == selected,
      tag = "welcome_theme_${theme.name}",
      onClick = { onSelected(theme) }
    )
    Spacer(Modifier.height(10.dp))
  }
}

@Composable
private fun SearchPage(selected: SearchEngine, onSelected: (SearchEngine) -> Unit) {
  Heading("Поиск по умолчанию", "Что открывать, когда вы ищете в адресной строке.")
  Spacer(Modifier.height(24.dp))
  SearchEngine.entries.forEach { engine ->
    ChoiceCard(
      title = engine.displayName,
      subtitle = engine.description,
      icon = NeoIcons.Search,
      selected = engine == selected,
      tag = "welcome_engine_${engine.id}",
      onClick = { onSelected(engine) }
    )
    Spacer(Modifier.height(10.dp))
  }
}

@Composable
private fun Heading(title: String, subtitle: String) {
  Text(
    title,
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onBackground,
    textAlign = TextAlign.Center
  )
  Spacer(Modifier.height(8.dp))
  Text(
    subtitle,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center
  )
}

@Composable
private fun ChoiceCard(
  title: String,
  subtitle: String?,
  icon: ImageVector,
  selected: Boolean,
  tag: String,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "choice_border")
  val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceVariant, label = "choice_container")
  Surface(
    onClick = onClick,
    selected = selected,
    shape = RoundedCornerShape(16.dp),
    color = container,
    border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    modifier = Modifier.fillMaxWidth().testTag(tag)
  ) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
          color = if (selected) colors.onPrimaryContainer else colors.onSurface,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (subtitle != null) {
          Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun StepDots(page: Int) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(PAGE_COUNT) { index ->
      val width by animateDpAsState(if (index == page) 22.dp else 8.dp, label = "dot_width")
      val color by animateColorAsState(
        if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "dot_color"
      )
      Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
    }
  }
}
