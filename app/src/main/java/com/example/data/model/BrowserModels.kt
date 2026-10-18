package com.example.data.model

import java.net.URLEncoder
import java.util.UUID

enum class SearchEngine(
  val id: String,
  val displayName: String,
  val shortTag: String,
  val homeUrl: String,
  val description: String,
) {
  GOOGLE(
    id = "Google",
    displayName = "Google",
    shortTag = "G",
    homeUrl = "https://www.google.com",
    description = "Универсальный поиск Google"
  ),
  DUCKDUCKGO(
    id = "DuckDuckGo",
    displayName = "DuckDuckGo",
    shortTag = "DDG",
    homeUrl = "https://duckduckgo.com",
    description = "Приватный поиск без отслеживания"
  ),
  YANDEX(
    id = "Yandex",
    displayName = "Яндекс",
    shortTag = "YA",
    homeUrl = "https://ya.ru",
    description = "Поиск по рунету и сервисам"
  ),
  BING(
    id = "Bing",
    displayName = "Microsoft Bing",
    shortTag = "B",
    homeUrl = "https://www.bing.com",
    description = "Глобальный поиск Microsoft"
  ),
  BRAVE(
    id = "Brave",
    displayName = "Brave Search",
    shortTag = "BRV",
    homeUrl = "https://search.brave.com",
    description = "Независимый приватный индекс"
  );

  fun buildSearchUrl(query: String): String {
    val encoded = try {
      URLEncoder.encode(query.trim(), "UTF-8")
    } catch (e: Exception) {
      query.trim().replace(" ", "+")
    }
    return when (this) {
      GOOGLE -> "https://www.google.com/search?q=$encoded"
      DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded"
      YANDEX -> "https://ya.ru/search/?text=$encoded"
      BING -> "https://www.bing.com/search?q=$encoded"
      BRAVE -> "https://search.brave.com/search?q=$encoded"
    }
  }

  companion object {
    fun fromId(id: String): SearchEngine {
      return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GOOGLE
    }
  }
}

enum class ReaderTheme(
  val id: String,
  val labelRu: String,
  val backgroundHex: Long,
  val surfaceHex: Long,
  val textHex: Long,
  val secondaryTextHex: Long,
) {
  GRAPHITE(
    id = "graphite",
    labelRu = "Графит",
    backgroundHex = 0xFF14171CL,
    surfaceHex = 0xFF1E232BL,
    textHex = 0xFFE6EDF3L,
    secondaryTextHex = 0xFF9BA3B0L
  ),
  AMOLED(
    id = "amoled",
    labelRu = "Черный",
    backgroundHex = 0xFF050608L,
    surfaceHex = 0xFF101318L,
    textHex = 0xFFD9E2ECL,
    secondaryTextHex = 0xFF828D9CL
  ),
  SEPIA(
    id = "sepia",
    labelRu = "Сепия",
    backgroundHex = 0xFF231F1AL,
    surfaceHex = 0xFF2E2922L,
    textHex = 0xFFEADBC8L,
    secondaryTextHex = 0xFFB8A68EL
  );

  companion object {
    fun fromId(id: String): ReaderTheme = entries.firstOrNull { it.id == id } ?: GRAPHITE
  }
}

data class ReaderArticle(
  val title: String,
  val domain: String,
  val byline: String,
  val paragraphs: List<String>,
  val estimatedMinutes: Int,
)

data class BrowserTab(
  val id: String = UUID.randomUUID().toString(),
  val title: String = "Стартовая страница",
  val url: String = "",
  val isIncognito: Boolean = false,
  val isDesktopMode: Boolean = false,
  val isReaderMode: Boolean = false,
  val readerArticle: ReaderArticle? = null,
  val isLoading: Boolean = false,
  val progress: Int = 0,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val adsBlockedInTab: Int = 0,
  val trackersBlockedInTab: Int = 0,
) {
  val isStartPage: Boolean
    get() = url.isBlank()
}

data class WeatherForecast(
  val cityName: String,
  val country: String,
  val temperatureC: Double,
  val apparentTempC: Double,
  val windSpeedKmh: Double,
  val humidityPercent: Int,
  val weatherCode: Int,
  val conditionText: String,
  val dailyMaxC: Double,
  val dailyMinC: Double,
)
