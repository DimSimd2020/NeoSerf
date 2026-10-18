#include "Preferences.h"
#include <QSettings>
#include <QStandardPaths>

Preferences Preferences::load(const QSettings &s)
{
    Preferences p;
    p.startup = s.value("startup/mode", p.startup).toString() == "home" ? "home" : "restore";
    p.showHomeButton = s.value("ui/showHome", p.showHomeButton).toBool();
    p.showMailButton = s.value("ui/showMail", p.showMailButton).toBool();
    p.keepCookies = s.value("privacy/keepCookies", p.keepCookies).toBool();
    p.blockThirdPartyCookies = s.value("privacy/blockThirdPartyCookies", p.blockThirdPartyCookies).toBool();
    p.clearHistoryOnExit = s.value("privacy/clearHistoryOnExit", p.clearHistoryOnExit).toBool();
    p.javascript = s.value("site/javascript", p.javascript).toBool();
    p.images = s.value("site/images", p.images).toBool();
    p.popups = s.value("site/popups", p.popups).toBool();
    p.autoplay = s.value("site/autoplay", p.autoplay).toBool();
    p.smoothScroll = s.value("site/smoothScroll", p.smoothScroll).toBool();
    p.forceDark = s.value("site/forceDark", p.forceDark).toBool();
    auto permission = [&s](const char *key) { return s.value(key, "ask").toString() == "block" ? QString("block") : QString("ask"); };
    p.geolocation = permission("permissions/geolocation");
    p.camera = permission("permissions/camera");
    p.microphone = permission("permissions/microphone");
    p.zoomPercent = qBound(25, s.value("page/zoom", p.zoomPercent).toInt(), 500);
    p.fontSize = qBound(9, s.value("page/fontSize", p.fontSize).toInt(), 48);
    p.minimumFontSize = qBound(0, s.value("page/minFontSize", p.minimumFontSize).toInt(), 24);
    p.askDownloadLocation = s.value("downloads/ask", p.askDownloadLocation).toBool();
    p.downloadFolder = s.value("downloads/folder").toString();
    if (p.downloadFolder.isEmpty()) p.downloadFolder = QStandardPaths::writableLocation(QStandardPaths::DownloadLocation);
    return p;
}

QStringList Preferences::keys()
{
    return {"startup/mode", "ui/showHome", "ui/showMail", "privacy/keepCookies", "privacy/blockThirdPartyCookies",
            "privacy/clearHistoryOnExit", "site/javascript", "site/images", "site/popups", "site/autoplay",
            "site/smoothScroll", "site/forceDark", "permissions/geolocation", "permissions/camera",
            "permissions/microphone", "page/zoom", "page/fontSize", "page/minFontSize", "downloads/ask",
            "downloads/folder", "search/engine", "mail/url", "weather/enabled", "appearance/theme", "appearance/language"};
}
