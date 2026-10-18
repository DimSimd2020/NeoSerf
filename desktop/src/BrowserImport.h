#pragma once
#include <QString>
#include <QStringList>
#include <QVariant>
#include <QVector>

class QSettings;
struct BrowserProfile { QString name; QString path; };
struct ImportResult {
    QVariantList bookmarks;
    QVariantList history;
    QStringList errors;
};

namespace BrowserImport {
QVector<BrowserProfile> discover(const QString &localData, const QString &roamingData);
ImportResult read(const QString &profile, bool bookmarks, bool history);
QPair<int, int> save(const ImportResult &result, QSettings &settings);
}
