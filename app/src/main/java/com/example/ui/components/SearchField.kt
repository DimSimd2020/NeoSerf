package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StartSearchField(onSubmit: (String) -> Unit) {
  var query by remember { mutableStateOf("") }
  var focused by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val submit = {
    if (query.isNotBlank()) {
      val input = query.trim()
      focusManager.clearFocus(force = true)
      keyboard?.hide()
      onSubmit(input)
    }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
      .border(
        1.dp,
        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        RoundedCornerShape(28.dp)
      )
      .padding(start = 18.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
    Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp)) {
      if (query.isEmpty()) {
        Text(
          "Поиск или адрес сайта",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      BasicTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        modifier = Modifier.fillMaxWidth()
          .onFocusChanged { focused = it.isFocused }
          .onPreviewKeyEvent {
            if (it.key != Key.Enter && it.key != Key.NumPadEnter) false
            else {
              // Submit on release so focus cannot move to a navigation button
              // between Enter-down and Enter-up and activate that button.
              if (it.type == KeyEventType.KeyUp) submit()
              true
            }
          }
          .testTag("omnibox_input")
      )
    }
    IconButton(onClick = { submit() }, modifier = Modifier.size(48.dp).testTag("start_search_submit")) {
      Icon(Icons.AutoMirrored.Filled.ArrowForward, "Найти", tint = MaterialTheme.colorScheme.primary)
    }
  }
}
