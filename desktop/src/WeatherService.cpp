#include "WeatherService.h"
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

void WeatherService::get(const QUrl &url, std::function<void(const QJsonObject &)> callback)
{
    QNetworkRequest request(url);
    request.setTransferTimeout(12000);
    request.setRawHeader("User-Agent", "NeoSerf/1.1");
    auto *reply = network.get(request);
    const auto requestGeneration = generation;
    connect(reply, &QNetworkReply::finished, this, [this, reply, callback, requestGeneration] {
        reply->deleteLater();
        if (!enabled || requestGeneration != generation) return;
        if (reply->error() != QNetworkReply::NoError) { emit failed(); return; }
        QJsonParseError error;
        const auto doc = QJsonDocument::fromJson(reply->readAll(), &error);
        if (error.error != QJsonParseError::NoError || !doc.isObject()) { emit failed(); return; }
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
        QUrl url("https://api.open-meteo.com/v1/forecast");
        QUrlQuery query;
        query.addQueryItem("latitude", QString::number(lat.toDouble(), 'f', 5));
        query.addQueryItem("longitude", QString::number(lon.toDouble(), 'f', 5));
        query.addQueryItem("current", "temperature_2m,apparent_temperature,weather_code,is_day");
        query.addQueryItem("timezone", "auto");
        url.setQuery(query);
        get(url, [this, city](const QJsonObject &json) {
            Weather weather;
            weather.city = city;
            if (!parseForecast(json, weather)) { emit failed(); return; }
            emit updated(weather);
        });
    });
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
    switch (weather.code) {
    case 0: weather.condition = tr("Ясно"); break;
    case 1: weather.condition = tr("Преимущественно ясно"); break;
    case 2: weather.condition = tr("Переменная облачность"); break;
    case 3: weather.condition = tr("Пасмурно"); break;
    case 45: case 48: weather.condition = tr("Туман"); break;
    case 51: case 53: case 55: case 56: case 57: weather.condition = tr("Морось"); break;
    case 61: case 63: case 65: case 66: case 67: case 80: case 81: case 82: weather.condition = tr("Дождь"); break;
    case 71: case 73: case 75: case 77: case 85: case 86: weather.condition = tr("Снег"); break;
    case 95: case 96: case 99: weather.condition = tr("Гроза"); break;
    default: weather.condition = tr("Нет описания"); break;
    }
    return true;
}
