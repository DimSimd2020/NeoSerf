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
    QString source = "Open-Meteo";
};

class WeatherService : public QObject {
    Q_OBJECT
public:
    explicit WeatherService(QObject *parent = nullptr);
    void setEnabled(bool enabled);
    void refresh();
    static bool parseForecast(const QJsonObject &json, Weather &weather);
    // MET Norway forecast, used when Open-Meteo is unreachable (it is blocked on some networks).
    static bool parseMetNo(const QJsonObject &json, Weather &weather);
signals:
    void updated(const Weather &weather);
    void failed();
    void loading();
private:
    void get(const QUrl &url, std::function<void(const QJsonObject &)> callback, std::function<void()> onError = {});
    void loadForecast(double latitude, double longitude, const QString &city);
    void loadMetNo(double latitude, double longitude, const QString &city);
    QNetworkAccessManager network;
    QTimer timer;
    bool enabled = false;
    bool openMeteoBlocked = false;
    quint64 generation = 0;
};
