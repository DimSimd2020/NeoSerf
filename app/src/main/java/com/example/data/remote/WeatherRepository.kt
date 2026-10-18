package com.example.data.remote

import com.example.data.model.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WeatherRepository {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

  suspend fun fetchWeatherForCity(cityQuery: String): Result<WeatherForecast> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanCity = cityQuery.trim().ifEmpty { "Москва" }
      val encodedCity = URLEncoder.encode(cleanCity, "UTF-8")
      val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=ru&format=json"

      val geoReq = Request.Builder().url(geoUrl).get().build()
      val geoBody = httpClient.newCall(geoReq).execute().use { response ->
        if (!response.isSuccessful) error("Ошибка геокодинга: HTTP ${response.code}")
        response.body?.string() ?: error("Пустой ответ геокодинга")
      }

      val geoJson = JSONObject(geoBody)
      val results = geoJson.optJSONArray("results")
      val (lat, lon, resolvedName, country) = if (results != null && results.length() > 0) {
        val first = results.getJSONObject(0)
        Quad(
          first.optDouble("latitude", 55.7558),
          first.optDouble("longitude", 37.6173),
          first.optString("name", cleanCity),
          first.optString("country", "Россия")
        )
      } else {
        Quad(55.7558, 37.6173, cleanCity, "Россия")
      }

      val forecastUrl =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
          "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
          "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

      val weatherReq = Request.Builder().url(forecastUrl).get().build()
      val weatherBody = httpClient.newCall(weatherReq).execute().use { response ->
        if (!response.isSuccessful) error("Ошибка прогноза погоды: HTTP ${response.code}")
        response.body?.string() ?: error("Пустой ответ метеосервиса")
      }

      val weatherJson = JSONObject(weatherBody)
      val current = weatherJson.optJSONObject("current") ?: JSONObject()
      val daily = weatherJson.optJSONObject("daily") ?: JSONObject()

      val temp = current.optDouble("temperature_2m", 18.0)
      val apparent = current.optDouble("apparent_temperature", temp)
      val humidity = current.optInt("relative_humidity_2m", 60)
      val wind = current.optDouble("wind_speed_10m", 10.0)
      val code = current.optInt("weather_code", 0)

      val maxArr = daily.optJSONArray("temperature_2m_max")
      val minArr = daily.optJSONArray("temperature_2m_min")
      val maxTemp = if (maxArr != null && maxArr.length() > 0) maxArr.optDouble(0, temp + 2) else temp + 2
      val minTemp = if (minArr != null && minArr.length() > 0) minArr.optDouble(0, temp - 3) else temp - 3

      WeatherForecast(
        cityName = resolvedName,
        country = country,
        temperatureC = temp,
        apparentTempC = apparent,
        windSpeedKmh = wind,
        humidityPercent = humidity,
        weatherCode = code,
        conditionText = mapWeatherCodeToRussian(code),
        dailyMaxC = maxTemp,
        dailyMinC = minTemp
      )
    }
  }

  private data class Quad(
    val lat: Double,
    val lon: Double,
    val name: String,
    val country: String,
  )

  companion object {
    fun mapWeatherCodeToRussian(code: Int): String = when (code) {
      0 -> "Ясное небо"
      1 -> "Преимущественно ясно"
      2 -> "Переменная облачность"
      3 -> "Пасмурно"
      45, 48 -> "Туман и дымка"
      51, 53, 55 -> "Морось"
      56, 57 -> "Ледяная морось"
      61, 63, 65 -> "Дождь"
      66, 67 -> "Ледяной дождь"
      71, 73, 75, 77 -> "Снегопад"
      80, 81, 82 -> "Ливневый дождь"
      85, 86 -> "Снежный ливень"
      95, 96, 99 -> "Гроза"
      else -> "Облачно с прояснениями"
    }
  }
}
