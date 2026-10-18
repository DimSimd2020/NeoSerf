#pragma once
#include <QObject>
#include <QTranslator>

class Language : public QObject {
    Q_OBJECT
public:
    explicit Language(QObject *parent = nullptr);
    void apply(const QString &choice);
    static QString resolved(const QString &choice);
private:
    QTranslator appTranslation;
    QTranslator qtTranslation;
};
