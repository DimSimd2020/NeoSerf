#include "WeatherService.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QUrlQuery>
#include <cmath>

WeatherService::WeatherService(QObject *parent) : QObject(parent), network(this)
{
    timer.setInterval(30 * 60 * 1000);
    connect(&timer, &QTimer::timeout, this, &WeatherService::refresh);
}

void WeatherService::setEnabled(bool value)
{
    enabled = value;
    ++generation;
    if (enabled) { timer.start(); refresh(); }
    else timer.stop();
}

void WeatherService::get(const QUrl &url, std::function<void(const QJsonObject &)> callback, std::function<void()> onError)
{
    QNetworkRequest request(url);
    request.setTransferTimeout(7000);
    // MET Norway requires an identifying User-Agent.
    request.setRawHeader("User-Agent", "NeoSerf/1.3 github.com/DimSimd2020/NeoSerf");
    auto *reply = network.get(request);
    const auto requestGeneration = generation;
    connect(reply, &QNetworkReply::finished, this, [this, reply, callback, onError, requestGeneration] {
        reply->deleteLater();
        if (!enabled || requestGeneration != generation) return;
        auto fail = [this, onError] { if (onError) onError(); else emit failed(); };
        if (reply->error() != QNetworkReply::NoError) { fail(); return; }
        QJsonParseError error;
        const auto doc = QJsonDocument::fromJson(reply->readAll(), &error);
        if (error.error != QJsonParseError::NoError || !doc.isObject()) { fail(); return; }
        callback(doc.object());
    });
}

void WeatherService::refresh()
{
    if (!enabled) return;
    ++generation;
    emit loading();
    get(QUrl("https://ipwho.is/?lang=ru&fields=success,latitude,longitude,city,region"), [this](const QJsonObject &geo) {
        const auto lat = geo.value("latitude");
        const auto lon = geo.value("longitude");
        if (!geo.value("success").toBool() || !lat.isDouble() || !lon.isDouble()
            || std::abs(lat.toDouble()) > 90 || std::abs(lon.toDouble()) > 180) { emit failed(); return; }
        auto city = geo.value("city").toString();
        if (city.isEmpty()) city = geo.value("region").toString();
        if (city.isEmpty()) city = tr("Ваш регион");
        if (openMeteoBlocked) loadMetNo(lat.toDouble(), lon.toDouble(), city);
        else loadForecast(lat.toDouble(), lon.toDouble(), city);
    });
}

void WeatherService::loadForecast(double latitude, double longitude, const QString &city)
{
    QUrl url("https://api.open-meteo.com/v1/forecast");
    QUrlQuery query;
    query.addQueryItem("latitude", QString::number(latitude, 'f', 5));
    query.addQueryItem("longitude", QString::number(longitude, 'f', 5));
    query.addQueryItem("current", "temperature_2m,apparent_temperature,weather_code,is_day");
    query.addQueryItem("timezone", "auto");
    url.setQuery(query);
    get(url, [this, city](const QJsonObject &json) {
        Weather weather;
        weather.city = city;
        if (!parseForecast(json, weather)) { emit failed(); return; }
        emit updated(weather);
    }, [this, latitude, longitude, city] {
        // Remember the failure so later refreshes go straight to the fallback.
        openMeteoBlocked = true;
        loadMetNo(latitude, longitude, city);
    });
}

void WeatherService::loadMetNo(double latitude, double longitude, const QString &city)
{
    QUrl url("https://api.met.no/weatherapi/locationforecast/2.0/compact");
    QUrlQuery query;
    // MET Norway asks for at most four decimals.
    query.addQueryItem("lat", QString::number(latitude, 'f', 4));
    query.addQueryItem("lon", QString::number(longitude, 'f', 4));
    url.setQuery(query);
    get(url, [this, city](const QJsonObject &json) {
        Weather weather;
        weather.city = city;
        if (!parseMetNo(json, weather)) { emit failed(); return; }
        emit updated(weather);
    });
}

static QString describe(int code)
{
    switch (code) {
    case 0: return WeatherService::tr("Ясно");
    case 1: return WeatherService::tr("Преимущественно ясно");
    case 2: return WeatherService::tr("Переменная облачность");
    case 3: return WeatherService::tr("Пасмурно");
    case 45: case 48: return WeatherService::tr("Туман");
    case 51: case 53: case 55: case 56: case 57: return WeatherService::tr("Морось");
    case 61: case 63: case 65: case 66: case 67: case 80: case 81: case 82: return WeatherService::tr("Дождь");
    case 71: case 73: case 75: case 77: case 85: case 86: return WeatherService::tr("Снег");
    case 95: case 96: case 99: return WeatherService::tr("Гроза");
    default: return WeatherService::tr("Нет описания");
    }
}

bool WeatherService::parseForecast(const QJsonObject &json, Weather &weather)
{
    const auto current = json.value("current").toObject();
    for (const auto &key : {"temperature_2m", "apparent_temperature", "weather_code", "is_day"}) {
        if (!current.value(key).isDouble() || !std::isfinite(current.value(key).toDouble())) return false;
    }
    weather.temperature = current.value("temperature_2m").toDouble();
    weather.apparent = current.value("apparent_temperature").toDouble();
    weather.code = current.value("weather_code").toInt();
    weather.isDay = current.value("is_day").toInt() == 1;
    weather.condition = describe(weather.code);
    weather.source = "Open-Meteo";
    return true;
}

bool WeatherService::parseMetNo(const QJsonObject &json, Weather &weather)
{
    const auto series = json.value("properties").toObject().value("timeseries").toArray();
    if (series.isEmpty()) return false;
    const auto data = series.first().toObject().value("data").toObject();
    const auto details = data.value("instant").toObject().value("details").toObject();
    const auto temperature = details.value("air_temperature");
    if (!temperature.isDouble() || !std::isfinite(temperature.toDouble())) return false;
    auto symbol = data.value("next_1_hours").toObject().value("summary").toObject().value("symbol_code").toString();
    if (symbol.isEmpty()) symbol = data.value("next_6_hours").toObject().value("summary").toObject().value("symbol_code").toString();
    if (symbol.isEmpty()) return false;
    weather.temperature = temperature.toDouble();
    // Wind chill (Environment Canada formula) below 10 °C with wind; otherwise the air temperature.
    const double wind = details.value("wind_speed").toDouble() * 3.6;
    const double t = weather.temperature;
    weather.apparent = t <= 10 && wind > 4.8
        ? 13.12 + 0.6215 * t - 11.37 * std::pow(wind, 0.16) + 0.3965 * t * std::pow(wind, 0.16) : t;
    weather.isDay = !symbol.endsWith("_night");
    const auto kind = symbol.section('_', 0, 0);
    // Map MET symbols onto the WMO codes used by Open-Meteo, so both sources read the same.
    if (kind.contains("thunder")) weather.code = 95;
    else if (kind.contains("snow")) weather.code = 73;
    else if (kind.contains("sleet") || kind.contains("rain")) weather.code = kind.startsWith("light") ? 61 : 63;
    else if (kind == "fog") weather.code = 45;
    else if (kind == "clearsky") weather.code = 0;
    else if (kind == "fair") weather.code = 1;
    else if (kind == "partlycloudy") weather.code = 2;
    else if (kind == "cloudy") weather.code = 3;
    else weather.code = -1;
    weather.condition = describe(weather.code);
    weather.source = "MET Norway";
    return true;
}
