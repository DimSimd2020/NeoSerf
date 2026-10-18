#pragma once
#include <QHash>
#include <QWidget>

class QButtonGroup;
class QComboBox;
class QLabel;
class QLineEdit;
class QListWidget;
class QSettings;
class QStackedWidget;
class QToolButton;
class QVBoxLayout;
class QWebEngineProfile;

// Settings, history and bookmarks, shown inside a browser tab like chrome://settings.
// Every change is stored at once; changed() tells the window to apply it.
class SettingsPage : public QWidget {
    Q_OBJECT
public:
    SettingsPage(QSettings &settings, QWebEngineProfile *profile, QWidget *parent = nullptr);
    void showSection(const QString &id, bool animated = true);
    QString currentSection() const;
    QString sectionTitle() const;
signals:
    void changed();
    void resetDone(); // stored values were cleared; the window rebuilds this page
    void openUrl(const QUrl &url);
    void sectionChanged(const QString &id);
protected:
    void showEvent(QShowEvent *event) override;
    void resizeEvent(QResizeEvent *event) override;
private:
    QVBoxLayout *addSection(const QString &id, const QString &title, const QString &icon, bool scrollable = true);
    QVBoxLayout *addCard(QVBoxLayout *section, const QString &heading = {});
    void addRow(QVBoxLayout *card, QWidget *row);
    QComboBox *addChoice(QVBoxLayout *card, const QString &key, const QString &label,
        const QList<QPair<QString, QVariant>> &items, const QVariant &value);
    void addToggle(QVBoxLayout *card, const QString &key, const QString &text, bool value);
    void store(const QString &key, const QVariant &value);
    void storeMail();
    void fillLibrary(const QString &key);
    void moveIndicator(bool animated);
    QSettings &settings;
    QWebEngineProfile *profile;
    QStackedWidget *sections;
    QVBoxLayout *navLayout;
    QButtonGroup *navigation;
    QWidget *indicator;
    QStringList ids;
    QStringList titles;
    QComboBox *mailProvider = nullptr;
    QLineEdit *mailAddress = nullptr;
    QLabel *mailError = nullptr;
    QHash<QString, QListWidget *> libraries;   // "history" / "bookmarks" -> list
    QHash<QString, QLineEdit *> librarySearch;
};
