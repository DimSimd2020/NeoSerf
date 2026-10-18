#include "ImportWidget.h"
#include "BrowserImport.h"
#include "UiAssets.h"
#include <QButtonGroup>
#include <QDir>
#include <QFileDialog>
#include <QFutureWatcher>
#include <QGridLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QSettings>
#include <QToolButton>
#include <QVBoxLayout>
#include <QtConcurrentRun>

ImportWidget::ImportWidget(QWidget *parent) : QWidget(parent)
{
    setObjectName("importPanel");
    auto *layout = new QVBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(10);
    description = new QLabel(this);
    description->setWordWrap(true);
    description->setObjectName("welcomeDetail");
    layout->addWidget(description);
    layout->addSpacing(4);

    sourceHeading = new QLabel(this);
    sourceHeading->setObjectName("importHeading");
    layout->addWidget(sourceHeading);
    sources = new QButtonGroup(this);
    sources->setExclusive(true);
    sourceGrid = new QGridLayout;
    sourceGrid->setSpacing(8);
    layout->addLayout(sourceGrid);
    for (const auto &profile : BrowserImport::discover(qEnvironmentVariable("LOCALAPPDATA"), qEnvironmentVariable("APPDATA"))) {
        const auto parts = profile.name.split(QString::fromUtf8(" · "));
        addSource(parts.value(0), parts.value(1), profile.path);
    }
    empty = new QLabel(this);
    empty->setObjectName("welcomeDetail");
    empty->setWordWrap(true);
    empty->setVisible(sources->buttons().isEmpty());
    // The most likely source is ready to go, so "Transfer" works right away.
    if (!sources->buttons().isEmpty()) sources->buttons().first()->setChecked(true);
    layout->addWidget(empty);
    choose = new QToolButton(this);
    choose->setObjectName("chooseProfile");
    choose->setProperty("neoIcon", "plus");
    choose->setIcon(neoIcon("plus", palette().color(QPalette::Text)));
    choose->setIconSize(QSize(18, 18));
    choose->setToolButtonStyle(Qt::ToolButtonTextBesideIcon);
    layout->addWidget(choose, 0, Qt::AlignLeft);
    layout->addSpacing(6);

    dataHeading = new QLabel(this);
    dataHeading->setObjectName("importHeading");
    layout->addWidget(dataHeading);
    auto *row = new QHBoxLayout;
    row->setSpacing(8);
    bookmarks = new QPushButton(this);
    bookmarks->setObjectName("importBookmarks");
    history = new QPushButton(this);
    history->setObjectName("importHistory");
    for (auto *chip : {bookmarks, history}) {
        chip->setProperty("chip", true);
        chip->setCheckable(true);
        chip->setChecked(true);
        chip->setCursor(Qt::PointingHandCursor);
        row->addWidget(chip);
        connect(chip, &QPushButton::toggled, this, &ImportWidget::updateState);
    }
    row->addStretch();
    start = new QPushButton(this);
    start->setObjectName("importStart");
    start->setCursor(Qt::PointingHandCursor);
    row->addWidget(start);
    layout->addLayout(row);
    result = new QLabel(this);
    result->setObjectName("importResult");
    result->setWordWrap(true);
    result->setTextInteractionFlags(Qt::TextSelectableByMouse);
    result->setAccessibleName(tr("Результат импорта"));
    result->hide();
    layout->addWidget(result);

    connect(sources, &QButtonGroup::buttonToggled, this, &ImportWidget::updateState);
    connect(choose, &QToolButton::clicked, this, [this] {
        const auto path = QFileDialog::getExistingDirectory(this, tr("Папка профиля с файлами Bookmarks и History"));
        if (path.isEmpty()) return;
        addSource(QDir(path).dirName(), QDir::toNativeSeparators(path), path)->setChecked(true);
        empty->hide();
    });
    connect(start, &QPushButton::clicked, this, &ImportWidget::importSelected);
    retranslate();
    updateState();
}

QToolButton *ImportWidget::addSource(const QString &browser, const QString &profile, const QString &path)
{
    auto *card = new QToolButton(this);
    card->setObjectName("importSource");
    card->setCheckable(true);
    card->setText(profile.isEmpty() ? browser : browser + "\n" + profile);
    card->setToolTip(QDir::toNativeSeparators(path));
    card->setProperty("profilePath", path);
    card->setProperty("neoIcon", "globe");
    card->setIcon(neoIcon("globe", palette().color(QPalette::Text)));
    card->setIconSize(QSize(22, 22));
    card->setToolButtonStyle(Qt::ToolButtonTextBesideIcon);
    card->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
    card->setCursor(Qt::PointingHandCursor);
    const int index = sources->buttons().size();
    sources->addButton(card);
    sourceGrid->addWidget(card, index / 2, index % 2);
    return card;
}

void ImportWidget::updateState()
{
    start->setEnabled(!busy && sources->checkedButton() && (bookmarks->isChecked() || history->isChecked()));
}

void ImportWidget::retranslate()
{
    description->setText(tr("Закладки и до 500 страниц истории из Chrome, Opera или Яндекс Браузера. Папки закладок объединятся в общий список."));
    sourceHeading->setText(tr("Откуда"));
    dataHeading->setText(tr("Что перенести"));
    empty->setText(tr("Установленные браузеры не найдены. Укажите папку профиля вручную."));
    choose->setText(tr("Другая папка…"));
    choose->setToolTip(tr("Если профиль не найден автоматически, укажите его папку вручную."));
    bookmarks->setText(tr("Закладки"));
    history->setText(tr("История"));
    start->setText(tr("Перенести"));
    // Rounded chips need room for their padding, or the text gets clipped.
    for (auto *chip : {bookmarks, history})
        chip->setMinimumWidth(chip->fontMetrics().horizontalAdvance(chip->text()) + 44);
}

void ImportWidget::importSelected()
{
    busy = true;
    emit busyChanged(true);
    for (auto *widget : std::initializer_list<QWidget *>{choose, bookmarks, history})
        widget->setEnabled(false);
    for (auto *card : sources->buttons()) card->setEnabled(false);
    updateState();
    result->setText(tr("Переносим данные…"));
    result->show();
    const auto path = sources->checkedButton()->property("profilePath").toString();
    const bool withBookmarks = bookmarks->isChecked(), withHistory = history->isChecked();
    auto *watcher = new QFutureWatcher<ImportResult>(this);
    connect(watcher, &QFutureWatcher<ImportResult>::finished, this, [this, watcher] {
        const auto data = watcher->result();
        QSettings settings;
        const auto counts = BrowserImport::save(data, settings);
        QString message = tr("Добавлено закладок: %1. Страниц истории: %2. Повторы пропущены.").arg(counts.first).arg(counts.second);
        if (!data.errors.isEmpty()) message += "\n" + data.errors.join("\n");
        if (settings.status() != QSettings::NoError) message = tr("Не удалось сохранить данные. Проверьте доступ к настройкам NeoSerf.");
        result->setText(message);
        for (auto *widget : std::initializer_list<QWidget *>{choose, bookmarks, history})
            widget->setEnabled(true);
        for (auto *card : sources->buttons()) card->setEnabled(true);
        busy = false;
        updateState();
        emit busyChanged(false);
        watcher->deleteLater();
    });
    watcher->setFuture(QtConcurrent::run([path, withBookmarks, withHistory] { return BrowserImport::read(path, withBookmarks, withHistory); }));
}
