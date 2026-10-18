#include "AddressResolver.h"
#include "BrowserImport.h"
#include "BrowserWindow.h"
#include "HomePage.h"
#include "Language.h"
#include "Preferences.h"
#include "WelcomeDialog.h"
#include "WeatherService.h"
#include <QAction>
#include <QApplication>
#include <QCheckBox>
#include <QDialogButtonBox>
#include <QPushButton>
#include <QSqlDatabase>
#include <QSqlQuery>
#include <QTemporaryDir>
#include <QWebEngineSettings>
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
            // The field has only the send arrow: no clear button.
            QCOMPARE(search->actions().size(), 1);
            search->setText("arrow");
            auto *submit = search->actions().first();
            QVERIFY(submit->isVisible() && !submit->icon().isNull());
            search->clear();
            QVERIFY(!submit->isVisible());
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

    void submitArrowSendsTheAddressAndHomeSearch()
    {
        BrowserWindow window;
        window.show();
        QVERIFY(QTest::qWaitForWindowExposed(&window));
        auto *pages = window.findChild<QStackedWidget *>("browserPages");
        auto *address = window.findChild<QLineEdit *>("addressBar");
        auto *submit = address->findChild<QAction *>("submitAction");
        QVERIFY(submit && !submit->isVisible() && !submit->icon().isNull());
        address->setText("example.com");
        QVERIFY(submit->isVisible());
        QCOMPARE(address->actions().last(), submit);
        submit->trigger();
        QCOMPARE(qobject_cast<QStackedWidget *>(pages->currentWidget())->currentIndex(), 1);
        QTest::mouseClick(window.findChild<QToolButton *>("homeButton"), Qt::LeftButton);
        auto *search = pages->currentWidget()->findChild<QLineEdit *>("homeSearch");
        QVERIFY(search->actions().last()->objectName() == "submitAction" && !search->actions().last()->isVisible());
        search->setText(QString::fromUtf8("погода"));
        QVERIFY(search->actions().last()->isVisible());
        const auto artifacts = qEnvironmentVariable("NEOSERF_TEST_ARTIFACTS");
        if (!artifacts.isEmpty()) {
            address->setText("https://example.com");
            QTest::qWait(60);
            QVERIFY(window.grab().save(artifacts + "/desktop-submit-arrow.png"));
        }
        search->actions().last()->trigger();
        QCOMPARE(qobject_cast<QStackedWidget *>(pages->currentWidget())->currentIndex(), 1);
        window.close();
    }

    void mailButtonOpensTheConfiguredService()
    {
        QSettings().remove("mail/url");
        QSettings().remove("session/urls");
        BrowserWindow window;
        window.resize(1000, 720);
        window.show();
        QVERIFY(QTest::qWaitForWindowExposed(&window));
        auto *tabs = window.findChild<QTabBar *>("browserTabs");
        auto *pages = window.findChild<QStackedWidget *>("browserPages");
        auto *address = window.findChild<QLineEdit *>("addressBar");
        auto openMail = [&] {
            auto *stack = qobject_cast<QStackedWidget *>(pages->currentWidget());
            stack->setCurrentIndex(0);
            auto *mail = stack->findChild<QToolButton *>("mailButton");
            QVERIFY(mail && mail->isVisible());
            // On the home page, top-right, below the navigation bar rather than in the tab strip.
            const auto corner = mail->mapTo(&window, QPoint());
            QVERIFY(corner.x() > window.width() * 3 / 4);
            QVERIFY(corner.y() > address->mapTo(&window, QPoint(0, address->height())).y());
            QVERIFY(corner.y() < window.height() / 4);
            const int before = tabs->count();
            QTest::mouseClick(mail, Qt::LeftButton);
            QCOMPARE(tabs->count(), before);
            QCOMPARE(stack->currentIndex(), 1);
        };
        openMail();
        QCOMPARE(pages->currentWidget()->property("sessionUrl").toString(), QString("https://mail.google.com/"));
        QSettings settings;
        settings.setValue("mail/url", "https://mail.yandex.ru/");
        settings.sync();
        openMail();
        QCOMPARE(pages->currentWidget()->property("sessionUrl").toString(), QString("https://mail.yandex.ru/"));
        settings.setValue("mail/url", "http://insecure.example/");
        settings.sync();
        openMail();
        QCOMPARE(pages->currentWidget()->property("sessionUrl").toString(), QString("https://mail.google.com/"));
        QVERIFY(!window.findChild<QWidget *>("tabStrip")->findChild<QToolButton *>("mailButton"));
        settings.remove("mail/url");
        window.close();
    }

    void settingsApplyBrowserPreferences()
    {
        BrowserWindow window;
        window.resize(1180, 800);
        window.show();
        QVERIFY(QTest::qWaitForWindowExposed(&window));
        window.activateWindow();
        QVERIFY(QTest::qWaitForWindowActive(&window));
        QTest::mouseClick(window.findChild<QToolButton *>("menuButton"), Qt::LeftButton);
        auto *menu = window.findChild<QMenu *>("overflowMenu");
        QVERIFY(menu);
        QAction *settingsAction = nullptr;
        for (auto *action : menu->actions()) if (action->text() == QString::fromUtf8("Настройки")) settingsAction = action;
        QVERIFY(settingsAction);
        bool edited = false;
        QTimer::singleShot(0, &window, [&] {
            auto *dialog = window.findChild<QDialog *>();
            QVERIFY(dialog);
            QVERIFY(dialog->findChildren<QComboBox *>().size() >= 10 && dialog->findChildren<QCheckBox *>().size() >= 10);
            // An insecure custom mail address must keep the dialog open.
            auto *provider = dialog->findChild<QComboBox *>("mailProvider");
            provider->setCurrentIndex(provider->findData("custom"));
            dialog->findChild<QLineEdit *>("mailAddress")->setText("http://insecure.example/");
            auto *save = dialog->findChild<QDialogButtonBox *>()->button(QDialogButtonBox::Save);
            // Standard buttons follow the NeoSerf language, not Qt's own catalog.
            QCOMPARE(save->text(), QString::fromUtf8("Сохранить"));
            QCOMPARE(dialog->findChild<QDialogButtonBox *>()->button(QDialogButtonBox::Cancel)->text(), QString::fromUtf8("Отмена"));
            save->click();
            QVERIFY(dialog->isVisible());
            dialog->findChild<QLineEdit *>("mailAddress")->setText("https://mail.example.org/");
            dialog->findChild<QCheckBox *>("site_javascript")->setChecked(false);
            dialog->findChild<QCheckBox *>("ui_showHome")->setChecked(false);
            dialog->findChild<QCheckBox *>("privacy_blockThirdPartyCookies")->setChecked(true);
            auto *camera = dialog->findChild<QComboBox *>("permissions_camera");
            camera->setCurrentIndex(camera->findData("block"));
            auto *zoom = dialog->findChild<QComboBox *>("page_zoom");
            zoom->setCurrentIndex(zoom->findData(150));
            auto *startup = dialog->findChild<QComboBox *>("startup_mode");
            startup->setCurrentIndex(startup->findData("home"));
            edited = true;
            const auto artifacts = qEnvironmentVariable("NEOSERF_TEST_ARTIFACTS");
            if (!artifacts.isEmpty()) QVERIFY(dialog->grab().save(artifacts + "/desktop-settings.png"));
            save->click();
        });
        menu->hide();
        settingsAction->trigger();
        QVERIFY(edited);
        QCoreApplication::sendPostedEvents(nullptr, QEvent::DeferredDelete);
        QSettings stored;
        QCOMPARE(stored.value("mail/url").toString(), QString("https://mail.example.org/"));
        QCOMPARE(stored.value("site/javascript").toBool(), false);
        QCOMPARE(stored.value("startup/mode").toString(), QString("home"));
        QVERIFY(!window.findChild<QToolButton *>("homeButton")->isVisible());
        auto *view = window.findChild<QWebEngineView *>();
        QVERIFY(!view->page()->profile()->settings()->testAttribute(QWebEngineSettings::JavascriptEnabled));
        QCOMPARE(view->zoomFactor(), 1.5);
        QCOMPARE(window.newTab()->zoomFactor(), 1.5);
        const auto prefs = Preferences::load(stored);
        QCOMPARE(prefs.camera, QString("block"));
        QVERIFY(prefs.blockThirdPartyCookies && !prefs.javascript && prefs.zoomPercent == 150);
        for (const auto &key : Preferences::keys()) stored.remove(key);
        stored.setValue("weather/enabled", false);
        stored.setValue("appearance/theme", "dark");
        window.close();
    }

    void preferencesFallBackToSafeDefaults()
    {
        QTemporaryDir dir;
        QVERIFY(dir.isValid());
        QSettings ini(dir.filePath("prefs.ini"), QSettings::IniFormat);
        const auto defaults = Preferences::load(ini);
        QCOMPARE(defaults.startup, QString("restore"));
        QVERIFY(defaults.javascript && defaults.images && !defaults.popups && !defaults.blockThirdPartyCookies);
        QCOMPARE(defaults.zoomPercent, 100);
        QVERIFY(!defaults.downloadFolder.isEmpty());
        ini.setValue("page/zoom", 9000);
        ini.setValue("permissions/camera", "allow-everything");
        ini.setValue("startup/mode", "somewhere");
        const auto clamped = Preferences::load(ini);
        QCOMPARE(clamped.zoomPercent, 500);
        QCOMPARE(clamped.camera, QString("ask"));
        QCOMPARE(clamped.startup, QString("restore"));
    }

    void welcomeScreenSavesChoicesOnce()
    {
        QSettings settings;
        settings.remove("onboarding/completed");
        settings.remove("mail/url");
        settings.remove("search/engine");
        QVERIFY(WelcomeDialog::needed());
        Language language;
        language.apply("ru");
        WelcomeDialog dialog(language);
        dialog.show();
        QVERIFY(QTest::qWaitForWindowExposed(&dialog));
        auto *pages = dialog.findChild<QStackedWidget *>("welcomePages");
        auto *theme = dialog.findChild<QComboBox *>("themeSelector");
        auto *languages = dialog.findChild<QComboBox *>("languageSelector");
        theme->setCurrentIndex(theme->findData("light"));
        QCOMPARE(dialog.property("darkTheme").toBool(), false);
        languages->setCurrentIndex(languages->findData("en"));
        auto *primary = dialog.findChild<QPushButton *>("welcomeFinish");
        auto *back = dialog.findChild<QPushButton *>("welcomeBack");
        QCOMPARE(primary->text(), QString("Next"));
        QVERIFY(!back->isVisible());
        auto *importStart = dialog.findChild<QPushButton *>("importStart");
        QVERIFY(importStart && !importStart->isEnabled()); // Nothing is imported until a source is picked.
        const auto artifacts = qEnvironmentVariable("NEOSERF_TEST_ARTIFACTS");
        if (!artifacts.isEmpty()) QVERIFY(dialog.grab().save(artifacts + "/desktop-welcome.png"));

        // Page 2 slides in; Back slides to page 1 and Next returns.
        primary->click();
        QCOMPARE(pages->currentIndex(), 1);
        QTest::qWait(150);
        if (!artifacts.isEmpty()) QVERIFY(dialog.grab().save(artifacts + "/desktop-welcome-transition.png"));
        QTRY_COMPARE(pages->currentWidget()->pos(), QPoint(0, 0));
        QTRY_VERIFY(!pages->currentWidget()->graphicsEffect());
        QCOMPARE(primary->text(), QString("Start browsing"));
        QVERIFY(back->isVisible());
        QCOMPARE(dialog.findChildren<QToolButton *>("engineChoice").size(), 5);
        QCOMPARE(dialog.findChildren<QToolButton *>("mailChoice").size(), 4);
        for (auto *card : dialog.findChildren<QToolButton *>("engineChoice"))
            if (card->property("choice") == "DuckDuckGo") card->click();
        for (auto *card : dialog.findChildren<QToolButton *>("mailChoice"))
            if (card->property("choice") == "https://e.mail.ru/") card->click();
        if (!artifacts.isEmpty()) QVERIFY(dialog.grab().save(artifacts + "/desktop-welcome-services.png"));
        back->click();
        QCOMPARE(pages->currentIndex(), 0);
        QTRY_VERIFY(!pages->currentWidget()->graphicsEffect());
        primary->click();
        QTRY_VERIFY(!pages->currentWidget()->graphicsEffect());
        QCOMPARE(pages->currentIndex(), 1);
        primary->click();
        QCOMPARE(dialog.result(), int(QDialog::Accepted));
        QCOMPARE(settings.value("appearance/theme").toString(), QString("light"));
        QCOMPARE(settings.value("appearance/language").toString(), QString("en"));
        QCOMPARE(settings.value("search/engine").toString(), QString("DuckDuckGo"));
        QCOMPARE(settings.value("mail/url").toString(), QString("https://e.mail.ru/"));
        QVERIFY(!WelcomeDialog::needed());
        language.apply("ru");
        settings.setValue("appearance/theme", "dark");
        settings.remove("appearance/language");
        settings.remove("mail/url");
        settings.remove("search/engine");
    }

    void importsBookmarksAndHistoryWithoutDuplicates()
    {
        QTemporaryDir root;
        QVERIFY(root.isValid());
        const auto profile = root.path() + "/Google/Chrome/User Data/Default";
        QVERIFY(QDir().mkpath(profile));
        QFile bookmarks(profile + "/Bookmarks");
        QVERIFY(bookmarks.open(QIODevice::WriteOnly));
        // Single quotes keep the fixture readable; they are swapped for JSON quotes below.
        QByteArray json = "{'roots':{'bookmark_bar':{'type':'folder','children':["
            "{'type':'url','name':'A','url':'https://a.example/'},"
            "{'type':'folder','children':[{'type':'url','name':'B','url':'https://b.example/'},"
            "{'type':'url','name':'Script','url':'javascript:alert(1)'}]}]},'other':{'type':'folder','children':[]}}}";
        bookmarks.write(json.replace('\'', '"'));
        bookmarks.close();
        const auto connection = QString("import-fixture");
        {
            auto db = QSqlDatabase::addDatabase("QSQLITE", connection);
            db.setDatabaseName(profile + "/History");
            QVERIFY(db.open());
            QSqlQuery query(db);
            QVERIFY(query.exec("CREATE TABLE urls(id INTEGER PRIMARY KEY, url TEXT, title TEXT, hidden INTEGER DEFAULT 0, last_visit_time INTEGER)"));
            QVERIFY(query.exec("INSERT INTO urls(url, title, hidden, last_visit_time) VALUES "
                "('https://a.example/','A',0,3),('https://hidden.example/','Hidden',1,2),('file:///c:/secret','File',0,1),('https://c.example/','C',0,5)"));
            // Like a running browser: keep the database open and locked while importing.
            QVERIFY(query.exec("PRAGMA locking_mode=EXCLUSIVE"));
            QVERIFY(query.exec("BEGIN EXCLUSIVE"));
            const auto profiles = BrowserImport::discover(root.path(), root.path() + "/none");
            QCOMPARE(profiles.size(), 1);
            QVERIFY(profiles.first().name.startsWith("Chrome"));
            const auto data = BrowserImport::read(profile, true, true);
            QVERIFY2(data.errors.isEmpty(), qPrintable(data.errors.join("; ")));
            QCOMPARE(data.bookmarks.size(), 2);
            QCOMPARE(data.history.size(), 2);
            QSettings target(root.filePath("target.ini"), QSettings::IniFormat);
            QCOMPARE(BrowserImport::save(data, target), qMakePair(2, 2));
            QCOMPARE(BrowserImport::save(data, target), qMakePair(0, 0));
            QCOMPARE(target.value("bookmarks").toList().size(), 2);
            QCOMPARE(target.value("history").toList().size(), 2);
            query.exec("ROLLBACK");
        }
        QSqlDatabase::removeDatabase(connection);
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
