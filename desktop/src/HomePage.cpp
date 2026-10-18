#include "HomePage.h"
#include "UiAssets.h"
#include <QAction>
#include <QLabel>
#include <QLineEdit>
#include <QHBoxLayout>
#include <QPushButton>
#include <QToolButton>
#include <QSvgRenderer>
#include <QVBoxLayout>
#include <QPainter>
#include <cmath>

HomePage::HomePage(bool incognito, QWidget *parent) : QWidget(parent)
{
    setObjectName("homePage");
    auto *layout = new QVBoxLayout(this);
    layout->setContentsMargins(24, 12, 20, 60);
    // Mail link in the top-right corner of the page, under the navigation bar.
    auto *top = new QHBoxLayout;
    top->addStretch();
    mail = new QToolButton(this);
    mail->setObjectName("mailButton");
    mail->setText(tr("Почта"));
    mail->setToolButtonStyle(Qt::ToolButtonTextBesideIcon);
    mail->setProperty("neoIcon", "mail");
    mail->setIcon(neoIcon("mail", palette().color(QPalette::Text)));
    mail->setIconSize(QSize(18, 18));
    mail->setAccessibleName(tr("Почта"));
    mail->setCursor(Qt::PointingHandCursor);
    connect(mail, &QToolButton::clicked, this, &HomePage::mailRequested);
    top->addWidget(mail);
    layout->addLayout(top);
    layout->addStretch();
    auto *center = new QWidget(this);
    center->setMaximumWidth(600);
    center->setMinimumWidth(460);
    auto *content = new QVBoxLayout(center);
    content->setContentsMargins(0, 0, 0, 0);
    content->setSpacing(0);
    auto *logo = new QLabel(center);
    logo->setObjectName("homeLogo");
    QPixmap pixmap(224, 224);
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
        label->setObjectName("incognitoHint");
        content->addWidget(label);
    }
    content->addSpacing(32);
    auto *search = new QLineEdit(center);
    search->setObjectName("homeSearch");
    search->setPlaceholderText(tr("Поиск или адрес сайта"));
    search->setAccessibleName(tr("Поиск или адрес сайта"));
    addSubmitButton(search);
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
    retry->setObjectName("actionLink");
    retry->hide();
    weatherLayout->addWidget(retry, 0, Qt::AlignCenter);
    connect(retry, &QPushButton::clicked, this, &HomePage::refreshWeather);
    content->addWidget(weatherBox);
    layout->addWidget(center, 0, Qt::AlignHCenter);
    layout->addStretch();
}

void HomePage::setWeatherEnabled(bool enabled) { weatherBox->setVisible(enabled); }
void HomePage::setMailVisible(bool visible) { mail->setVisible(visible); }
void HomePage::showWeather(const Weather &weather)
{
    weatherTitle->setText(tr("%1°   %2").arg(qRound(weather.temperature)).arg(weather.city));
    weatherDetail->setText(tr("%1 · ощущается как %2°").arg(weather.condition).arg(qRound(weather.apparent)));
    weatherSource->setText(tr("По сети · %1").arg(weather.source));
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
