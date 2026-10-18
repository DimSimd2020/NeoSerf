package com.example.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WeatherRepositoryTest {
  private val region = """{"success":true,"latitude":48.137,"longitude":11.575,"city":"Мюнхен","country":"Германия"}"""
  private val forecast = """{
    "current":{"temperature_2m":7.3,"apparent_temperature":5.1,"relative_humidity_2m":81,
      "wind_speed_10m":12.0,"weather_code":61,"is_day":0},
    "daily":{"temperature_2m_max":[10.0],"temperature_2m_min":[3.0]}
  }"""

  private fun repository(geo: String = region, weather: String = forecast, calls: MutableList<String> = mutableListOf()): WeatherRepository {
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      val request = chain.request()
      calls += request.url.toString()
      Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body((if (request.url.host == "ipwho.is") geo else weather).toResponseBody()).build()
    }.build()
    return WeatherRepository(httpClient = client)
  }

  @Test
  fun networkRegionDeterminesActualForecastCoordinates() = runBlocking {
    val calls = mutableListOf<String>()
    val result = repository(calls = calls).fetchWeather().getOrThrow()
    assertEquals("Мюнхен", result.cityName)
    assertEquals(7.3, result.temperatureC, 0.001)
    assertEquals("Дождь", result.conditionText)
    assertFalse(result.isDay)
    assertEquals("По сети", result.locationSource)
    assertEquals(2, calls.size)
    assertTrue(calls.last().contains("latitude=48.137&longitude=11.575"))
    assertFalse(calls.last().contains("55.7558"))
  }

  @Test
  fun unreachableOpenMeteoFallsBackToMetNorway() = runBlocking {
    val metNo = """{"properties":{"timeseries":[
      {"data":{"instant":{"details":{"air_temperature":2.0,"relative_humidity":90.4,"wind_speed":5.0}},
        "next_1_hours":{"summary":{"symbol_code":"lightrain_night"}}}},
      {"data":{"instant":{"details":{"air_temperature":4.5,"relative_humidity":88.0,"wind_speed":4.0}}}},
      {"data":{"instant":{"details":{"air_temperature":-1.0,"relative_humidity":95.0,"wind_speed":3.0}}}}
    ]}}"""
    val calls = mutableListOf<String>()
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      val request = chain.request()
      calls += request.url.toString()
      if (request.url.host == "api.open-meteo.com") throw java.net.SocketTimeoutException("timeout")
      Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body((if (request.url.host == "ipwho.is") region else metNo).toResponseBody()).build()
    }.build()
    val repository = WeatherRepository(httpClient = client)
    val result = repository.fetchWeather().getOrThrow()
    assertEquals("Мюнхен", result.cityName)
    assertEquals(2.0, result.temperatureC, 0.001)
    assertTrue(result.apparentTempC < 2.0)
    assertEquals(90, result.humidityPercent)
    assertEquals(4.5, result.dailyMaxC, 0.001)
    assertEquals(-1.0, result.dailyMinC, 0.001)
    assertEquals("Дождь", result.conditionText)
    assertFalse(result.isDay)
    assertEquals("MET Norway", result.dataSource)
    assertTrue(calls.last().startsWith("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=48.1370&lon=11.5750"))
    // The next refresh goes straight to MET Norway.
    calls.clear()
    repository.fetchWeather().getOrThrow()
    assertTrue(calls.none { "open-meteo" in it })
  }

  @Test
  fun missingTemperatureIsAnErrorInsteadOfInventedWeather() = runBlocking {
    assertTrue(repository(weather = forecast.replace("\"temperature_2m\":7.3,", "")).fetchWeather().isFailure)
  }

  @Test
  fun unknownRegionNeverFallsBackToMoscow() = runBlocking {
    val calls = mutableListOf<String>()
    assertTrue(repository(geo = """{"success":false}""", calls = calls).fetchWeather().isFailure)
    assertEquals(1, calls.size)
  }

  @Test
  fun invalidCoordinatesDoNotProduceAWeatherRequest() = runBlocking {
    val calls = mutableListOf<String>()
    assertTrue(repository(geo = region.replace("48.137", "999"), calls = calls).fetchWeather().isFailure)
    assertEquals(1, calls.size)
  }
}
