#pragma once
#include "WeatherService.h"
#include <QMainWindow>
#include <QSettings>
#include <optional>

class QCloseEvent;
class QLineEdit;
class QProgressBar;
class QStackedWidget;
class QTabBar;
class QToolBar;
class QWebEngineProfile;
class QWebEngineView;

class BrowserWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit BrowserWindow(QWidget *parent = nullptr);
    ~BrowserWindow() override;
    QWebEngineView *newTab(bool incognito = false, const QUrl &url = {});
protected:
    void closeEvent(QCloseEvent *event) override;
private:
    QWebEngineView *activeView() const;
    QStackedWidget *activeStack() const;
    void navigate(const QString &input);
    void navigateHome();
    void closeTab(int index);
    void updateChrome();
    void showMenu();
    void showSettings();
    void applyTheme();
    void showLibrary(bool history);
    void bookmarkPage();
    void findInPage();
    void configureProfile(QWebEngineProfile *profile);
    void saveSession();
    QSettings settings;
    QTabBar *tabs;
    QStackedWidget *pages;
    QToolBar *toolbar;
    QLineEdit *address;
    QProgressBar *progress;
    QWebEngineProfile *regularProfile;
    QWebEngineProfile *privateProfile;
    WeatherService weather;
    std::optional<Weather> latestWeather;
    bool weatherFailed = false;
};
