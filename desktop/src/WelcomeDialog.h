#pragma once
#include <QDialog>
#include <QPointer>
class QAbstractAnimation;
class QButtonGroup;
class QComboBox;
class QGridLayout;
class QLabel;
class QPushButton;
class QStackedWidget;
class QToolButton;
class QEvent;
class ImportWidget;
class Language;

class WelcomeDialog : public QDialog {
    Q_OBJECT
public:
    explicit WelcomeDialog(Language &language, QWidget *parent = nullptr);
    static bool needed();
protected:
    void changeEvent(QEvent *event) override;
    void reject() override;
private:
    void retranslate();
    void showPage(int index);
    void updateNavigation();
    void finish();
    QToolButton *addChoice(QGridLayout *grid, QButtonGroup *group, const QString &value, int columns);
    QStackedWidget *pages;
    QLabel *title;
    QLabel *subtitle;
    QLabel *appearanceLabel;
    QLabel *languageLabel;
    QLabel *importLabel;
    QLabel *footnote;
    QLabel *servicesTitle;
    QLabel *servicesSubtitle;
    QLabel *engineLabel;
    QLabel *mailLabel;
    QLabel *servicesNote;
    QComboBox *theme;
    QComboBox *languages;
    QButtonGroup *engines;
    QButtonGroup *mails;
    QToolButton *yandexEngine;
    QToolButton *yandexMail;
    QList<QLabel *> dots;
    QPushButton *back;
    QPushButton *primary;
    ImportWidget *importer;
    QPointer<QAbstractAnimation> transition;
};
