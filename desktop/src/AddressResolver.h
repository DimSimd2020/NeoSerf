#pragma once
#include <QUrl>
#include <QString>

namespace AddressResolver {
QUrl resolve(const QString &input, const QString &engine = "Google");
}
