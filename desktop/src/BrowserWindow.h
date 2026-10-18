#pragma once
#include "Preferences.h"
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
class QToolButton;
class QWebEngineProfile;
class QWebEngineView;
class SettingsPage;

class BrowserWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit BrowserWindow(QWidget *parent = nullptr);
    ~BrowserWindow() override;
    QWebEngineView *newTab(bool incognito = false, const QUrl &url = {});
    // Opens Settings (or its History / Bookmarks section) in a tab, reusing an open one.
    void showSettings(const QString &section = "appearance");
protected:
    void closeEvent(QCloseEvent *event) override;
    void changeEvent(QEvent *event) override;
    bool eventFilter(QObject *watched, QEvent *event) override;
    bool nativeEvent(const QByteArray &eventType, void *message, qintptr *result) override;
private:
    QWebEngineView *activeView() const;
    QStackedWidget *activeStack() const;
    static SettingsPage *settingsPageIn(QStackedWidget *stack);
    void openSettingsIn(QStackedWidget *stack, const QString &section);
    void navigate(const QString &input);
    void navigateHome();
    void goBack();
    void closeTab(int index);
    void reopenClosedTab();
    void updateChrome();
    void updateInternalTab(QStackedWidget *stack);
    void showMenu();
    void applySettings();
    void applyTheme();
    void applyPreferences();
    void applyProfilePreferences(QWebEngineProfile *profile);
    void bookmarkPage();
    void findInPage();
    void configureProfile(QWebEngineProfile *profile);
    void saveSession();
    int hitTest(const QPoint &pos) const;
    QSettings settings;
    Preferences prefs;
    QString theme;
    bool weatherOn = true;
    QWidget *tabStrip;
    QTabBar *tabs;
    QStackedWidget *pages;
    QToolBar *toolbar;
    QLineEdit *address;
    QProgressBar *progress;
    QToolButton *maximizeButton;
    QWebEngineProfile *regularProfile;
    QWebEngineProfile *privateProfile;
    WeatherService weather;
    std::optional<Weather> latestWeather;
    bool weatherFailed = false;
    QList<QPair<QString, QString>> closedTabs; // title, url; most recent first
};
