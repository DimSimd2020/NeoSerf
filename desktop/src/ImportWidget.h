#pragma once
#include <QWidget>
class QButtonGroup;
class QGridLayout;
class QLabel;
class QPushButton;
class QToolButton;

class ImportWidget : public QWidget {
    Q_OBJECT
public:
    explicit ImportWidget(QWidget *parent = nullptr);
    bool isBusy() const { return busy; }
    void retranslate();
signals:
    void busyChanged(bool busy);
private:
    QToolButton *addSource(const QString &browser, const QString &profile, const QString &path);
    void updateState();
    void importSelected();
    QButtonGroup *sources;
    QGridLayout *sourceGrid;
    QToolButton *choose;
    QPushButton *bookmarks;
    QPushButton *history;
    QPushButton *start;
    QLabel *description;
    QLabel *sourceHeading;
    QLabel *dataHeading;
    QLabel *empty;
    QLabel *result;
    bool busy = false;
};
