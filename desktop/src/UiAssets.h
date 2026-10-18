#pragma once
#include <QColor>
#include <QIcon>

class QLineEdit;
QIcon neoIcon(const QString &name, const QColor &color);
void addClearButton(QLineEdit *field);
