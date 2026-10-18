#include "AddressResolver.h"
#include "BrowserWindow.h"
#include "HomePage.h"
#include "WeatherService.h"
#include <QApplication>
#include <QDir>
#include <QJsonDocument>
#include <QLabel>
#include <QLineEdit>
#include <QMenu>
#include <QSettings>
#include <QSignalSpy>
#include <QStackedWidget>
#include <QStandardPaths>
#include <QTabWidget>
#include <QTest>
#include <QToolButton>
#include <QWebEnginePage>
#include <QWebEngineProfile>
#include <QWebEngineView>

class BrowserTest : public QObject {
    Q_OBJECT
private slots:
    void initTestCase()
    {
        QCoreApplication::setOrganizationName("NeoSerfTests");
        QCoreApplication::setApplicationName("NeoSerfTests");
        QStandardPaths::setTestModeEnabled(true);
        QSettings().clear();
        QSettings().setValue("weather/enabled", false);
        QApplication::setStyle("Fusion");
    }
    void cleanupTestCase() { QSettings().clear(); }

    void addressResolution()
    {
        QCOMPARE(AddressResolver::resolve(" example.com "), QUrl("https://example.com"));
        QCOMPARE(AddressResolver::resolve("http://localhost:8080/test"), QUrl("http://localhost:8080/test"));
        QCOMPARE(AddressResolver::resolve("localhost:8080"), QUrl("http://localhost:8080"));
        QVERIFY(AddressResolver::resolve("  ").isEmpty());
        QVERIFY(AddressResolver::resolve("C++ & Qt", "Google").toEncoded().contains("C%2B%2B%20%26%20Qt"));
        QVERIFY(AddressResolver::resolve(QString::fromUtf8("погода рядом"), QString::fromUtf8("Яндекс")).query().startsWith("text="));
        QCOMPARE(AddressResolver::resolve("javascript:alert(1)").host(), QString("www.google.com"));
    }

    void missingWeatherIsNotSynthesized()
    {
        Weather weather;
        QVERIFY(!WeatherService::parseForecast(QJsonObject{}, weather));
        const auto valid = QJsonDocument::fromJson(R"({"current":{"temperature_2m":7.3,"apparent_temperature":5.1,"weather_code":61,"is_day":0}})").object();
        QVERIFY(WeatherService::parseForecast(valid, weather));
        QCOMPARE(weather.temperature, 7.3);
        QCOMPARE(weather.condition, QString::fromUtf8("Дождь"));
        QVERIFY(!weather.isDay);
        auto incomplete = valid;
        auto current = incomplete.value("current").toObject();
        current.remove("temperature_2m");
        incomplete.insert("current", current);
        QVERIFY(!WeatherService::parseForecast(incomplete, weather));
    }

    void homeTabsMenuAndPageNavigation()
    {
        BrowserWindow window;
        window.show();
        QVERIFY(QTest::qWaitForWindowExposed(&window));
        auto *tabs = window.findChild<QTabWidget *>("browserTabs");
        QCOMPARE(tabs->count(), 1);
        auto *home = window.findChild<HomePage *>();
        auto *search = home->findChild<QLineEdit *>("homeSearch");
        QVERIFY(search->isVisible());
        QVERIFY(window.rect().contains(search->mapTo(&window, QPoint(0, 0))));

        // Render the actual native home and popup, with deterministic weather only in this test.
        const auto artifacts = qEnvironmentVariable("NEOSERF_TEST_ARTIFACTS");
        if (!artifacts.isEmpty()) {
            QDir().mkpath(artifacts);
            home->setWeatherEnabled(true);
            home->showWeather({QString::fromUtf8("Москва"), 8, 6, 3, true, QString::fromUtf8("Пасмурно")});
            QTest::qWait(120);
            QVERIFY(window.grab().save(artifacts + "/desktop-home.png"));
        }
        auto *button = window.findChild<QToolButton *>("menuButton");
        QTest::mouseClick(button, Qt::LeftButton);
        QTest::qWait(250);
        auto *menu = window.findChild<QMenu *>("overflowMenu");
        QVERIFY(menu && menu->isVisible());
        QVERIFY(menu->width() < window.width() / 2);
        QVERIFY(menu->height() < window.height());
        if (!artifacts.isEmpty()) QVERIFY(menu->grab().save(artifacts + "/desktop-menu.png"));
        QTest::keyClick(menu, Qt::Key_Escape);
        QCoreApplication::sendPostedEvents(nullptr, QEvent::DeferredDelete);

        QTest::keyClick(&window, Qt::Key_T, Qt::ControlModifier);
        QCOMPARE(tabs->count(), 2);
        QTest::keyClick(&window, Qt::Key_N, Qt::ControlModifier | Qt::ShiftModifier);
        QCOMPARE(tabs->count(), 3);
        auto *privateView = tabs->currentWidget()->findChild<QWebEngineView *>();
        QVERIFY(privateView->page()->profile()->isOffTheRecord());
        QSignalSpy privateProfileDestroyed(privateView->page()->profile(), &QObject::destroyed);
        QTest::keyClick(&window, Qt::Key_W, Qt::ControlModifier);
        QCOMPARE(privateProfileDestroyed.count(), 1);
        QCOMPARE(tabs->count(), 2);
        QTest::keyClick(&window, Qt::Key_L, Qt::ControlModifier);
        auto *address = window.findChild<QLineEdit *>("addressBar");
        QVERIFY(address->hasFocus());
        QTest::keyClicks(address, "https://example.com");
        QTest::keyClick(address, Qt::Key_Return);
        QCOMPARE(qobject_cast<QStackedWidget *>(tabs->currentWidget())->currentIndex(), 1);
        auto *view = tabs->currentWidget()->findChild<QWebEngineView *>();
        QSignalSpy loaded(view, &QWebEngineView::loadFinished);
        view->setHtml("<html><title>NeoSerf test</title><body><h1>Working browser</h1></body></html>", QUrl("https://example.com"));
        QVERIFY(loaded.wait(15000));
        QCOMPARE(view->title(), QString("NeoSerf test"));
        QTest::keyClick(&window, Qt::Key_D, Qt::ControlModifier);
        QCOMPARE(QSettings().value("bookmarks").toList().size(), 1);
        QTest::mouseClick(window.findChild<QToolButton *>("homeButton"), Qt::LeftButton);
        QCOMPARE(qobject_cast<QStackedWidget *>(tabs->currentWidget())->currentIndex(), 0);
        window.close();
    }

    void sessionExcludesPrivateTabs()
    {
        QSettings().remove("session/urls");
        BrowserWindow window;
        auto *view = window.newTab(false, QUrl("https://example.org/path"));
        Q_UNUSED(view);
        window.newTab(true, QUrl("https://private.example/secret"));
        window.close();
        const auto urls = QSettings().value("session/urls").toStringList();
        QVERIFY(urls.contains("https://example.org/path"));
        QVERIFY(!urls.contains("https://private.example/secret"));
    }
};

QTEST_MAIN(BrowserTest)
#include "BrowserTest.moc"
