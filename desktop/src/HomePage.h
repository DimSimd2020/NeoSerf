#pragma once
#include "WeatherService.h"
#include <QWidget>
class QLabel;
class QLineEdit;
class QPushButton;

class HomePage : public QWidget {
    Q_OBJECT
public:
    explicit HomePage(bool incognito, QWidget *parent = nullptr);
    void setWeatherEnabled(bool enabled);
    void showWeather(const Weather &weather);
    void showWeatherError();
    void showWeatherLoading();
signals:
    void submitted(const QString &input);
    void refreshWeather();
private:
    QWidget *weatherBox;
    QLabel *weatherTitle;
    QLabel *weatherDetail;
    QLabel *weatherSource;
    QPushButton *retry;
};
