#include "HomePage.h"
#include <QAction>
#include <QLabel>
#include <QLineEdit>
#include <QPushButton>
#include <QSvgRenderer>
#include <QVBoxLayout>
#include <QPainter>
#include <cmath>

HomePage::HomePage(bool incognito, QWidget *parent) : QWidget(parent)
{
    setObjectName("homePage");
    auto *layout = new QVBoxLayout(this);
    layout->setContentsMargins(24, 32, 24, 60);
    layout->addStretch();
    auto *center = new QWidget(this);
    center->setMaximumWidth(520);
    center->setMinimumWidth(480);
    auto *content = new QVBoxLayout(center);
    content->setContentsMargins(0, 0, 0, 0);
    content->setSpacing(0);
    auto *logo = new QLabel(center);
    QPixmap pixmap(128, 128);
    pixmap.fill(Qt::transparent);
    QPainter painter(&pixmap);
    QSvgRenderer renderer(QStringLiteral(":/assets/logo.svg"));
    renderer.render(&painter);
    painter.end();
    pixmap.setDevicePixelRatio(2);
    logo->setPixmap(pixmap);
    logo->setAlignment(Qt::AlignCenter);
    content->addWidget(logo);
    content->addSpacing(14);
    auto *brand = new QLabel("NeoSerf", center);
    brand->setObjectName("brand");
    brand->setAlignment(Qt::AlignCenter);
    content->addWidget(brand);
    if (incognito) {
        auto *label = new QLabel(tr("Инкогнито"), center);
        label->setAlignment(Qt::AlignCenter);
        label->setStyleSheet("color: #a491ff; padding-top: 8px;");
        content->addWidget(label);
    }
    content->addSpacing(32);
    auto *search = new QLineEdit(center);
    search->setObjectName("homeSearch");
    search->setPlaceholderText(tr("Поиск или адрес сайта"));
    search->setAccessibleName(tr("Поиск или адрес сайта"));
    search->setClearButtonEnabled(true);
    connect(search, &QLineEdit::returnPressed, this, [this, search] {
        if (!search->text().trimmed().isEmpty()) emit submitted(search->text());
    });
    content->addWidget(search);
    content->addSpacing(28);
    weatherBox = new QWidget(center);
    auto *weatherLayout = new QVBoxLayout(weatherBox);
    weatherLayout->setContentsMargins(0, 0, 0, 0);
    weatherLayout->setSpacing(6);
    weatherTitle = new QLabel(tr("Определяем погоду…"), weatherBox);
    weatherTitle->setObjectName("weather");
    weatherDetail = new QLabel(weatherBox);
    weatherDetail->setObjectName("weatherDetail");
    weatherSource = new QLabel(weatherBox);
    weatherSource->setObjectName("weatherSource");
    for (auto *label : {weatherTitle, weatherDetail, weatherSource}) {
        label->setAlignment(Qt::AlignCenter);
        label->setWordWrap(true);
        weatherLayout->addWidget(label);
    }
    retry = new QPushButton(tr("Повторить"), weatherBox);
    retry->setStyleSheet("color: #459fff;");
    retry->hide();
    weatherLayout->addWidget(retry, 0, Qt::AlignCenter);
    connect(retry, &QPushButton::clicked, this, &HomePage::refreshWeather);
    content->addWidget(weatherBox);
    layout->addWidget(center, 0, Qt::AlignHCenter);
    layout->addStretch();
}

void HomePage::setWeatherEnabled(bool enabled) { weatherBox->setVisible(enabled); }
void HomePage::showWeather(const Weather &weather)
{
    weatherTitle->setText(tr("%1°   %2").arg(qRound(weather.temperature)).arg(weather.city));
    weatherDetail->setText(tr("%1 · ощущается как %2°").arg(weather.condition).arg(qRound(weather.apparent)));
    weatherSource->setText(tr("По сети · Open-Meteo"));
    retry->hide();
}
void HomePage::showWeatherError()
{
    weatherTitle->setText(tr("Погода недоступна"));
    weatherDetail->setText(tr("Проверьте подключение к сети"));
    weatherSource->clear();
    retry->show();
}
void HomePage::showWeatherLoading()
{
    weatherTitle->setText(tr("Определяем погоду…"));
    weatherDetail->clear();
    weatherSource->clear();
    retry->hide();
}
