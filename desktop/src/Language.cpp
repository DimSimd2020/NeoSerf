#include "Language.h"
#include <QCoreApplication>
#include <QLibraryInfo>
#include <QLocale>

Language::Language(QObject *parent) : QObject(parent) {}

QString Language::resolved(const QString &choice)
{
    if (choice == "ru" || choice == "en") return choice;
    return QLocale::system().language() == QLocale::Russian ? "ru" : "en";
}

void Language::apply(const QString &choice)
{
    QCoreApplication::removeTranslator(&appTranslation);
    QCoreApplication::removeTranslator(&qtTranslation);
    const auto language = resolved(choice);
    QLocale::setDefault(QLocale(language));
    if (language == "en" && appTranslation.load(":/i18n/neoserf_en.qm"))
        QCoreApplication::installTranslator(&appTranslation);
    if (qtTranslation.load("qtbase_" + language, QLibraryInfo::path(QLibraryInfo::TranslationsPath)))
        QCoreApplication::installTranslator(&qtTranslation);
}
