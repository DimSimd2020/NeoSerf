#pragma once
#include <QColor>
#include <QIcon>
#include <QUrl>

class QDialogButtonBox;
class QLineEdit;
class QWidget;
class QSettings;
QIcon neoIcon(const QString &name, const QColor &color);
void addSubmitButton(QLineEdit *field); // Round send button inside the field, shown while there is text.
void applyUiTheme(QWidget *widget, const QString &mode);
bool validMailUrl(const QUrl &url);
QUrl mailUrl(const QSettings &settings);
// Standard Qt buttons take their text from Qt's own catalog; NeoSerf names them itself so they match the UI language.
void localizeButtons(QDialogButtonBox *box);
bool askYesNo(QWidget *parent, const QString &title, const QString &text);
void showInfo(QWidget *parent, const QString &title, const QString &text);
