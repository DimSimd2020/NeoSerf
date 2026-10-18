#include "AddressResolver.h"
#include "BrowserWindow.h"
#include "HomePage.h"
#include "WeatherService.h"
#include <QApplication>
#include <QDir>
#include <QDialog>
#include <QComboBox>
#include <QJsonDocument>
#include <QLabel>
#include <QLineEdit>
#include <QMenu>
#include <QSettings>
#include <QScopeGuard>
#include <QStyleHints>
#include <QSignalSpy>
#include <QStackedWidget>
#include <QStandardPaths>
#include <QTabBar>
#include <QTimer>
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
        QSettings().setValue("appearance/theme", "dark");
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
        auto *tabs = window.findChild<QTabBar *>("browserTabs");
        auto *pages = window.findChild<QStackedWidget *>("browserPages");
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
        auto *privateView = pages->currentWidget()->findChild<QWebEngineView *>();
        QVERIFY(privateView->page()->profile()->isOffTheRecord());
        QSignalSpy privateProfileDestroyed(privateView->page()->profile(), &QObject::destroyed);
        auto *close = qobject_cast<QToolButton *>(tabs->tabButton(tabs->currentIndex(), QTabBar::RightSide));
        QVERIFY(close && !close->icon().isNull());
        QTest::mouseClick(close, Qt::LeftButton);
        QCOMPARE(privateProfileDestroyed.count(), 1);
        QCOMPARE(tabs->count(), 2);
        QTest::keyClick(&window, Qt::Key_L, Qt::ControlModifier);
        auto *address = window.findChild<QLineEdit *>("addressBar");
        QVERIFY(address->hasFocus());
        QTest::keyClicks(address, "https://example.com");
        QTest::keyClick(address, Qt::Key_Return);
        QCOMPARE(qobject_cast<QStackedWidget *>(pages->currentWidget())->currentIndex(), 1);
        auto *view = pages->currentWidget()->findChild<QWebEngineView *>();
        QSignalSpy loaded(view, &QWebEngineView::loadFinished);
        view->setHtml("<html><title>NeoSerf test</title><body><h1>Working browser</h1></body></html>", QUrl("https://example.com"));
        QVERIFY(loaded.wait(15000));
        QCOMPARE(view->title(), QString("NeoSerf test"));
        QTest::keyClick(&window, Qt::Key_D, Qt::ControlModifier);
        QCOMPARE(QSettings().value("bookmarks").toList().size(), 1);
        QTest::mouseClick(window.findChild<QToolButton *>("homeButton"), Qt::LeftButton);
        QCOMPARE(qobject_cast<QStackedWidget *>(pages->currentWidget())->currentIndex(), 0);
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

    void tabStripStaysAboveAddressAndPlusFollowsTabs()
    {
        QSettings().remove("session/urls");
        BrowserWindow window;
        window.resize(1000, 720);
        window.show();
        QVERIFY(QTest::qWaitForWindowExposed(&window));
        auto *tabs = window.findChild<QTabBar *>("browserTabs");
        auto *pages = window.findChild<QStackedWidget *>("browserPages");
        auto *plus = window.findChild<QToolButton *>("newTabButton");
        auto *address = window.findChild<QLineEdit *>("addressBar");
        auto checkPlacement = [&] {
            const int tabBottom = tabs->mapTo(&window, tabs->rect().bottomLeft()).y();
            QVERIFY(tabBottom < address->mapTo(&window, QPoint()).y());
            const int nextTo = tabs->mapTo(&window, tabs->tabRect(tabs->count() - 1).topRight()).x();
            const int plusX = plus->mapTo(&window, QPoint()).x();
            QVERIFY2(plusX >= nextTo && plusX - nextTo <= 12,
                qPrintable(QString("plus=%1 last=%2 barWidth=%3 count=%4").arg(plusX).arg(nextTo).arg(tabs->width()).arg(tabs->count())));
            QVERIFY(window.rect().contains(plus->mapTo(&window, plus->rect().bottomRight())));
            QVERIFY(tabs->tabRect(0).width() <= 190);
        };
        QTest::qWait(50);
        checkPlacement();
        QTest::mouseClick(plus, Qt::LeftButton);
        QTest::qWait(50);
        QCOMPARE(tabs->count(), 2);
        checkPlacement();
        auto *selected = pages->currentWidget();
        auto *other = pages->widget(0);
        selected->setProperty("sessionUrl", "https://second.example");
        other->setProperty("sessionUrl", "https://first.example");
        tabs->moveTab(1, 0);
        QCOMPARE(pages->widget(0), selected);
        QCOMPARE(pages->widget(1), other);
        QCOMPARE(pages->currentWidget(), selected);
        QTest::mouseClick(tabs, Qt::LeftButton, Qt::NoModifier, tabs->tabRect(1).center());
        QCOMPARE(pages->currentWidget(), other);
        auto *close = qobject_cast<QToolButton *>(tabs->tabButton(1, QTabBar::RightSide));
        QTest::mouseClick(close, Qt::LeftButton);
        QCOMPARE(tabs->count(), 1);
        QCOMPARE(pages->currentWidget(), selected);
        QTest::qWait(50);
        checkPlacement();
        for (int i = 0; i < 12; ++i) window.newTab();
        window.resize(640, 520);
        QTest::qWait(50);
        int readableTabs = 0;
        for (int i = 0; i < tabs->count(); ++i) {
            if (tabs->tabRect(i).intersected(tabs->rect()).width() >= 80) ++readableTabs;
        }
        QVERIFY(readableTabs >= 3);
        QVERIFY(plus->isVisible());
        QVERIFY(window.rect().contains(plus->mapTo(&window, plus->rect().bottomRight())));
        QVERIFY(plus->mapTo(&window, QPoint()).x() >= tabs->mapTo(&window, tabs->rect().topRight()).x());
        const auto artifacts = qEnvironmentVariable("NEOSERF_TEST_ARTIFACTS");
        if (!artifacts.isEmpty()) QVERIFY(window.grab().save(artifacts + "/desktop-many-tabs.png"));
        window.close();
    }

    void themeSettingsPersistAndKeepControlsReadable()
    {
        BrowserWindow window;
        window.resize(1180, 800);
        window.show();
        QVERIFY(QTest::qWaitForWindowExposed(&window));
        window.activateWindow();
        QVERIFY(QTest::qWaitForWindowActive(&window));
        for (const auto &mode : {QString("light"), QString("dark")}) {
            QTest::mouseClick(window.findChild<QToolButton *>("menuButton"), Qt::LeftButton);
            auto *menu = window.findChild<QMenu *>("overflowMenu");
            QVERIFY(menu);
            QAction *settingsAction = nullptr;
            for (auto *action : menu->actions()) if (action->text() == QString::fromUtf8("Настройки")) settingsAction = action;
            QVERIFY(settingsAction);
            bool selected = false;
            QTimer::singleShot(0, &window, [&] {
                auto *dialog = window.findChild<QDialog *>();
                auto *combo = dialog ? dialog->findChild<QComboBox *>("themeSelector") : nullptr;
                if (combo) {
                    combo->setCurrentIndex(combo->findData(mode));
                    selected = true;
                    dialog->accept();
                } else if (dialog) dialog->reject();
            });
            menu->hide();
            settingsAction->trigger();
            QVERIFY(selected);
            QCoreApplication::sendPostedEvents(nullptr, QEvent::DeferredDelete);
            QCOMPARE(QSettings().value("appearance/theme").toString(), mode);
            QCOMPARE(window.property("darkTheme").toBool(), mode == "dark");
            const auto colors = window.palette();
            QVERIFY(qAbs(colors.color(QPalette::Window).lightness() - colors.color(QPalette::Text).lightness()) > 180);
            for (auto *button : window.findChildren<QToolButton *>()) {
                if (button->property("neoIcon").isValid()) QVERIFY(!button->icon().isNull());
            }
            auto *search = window.findChild<QLineEdit *>("homeSearch");
            search->setText("clear me");
            auto *clear = search->actions().first();
            QVERIFY(clear->isVisible() && !clear->icon().isNull());
            clear->trigger();
            QVERIFY(search->text().isEmpty() && !clear->isVisible());
            const auto artifacts = qEnvironmentVariable("NEOSERF_TEST_ARTIFACTS");
            if (!artifacts.isEmpty()) {
                window.activateWindow();
                QVERIFY(QTest::qWaitForWindowActive(&window));
                QTest::qWait(50);
                QVERIFY(window.grab().save(artifacts + "/desktop-home-" + mode + ".png"));
                QTest::mouseClick(window.findChild<QToolButton *>("menuButton"), Qt::LeftButton);
                QTest::qWait(220);
                auto *popup = qobject_cast<QMenu *>(QApplication::activePopupWidget());
                QVERIFY(popup && popup->grab().save(artifacts + "/desktop-menu-" + mode + ".png"));
                popup->hide();
            }
            QCoreApplication::sendPostedEvents(nullptr, QEvent::DeferredDelete);
        }
        window.close();
    }

    void systemThemeChangesApplyToAnOpenWindow()
    {
        QSettings().setValue("appearance/theme", "system");
        auto *hints = QGuiApplication::styleHints();
        const auto restore = qScopeGuard([hints] { hints->unsetColorScheme(); });
        hints->setColorScheme(Qt::ColorScheme::Dark);
        if (hints->colorScheme() != Qt::ColorScheme::Dark) QSKIP("Platform does not expose a mutable system color scheme");
        BrowserWindow window;
        QCOMPARE(window.property("darkTheme").toBool(), true);
        hints->setColorScheme(Qt::ColorScheme::Light);
        QTRY_COMPARE(window.property("darkTheme").toBool(), false);
        hints->setColorScheme(Qt::ColorScheme::Dark);
        QTRY_COMPARE(window.property("darkTheme").toBool(), true);
    }
};

QTEST_MAIN(BrowserTest)
#include "BrowserTest.moc"
