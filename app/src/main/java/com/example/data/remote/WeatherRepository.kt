package com.example.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.data.model.WeatherForecast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class WeatherRepository(
  private val context: Context? = null,
  private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .callTimeout(12, TimeUnit.SECONDS)
    .build(),
) {
  private data class Region(val latitude: Double, val longitude: Double, val city: String, val country: String, val source: String)

  suspend fun fetchWeather(): Result<WeatherForecast> = withContext(Dispatchers.IO) {
    try {
      val location = deviceLocation()
      val region = if (location != null) {
        val address = try {
          @Suppress("DEPRECATION")
          context?.let { Geocoder(it, Locale("ru")).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }
        } catch (_: java.io.IOException) { null }
        Region(location.latitude, location.longitude,
          address?.locality ?: address?.subAdminArea ?: "Рядом с вами", address?.countryName.orEmpty(), "По местоположению")
      } else {
        val geo = requestJson("https://ipwho.is/?lang=ru&fields=success,latitude,longitude,city,region,country")
        check(geo.optBoolean("success")) { "Не удалось определить регион" }
        Region(geo.getDouble("latitude"), geo.getDouble("longitude"),
          geo.optString("city").ifBlank { geo.optString("region").ifBlank { "Ваш регион" } },
          geo.optString("country"), "По сети")
      }
      require(region.latitude in -90.0..90.0 && region.longitude in -180.0..180.0)
      val forecast = requestJson(
        "https://api.open-meteo.com/v1/forecast?latitude=${region.latitude}&longitude=${region.longitude}" +
          "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
          "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
      )
      Result.success(parseForecast(forecast, region.city, region.country, region.source))
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      Result.failure(error)
    }
  }

  private suspend fun deviceLocation(): Location? {
    val app = context ?: return null
    if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return try {
      val providers = manager.getProviders(true)
      val cached = providers.mapNotNull { manager.getLastKnownLocation(it) }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..TimeUnit.MINUTES.toNanos(30) }
        .maxByOrNull { it.elapsedRealtimeNanos }
      if (cached != null) return cached
      val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: return null
      withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { continuation ->
          val cancellation = android.os.CancellationSignal()
          continuation.invokeOnCancellation { cancellation.cancel() }
          LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(app)) {
            if (continuation.isActive) continuation.resume(it)
          }
        }
      }
    } catch (_: SecurityException) { null }
      catch (_: IllegalArgumentException) { null }
  }

  private fun requestJson(url: String): JSONObject {
    val request = Request.Builder().url(url).header("User-Agent", "NeoSerf/1.1").build()
    return httpClient.newCall(request).execute().use { response ->
      check(response.isSuccessful) { "Метеосервис недоступен: HTTP ${response.code}" }
      JSONObject(response.body?.string() ?: error("Пустой ответ метеосервиса"))
    }
  }

  companion object {
    internal fun parseForecast(json: JSONObject, city: String, country: String, source: String): WeatherForecast {
      val current = json.getJSONObject("current")
      val daily = json.getJSONObject("daily")
      val code = current.getInt("weather_code")
      val temperature = current.getDouble("temperature_2m")
      val apparent = current.getDouble("apparent_temperature")
      val wind = current.getDouble("wind_speed_10m")
      val humidity = current.getInt("relative_humidity_2m")
      val high = daily.getJSONArray("temperature_2m_max").getDouble(0)
      val low = daily.getJSONArray("temperature_2m_min").getDouble(0)
      require(listOf(temperature, apparent, wind, high, low).all { it.isFinite() } && humidity in 0..100)
      return WeatherForecast(
        cityName = city, country = country, temperatureC = temperature,
        apparentTempC = apparent, windSpeedKmh = wind, humidityPercent = humidity,
        weatherCode = code, conditionText = mapWeatherCodeToRussian(code),
        dailyMaxC = high, dailyMinC = low, isDay = current.getInt("is_day") == 1,
        locationSource = source
      )
    }

    fun mapWeatherCodeToRussian(code: Int): String = when (code) {
      0 -> "Ясно"
      1 -> "Преимущественно ясно"
      2 -> "Переменная облачность"
      3 -> "Пасмурно"
      45, 48 -> "Туман"
      51, 53, 55 -> "Морось"
      56, 57 -> "Ледяная морось"
      61, 63, 65 -> "Дождь"
      66, 67 -> "Ледяной дождь"
      71, 73, 75, 77 -> "Снег"
      80, 81, 82 -> "Ливень"
      85, 86 -> "Снежный ливень"
      95, 96, 99 -> "Гроза"
      else -> "Нет описания"
    }
  }
}
