package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.icons.NeoIcons
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ReaderArticle
import com.example.data.model.ReaderTheme

@Composable
fun ReaderModeOverlay(
  article: ReaderArticle?,
  fontSizeSp: Int,
  readerTheme: ReaderTheme,
  onChangeFontSize: (Int) -> Unit,
  onSelectTheme: (ReaderTheme) -> Unit,
  onCloseReader: () -> Unit,
) {
  val bgColor = Color(readerTheme.backgroundHex)
  val surfaceColor = Color(readerTheme.surfaceHex)
  val textColor = Color(readerTheme.textHex)
  val subTextColor = Color(readerTheme.secondaryTextHex)

  Surface(
    color = bgColor,
    modifier = Modifier
      .fillMaxSize()
      .testTag("reader_mode_overlay")
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Reader Toolbar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(surfaceColor)
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          ReaderTheme.entries.forEach { theme ->
            val selected = theme == readerTheme
            FilterChip(
              selected = selected,
              onClick = { onSelectTheme(theme) },
              label = { Text(text = theme.labelRu) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
              )
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { onChangeFontSize((fontSizeSp - 2).coerceAtLeast(14)) },
            modifier = Modifier.testTag("reader_font_decrease")
          ) {
            Icon(
              imageVector = NeoIcons.TextDecrease,
              contentDescription = stringResource(R.string.reader_font_size),
              tint = textColor
            )
          }
          Text(
            text = "${fontSizeSp}sp",
            style = MaterialTheme.typography.labelLarge,
            color = subTextColor
          )
          IconButton(
            onClick = { onChangeFontSize((fontSizeSp + 2).coerceAtMost(28)) },
            modifier = Modifier.testTag("reader_font_increase")
          ) {
            Icon(
              imageVector = NeoIcons.TextIncrease,
              contentDescription = stringResource(R.string.reader_font_size),
              tint = textColor
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
          IconButton(
            onClick = onCloseReader,
            modifier = Modifier.testTag("reader_close_button")
          ) {
            Icon(
              imageVector = NeoIcons.Close,
              contentDescription = stringResource(R.string.reader_mode_exit),
              tint = textColor
            )
          }
        }
      }

      HorizontalDivider(color = subTextColor.copy(alpha = 0.2f))

      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
          contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          if (article == null) {
            item {
              Text(
                text = stringResource(R.string.reader_extracting),
                color = subTextColor,
                style = MaterialTheme.typography.bodyLarge
              )
            }
          } else {
            item {
              Column {
                Text(
                  text = "${article.domain}  •  ~${article.estimatedMinutes} мин чтения",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = article.title,
                  style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = (fontSizeSp + 6).sp,
                    lineHeight = (fontSizeSp + 14).sp
                  ),
                  color = textColor,
                  fontWeight = FontWeight.Bold
                )
                if (article.byline.isNotBlank() && article.byline != article.domain) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = article.byline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subTextColor
                  )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = subTextColor.copy(alpha = 0.25f))
              }
            }

            if (article.paragraphs.isEmpty()) {
              item {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, subTextColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(18.dp)
                ) {
                  Text(
                    text = stringResource(R.string.reader_empty_fallback),
                    style = MaterialTheme.typography.bodyLarge.copy(
                      fontSize = fontSizeSp.sp,
                      lineHeight = (fontSizeSp * 1.55f).sp
                    ),
                    color = subTextColor
                  )
                }
              }
            } else {
              items(article.paragraphs) { paragraph ->
                Text(
                  text = paragraph,
                  style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.6f).sp
                  ),
                  color = textColor
                )
              }
            }
          }
        }
      }
    }
  }
}
