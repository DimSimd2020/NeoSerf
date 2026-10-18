#include "BrowserImport.h"
#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QSet>
#include <QSettings>
#include <QSqlDatabase>
#include <QSqlQuery>
#include <QTemporaryDir>
#include <QUrl>
#include <QUuid>

static QString text(const char *source) { return QCoreApplication::translate("BrowserImport", source); }
static bool webUrl(const QString &value)
{
    const QUrl url(value);
    return url.isValid() && !url.host().isEmpty() && (url.scheme() == "https" || url.scheme() == "http");
}

QVector<BrowserProfile> BrowserImport::discover(const QString &localData, const QString &roamingData)
{
    const QVector<BrowserProfile> roots{
        {"Chrome", localData + "/Google/Chrome/User Data"},
        {QString::fromUtf8("Яндекс Браузер"), localData + "/Yandex/YandexBrowser/User Data"},
        {"Opera", roamingData + "/Opera Software/Opera Stable"},
        {"Opera GX", roamingData + "/Opera Software/Opera GX Stable"}
    };
    QVector<BrowserProfile> profiles;
    for (const auto &root : roots) {
        // "Local State" maps profile folders ("Profile 1") to the names people gave them.
        QFile state(root.path + "/Local State");
        QJsonObject names;
        if (state.open(QIODevice::ReadOnly))
            names = QJsonDocument::fromJson(state.readAll()).object().value("profile").toObject().value("info_cache").toObject();
        QStringList candidates{root.path}; // Older Opera stores files directly in this directory.
        for (const auto &dir : QDir(root.path).entryList({"Default", "Profile *"}, QDir::Dirs | QDir::NoDotAndDotDot))
            candidates.append(root.path + "/" + dir);
        for (const auto &path : candidates) {
            if (!QFile::exists(path + "/Bookmarks") && !QFile::exists(path + "/History")) continue;
            const auto folder = QDir(path).dirName();
            auto label = names.value(folder).toObject().value("name").toString().trimmed();
            if (label.isEmpty()) label = folder;
            profiles.append({root.name + " · " + label, path});
        }
    }
    return profiles;
}

static void readBookmarks(const QJsonObject &node, QVariantList &items)
{
    if (node.value("type") == "url") {
        const auto url = node.value("url").toString();
        if (webUrl(url)) items.append(QVariantMap{{"url", url}, {"title", node.value("name").toString()}});
    }
    for (const auto &child : node.value("children").toArray()) readBookmarks(child.toObject(), items);
}

ImportResult BrowserImport::read(const QString &profile, bool bookmarks, bool history)
{
    ImportResult result;
    if (bookmarks) {
        QFile file(profile + "/Bookmarks");
        if (!file.open(QIODevice::ReadOnly)) result.errors.append(text("Не удалось прочитать закладки."));
        else {
            QJsonParseError error;
            const auto doc = QJsonDocument::fromJson(file.readAll(), &error);
            if (error.error != QJsonParseError::NoError || !doc.object().value("roots").isObject())
                result.errors.append(text("Файл закладок повреждён или имеет другой формат."));
            else {
                const auto roots = doc.object().value("roots").toObject();
                for (const auto &root : roots) readBookmarks(root.toObject(), result.bookmarks);
            }
        }
    }
    if (history) {
        const auto connection = QUuid::createUuid().toString();
        // A running browser locks its History file, so read a private copy when it can be made.
        QTemporaryDir snapshot;
        QString historyPath = profile + "/History";
        bool copied = false;
        if (snapshot.isValid() && QFile::copy(historyPath, snapshot.filePath("History"))) {
            if (QFile::exists(historyPath + "-wal")) QFile::copy(historyPath + "-wal", snapshot.filePath("History-wal"));
            historyPath = snapshot.filePath("History");
            copied = true;
        }
        {
            auto db = QSqlDatabase::addDatabase("QSQLITE", connection);
            db.setConnectOptions(copied ? "QSQLITE_BUSY_TIMEOUT=1000" : "QSQLITE_OPEN_READONLY;QSQLITE_BUSY_TIMEOUT=1000");
            db.setDatabaseName(historyPath);
            if (!db.open()) result.errors.append(text("История недоступна. Закройте исходный браузер и повторите импорт."));
            else {
                QSqlQuery query(db);
                // NeoSerf retains the 500 most recent history entries, just like normal browsing.
                if (!query.exec("SELECT url, title FROM urls WHERE hidden = 0 ORDER BY last_visit_time DESC LIMIT 500"))
                    result.errors.append(text("Не удалось прочитать историю. Закройте исходный браузер и повторите импорт."));
                else while (query.next()) {
                    const auto url = query.value(0).toString();
                    if (webUrl(url)) result.history.append(QVariantMap{{"url", url}, {"title", query.value(1)}});
                }
            }
        }
        QSqlDatabase::removeDatabase(connection);
    }
    return result;
}

QPair<int, int> BrowserImport::save(const ImportResult &result, QSettings &settings)
{
    auto merge = [&settings](const char *key, const QVariantList &incoming, int limit) {
        auto stored = settings.value(key).toList();
        QSet<QString> urls;
        for (const auto &item : stored) urls.insert(item.toMap().value("url").toString());
        int added = 0;
        for (const auto &item : incoming) {
            const auto url = item.toMap().value("url").toString();
            if (urls.contains(url) || !webUrl(url) || (limit && stored.size() >= limit)) continue;
            stored.append(item);
            urls.insert(url);
            ++added;
        }
        settings.setValue(key, stored);
        return added;
    };
    const auto counts = qMakePair(merge("bookmarks", result.bookmarks, 0), merge("history", result.history, 500));
    settings.sync();
    return counts;
}
