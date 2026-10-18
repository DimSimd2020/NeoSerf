#pragma once
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QObject>
#include <QTimer>
#include <functional>

struct Weather {
    QString city;
    double temperature = 0;
    double apparent = 0;
    int code = 0;
    bool isDay = true;
    QString condition;
};

class WeatherService : public QObject {
    Q_OBJECT
public:
    explicit WeatherService(QObject *parent = nullptr);
    void setEnabled(bool enabled);
    void refresh();
    static bool parseForecast(const QJsonObject &json, Weather &weather);
signals:
    void updated(const Weather &weather);
    void failed();
    void loading();
private:
    void get(const QUrl &url, std::function<void(const QJsonObject &)> callback);
    QNetworkAccessManager network;
    QTimer timer;
    bool enabled = false;
    quint64 generation = 0;
};
