#pragma once
#include <QDialog>
#include <QHash>

class QLabel;
class QLineEdit;
class QComboBox;
class QSettings;
class QWebEngineProfile;

class SettingsDialog : public QDialog {
    Q_OBJECT
public:
    SettingsDialog(QSettings &settings, QWebEngineProfile *profile, QWidget *parent = nullptr);
    // Writes every field to QSettings. Not called after a reset, which clears the stored values instead.
    void save();
    bool wasReset() const { return reset; }
private:
    void clearBrowsingData();
    void resetSettings();
    QSettings &settings;
    QWebEngineProfile *profile;
    QHash<QString, QWidget *> fields; // settings key -> editor
    QComboBox *mailProvider = nullptr;
    QLineEdit *mailAddress = nullptr;
    QLabel *mailError = nullptr;
    bool reset = false;
};
