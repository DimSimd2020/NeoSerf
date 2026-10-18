package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.ui.icons.NeoIcons
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BrowserTab

@Composable
fun TopOmniboxBar(
  activeTab: BrowserTab,
  isBookmarked: Boolean,
  adBlockEnabled: Boolean,
  onSubmitInput: (String) -> Unit,
  onToggleBookmark: () -> Unit,
  onReload: () -> Unit,
  onOpenShieldSheet: () -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  var textValue by remember(activeTab.id, activeTab.url) {
    mutableStateOf(activeTab.url)
  }
  var isFocused by remember { mutableStateOf(false) }
  // IME Go and hardware Enter can both fire for one submit; only navigate once.
  var submitInFlight by remember { mutableStateOf(false) }

  fun submitOmnibox() {
    if (submitInFlight) return
    submitInFlight = true
    onSubmitInput(textValue)
  }

  LaunchedEffect(activeTab.url) {
    if (!isFocused) {
      textValue = activeTab.url
    }
  }

  val accentColor = if (activeTab.isIncognito) {
    MaterialTheme.colorScheme.tertiary
  } else {
    MaterialTheme.colorScheme.primary
  }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 0.dp,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp)
          .onPreviewKeyEvent { event ->
            if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp && isFocused) {
              submitOmnibox()
              keyboardController?.hide()
              focusManager.clearFocus(force = true)
            }
            true
          },
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Main Address & Search Field (Omnibox)
        Row(
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
              width = if (isFocused) 1.5.dp else 1.dp,
              color = if (isFocused) accentColor else MaterialTheme.colorScheme.outline,
              shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // AdBlock / SSL Shield Badge inside Omnibox
          val blockedInTab = activeTab.adsBlockedInTab + activeTab.trackersBlockedInTab
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .focusProperties { canFocus = false }
              .onPreviewKeyEvent { event -> event.key == Key.Enter || event.key == Key.NumPadEnter }
              .semantics {
                role = Role.Button
                onClick {
                  onOpenShieldSheet()
                  true
                }
              }
              .pointerInput(Unit) {
                detectTapGestures(onTap = { onOpenShieldSheet() })
              }
              .padding(horizontal = 4.dp, vertical = 4.dp)
              .testTag("omnibox_shield_badge"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (adBlockEnabled) NeoIcons.Shield else NeoIcons.Security,
              contentDescription = stringResource(R.string.cd_shield_stats),
              tint = if (adBlockEnabled) {
                MaterialTheme.colorScheme.secondary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              modifier = Modifier.size(18.dp)
            )
            if (blockedInTab > 0) {
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = blockedInTab.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.width(6.dp))

          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
          ) {
            if (textValue.isEmpty()) {
              Text(
                text = if (activeTab.isIncognito) {
                  stringResource(R.string.omnibox_incognito_hint)
                } else {
                  stringResource(R.string.omnibox_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            BasicTextField(
              value = textValue,
              onValueChange = { textValue = it },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
              ),
              cursorBrush = SolidColor(accentColor),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
              ),
              keyboardActions = KeyboardActions(
                onGo = {
                  submitOmnibox()
                  keyboardController?.hide()
                  focusManager.clearFocus(force = true)
                }
              ),
              modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                  isFocused = state.isFocused
                  if (state.isFocused) {
                    submitInFlight = false
                  }
                }
                .onPreviewKeyEvent { event ->
                  val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                  if (!isEnter) return@onPreviewKeyEvent false
                  if (event.type == KeyEventType.KeyUp) {
                    submitOmnibox()
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                  }
                  // Keep focus until release so Enter cannot activate a navigation button.
                  true
                }
                .testTag("omnibox_input")
            )
          }

          if (textValue.isNotEmpty() && isFocused) {
            IconButton(
              onClick = { textValue = "" },
              modifier = Modifier
                .size(32.dp)
                .testTag("omnibox_clear_button")
            ) {
              Icon(
                imageVector = NeoIcons.Close,
                contentDescription = stringResource(R.string.cd_clear_input),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
            }
          } else if (!activeTab.isStartPage) {
            IconButton(
              onClick = onReload,
              modifier = Modifier
                .size(32.dp)
                .testTag("omnibox_reload_button")
            ) {
              Icon(
                imageVector = NeoIcons.Refresh,
                contentDescription = stringResource(R.string.cd_reload_page),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Quick Action: Bookmark Current Page
        if (!activeTab.isStartPage) {
          IconButton(
            onClick = onToggleBookmark,
            modifier = Modifier
              .size(44.dp)
              .testTag("bookmark_toggle_button")
          ) {
            Icon(
              imageVector = if (isBookmarked) NeoIcons.Bookmark else NeoIcons.BookmarkBorder,
              contentDescription = stringResource(R.string.cd_bookmark_toggle),
              tint = if (isBookmarked) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              }
            )
          }
        }
      }

      AnimatedVisibility(visible = activeTab.isLoading) {
        LinearProgressIndicator(
          progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
          color = accentColor,
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
        )
      }

      HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
      )
    }
  }
}

@Composable
fun BottomBrowserBar(
  activeTab: BrowserTab,
  tabCount: Int,
  onBack: () -> Unit,
  onForward: () -> Unit,
  onHome: () -> Unit,
  onOpenTabs: () -> Unit,
  onOpenMenu: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 0.dp,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
      HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          enabled = !activeTab.isStartPage || activeTab.isReaderMode,
          modifier = Modifier
            .size(48.dp)
            .testTag("nav_back_button")
        ) {
          Icon(
            imageVector = NeoIcons.ArrowBack,
            contentDescription = stringResource(R.string.nav_back),
            tint = if (!activeTab.isStartPage || activeTab.isReaderMode) {
              MaterialTheme.colorScheme.onSurface
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
          )
        }

        IconButton(
          onClick = onForward,
          enabled = activeTab.canGoForward && !activeTab.isStartPage,
          modifier = Modifier
            .size(48.dp)
            .testTag("nav_forward_button")
        ) {
          Icon(
            imageVector = NeoIcons.ArrowForward,
            contentDescription = stringResource(R.string.nav_forward),
            tint = if (activeTab.canGoForward && !activeTab.isStartPage) {
              MaterialTheme.colorScheme.onSurface
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
          )
        }

        IconButton(
          onClick = onHome,
          modifier = Modifier
            .size(48.dp)
            .testTag("nav_home_button")
        ) {
          Icon(
            imageVector = NeoIcons.Home,
            contentDescription = stringResource(R.string.nav_home),
            tint = if (activeTab.isStartPage) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurface
            }
          )
        }

        IconButton(
          onClick = onOpenTabs,
          modifier = Modifier
            .size(48.dp)
            .testTag("nav_tabs_button")
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(8.dp))
              .border(
                width = 1.5.dp,
                color = if (activeTab.isIncognito) {
                  MaterialTheme.colorScheme.tertiary
                } else {
                  MaterialTheme.colorScheme.primary
                },
                shape = RoundedCornerShape(8.dp)
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = tabCount.toString(),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = if (activeTab.isIncognito) {
                MaterialTheme.colorScheme.tertiary
              } else {
                MaterialTheme.colorScheme.primary
              }
            )
          }
        }

        IconButton(
          onClick = onOpenMenu,
          modifier = Modifier
            .size(48.dp)
            .testTag("nav_menu_button")
        ) {
          Icon(
            imageVector = NeoIcons.MoreVert,
            contentDescription = stringResource(R.string.nav_menu),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}
