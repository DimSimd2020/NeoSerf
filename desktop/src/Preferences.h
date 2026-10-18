#pragma once
#include <QString>
#include <QStringList>

class QSettings;

// Browser behavior chosen in Settings. Keys and defaults live here so the dialog and the window agree.
struct Preferences {
    QString startup = "restore"; // "restore" the last session or open the "home" page
    bool showHomeButton = true;
    bool showMailButton = true;
    bool keepCookies = true;
    bool blockThirdPartyCookies = false;
    bool clearHistoryOnExit = false;
    bool javascript = true;
    bool images = true;
    bool popups = false;
    bool autoplay = false;
    bool smoothScroll = true;
    bool forceDark = false;
    QString geolocation = "ask"; // "ask" or "block"
    QString camera = "ask";
    QString microphone = "ask";
    int zoomPercent = 100;
    int fontSize = 16;
    int minimumFontSize = 0;
    bool askDownloadLocation = true;
    QString downloadFolder;

    static Preferences load(const QSettings &settings);
    static QStringList keys();
};
