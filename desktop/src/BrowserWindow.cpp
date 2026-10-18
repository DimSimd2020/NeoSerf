#include "BrowserWindow.h"
#include "AddressResolver.h"
#include "HomePage.h"
#include "UiAssets.h"
#include "SettingsDialog.h"
#include <QApplication>
#include <QStyleHints>
#include <QAction>
#include <QCheckBox>
#include <QCloseEvent>
#include <QComboBox>
#include <QDialog>
#include <QDialogButtonBox>
#include <QDir>
#include <QFile>
#include <QFileDialog>
#include <QFileInfo>
#include <QFormLayout>
#include <QHBoxLayout>
#include <QInputDialog>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QMenu>
#include <QPropertyAnimation>
#include <QProgressBar>
#include <QPushButton>
#include <QShortcut>
#include <QSignalBlocker>
#include <QStackedWidget>
#include <QStandardPaths>
#include <QStatusBar>
#include <QTabBar>
#include <QToolBar>
#include <QToolButton>
#include <QVBoxLayout>
#include <QWebEngineDownloadRequest>
#include <QWebEngineCookieStore>
#include <QWebEngineHistory>
#include <QWebEngineNewWindowRequest>
#include <QWebEnginePage>
#include <QWebEnginePermission>
#include <QWebEngineProfile>
#include <QWebEngineSettings>
#include <QWebEngineView>

class CompactTabBar : public QTabBar {
public:
    using QTabBar::QTabBar;
    QSize minimumSizeHint() const override
    {
        const auto minimum = QTabBar::minimumSizeHint();
        return QSize(qMin(minimum.width(), sizeHint().width()), minimum.height());
    }
protected:
    QSize tabSizeHint(int index) const override
    {
        const auto size = QTabBar::tabSizeHint(index);
        return QSize(qBound(152, size.width(), 176), 34);
    }
};

BrowserWindow::BrowserWindow(QWidget *parent) : QMainWindow(parent), weather(this)
{
    Q_INIT_RESOURCE(resources);
    setWindowTitle("NeoSerf");
    setWindowIcon(QIcon(":/assets/logo.svg"));
    resize(1180, 800);
    setMinimumSize(640, 480);
    applyTheme();
    connect(QGuiApplication::styleHints(), &QStyleHints::colorSchemeChanged, this, [this] {
        if (settings.value("appearance/theme", "system") == "system") applyTheme();
    });
    prefs = Preferences::load(settings);
    regularProfile = new QWebEngineProfile("NeoSerf", this);
    const auto storage = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    regularProfile->setPersistentStoragePath(storage + "/web");
    regularProfile->setCachePath(QStandardPaths::writableLocation(QStandardPaths::CacheLocation) + "/web");
    privateProfile = new QWebEngineProfile(this);
    configureProfile(regularProfile);
    configureProfile(privateProfile);

    toolbar = new QToolBar(tr("Навигация"), this);
    toolbar->setMovable(false);
    toolbar->setFloatable(false);
    toolbar->setObjectName("navigationBar");
    auto addButton = [this](const QString &text, const QString &tip, const QString &name, auto callback) {
        auto *button = new QToolButton(toolbar);
        Q_UNUSED(text);
        auto iconName = name;
        iconName.remove("Button");
        button->setProperty("neoIcon", iconName);
        button->setIcon(neoIcon(iconName, palette().color(QPalette::Text)));
        button->setIconSize(QSize(20, 20));
        button->setToolTip(tip);
        button->setAccessibleName(tip);
        button->setObjectName(name);
        button->setFixedSize(40, 40);
        connect(button, &QToolButton::clicked, this, callback);
        toolbar->addWidget(button);
        return button;
    };
    addButton(QString::fromUtf8("←"), tr("Назад · Alt+Left"), "backButton", [this] {
        if (activeView()->history()->canGoBack()) activeView()->back();
        else navigateHome();
    });
    addButton(QString::fromUtf8("→"), tr("Вперёд · Alt+Right"), "forwardButton", [this] { activeView()->forward(); });
    addButton(QString::fromUtf8("↻"), tr("Обновить · Ctrl+R"), "reloadButton", [this] {
        if (activeStack()->currentIndex() == 0) weather.refresh(); else activeView()->reload();
    });
    addButton(QString::fromUtf8("⌂"), tr("Главная · Alt+Home"), "homeButton", [this] { navigateHome(); });
    address = new QLineEdit(toolbar);
    address->setObjectName("addressBar");
    address->setPlaceholderText(tr("Поиск или адрес сайта"));
    address->setAccessibleName(tr("Адресная строка"));
    addSubmitButton(address);
    toolbar->addWidget(address);
    connect(address, &QLineEdit::returnPressed, this, [this] { navigate(address->text()); });
    addButton(QString::fromUtf8("☆"), tr("Сохранить в закладки · Ctrl+D"), "bookmarkButton", [this] { bookmarkPage(); });
    addButton(QString::fromUtf8("⋮"), tr("Меню"), "menuButton", [this] { showMenu(); });

    auto *central = new QWidget(this);
    auto *layout = new QVBoxLayout(central);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);
    auto *tabStrip = new QWidget(central);
    tabStrip->setObjectName("tabStrip");
    auto *tabLayout = new QHBoxLayout(tabStrip);
    tabLayout->setContentsMargins(12, 6, 12, 0);
    tabLayout->setSpacing(6);
    tabs = new CompactTabBar(tabStrip);
    tabs->setObjectName("browserTabs");
    tabs->setMovable(true);
    tabs->setExpanding(false);
    tabs->setDrawBase(false);
    tabs->setUsesScrollButtons(true);
    tabs->setElideMode(Qt::ElideRight);
    tabs->setSizePolicy(QSizePolicy::Maximum, QSizePolicy::Fixed);
    tabLayout->addWidget(tabs);
    auto *newButton = new QToolButton(tabStrip);
    newButton->setProperty("neoIcon", "plus");
    newButton->setIcon(neoIcon("plus", palette().color(QPalette::Text)));
    newButton->setIconSize(QSize(18, 18));
    newButton->setFixedSize(32, 32);
    newButton->setToolTip(tr("Новая вкладка · Ctrl+T"));
    newButton->setAccessibleName(tr("Новая вкладка"));
    newButton->setObjectName("newTabButton");
    tabLayout->addWidget(newButton);
    tabLayout->addStretch();
    connect(newButton, &QToolButton::clicked, this, [this] { newTab(); });
    layout->addWidget(tabStrip);
    layout->addWidget(toolbar);
    progress = new QProgressBar(central);
    progress->setTextVisible(false);
    progress->setFixedHeight(2);
    progress->hide();
    layout->addWidget(progress);
    pages = new QStackedWidget(central);
    pages->setObjectName("browserPages");
    connect(tabs, &QTabBar::currentChanged, this, [this](int index) {
        pages->setCurrentIndex(index);
        updateChrome();
    });
    connect(tabs, &QTabBar::tabMoved, this, [this](int from, int to) {
        const QSignalBlocker blocker(pages);
        auto *page = pages->widget(from);
        pages->removeWidget(page);
        pages->insertWidget(to, page);
        pages->setCurrentIndex(tabs->currentIndex());
        updateChrome();
    });
    layout->addWidget(pages, 1);
    setCentralWidget(central);
    statusBar()->setSizeGripEnabled(false);

    auto shortcut = [this](const QKeySequence &keys, auto callback) {
        auto *item = new QShortcut(keys, this);
        connect(item, &QShortcut::activated, this, callback);
    };
    shortcut(QKeySequence::AddTab, [this] { newTab(); });
    shortcut(QKeySequence("Ctrl+Shift+N"), [this] { newTab(true); });
    shortcut(QKeySequence("Ctrl+F4"), [this] { closeTab(tabs->currentIndex()); });
    shortcut(QKeySequence("Ctrl+W"), [this] { closeTab(tabs->currentIndex()); });
    shortcut(QKeySequence("Ctrl+L"), [this] { address->setFocus(); address->selectAll(); });
    shortcut(QKeySequence("Ctrl+R"), [this] { activeView()->reload(); });
    shortcut(QKeySequence("Alt+Left"), [this] { if (activeView()->history()->canGoBack()) activeView()->back(); else navigateHome(); });
    shortcut(QKeySequence("Alt+Right"), [this] { activeView()->forward(); });
    shortcut(QKeySequence("Alt+Home"), [this] { navigateHome(); });
    shortcut(QKeySequence("Ctrl+D"), [this] { bookmarkPage(); });
    shortcut(QKeySequence::Find, [this] { findInPage(); });
    shortcut(QKeySequence("Ctrl+Tab"), [this] { tabs->setCurrentIndex((tabs->currentIndex() + 1) % tabs->count()); });
    shortcut(QKeySequence::ZoomIn, [this] { activeView()->setZoomFactor(qMin(3.0, activeView()->zoomFactor() + 0.1)); });
    shortcut(QKeySequence::ZoomOut, [this] { activeView()->setZoomFactor(qMax(0.3, activeView()->zoomFactor() - 0.1)); });
    shortcut(QKeySequence("Ctrl+0"), [this] { activeView()->setZoomFactor(1); });

    connect(&weather, &WeatherService::updated, this, [this](const Weather &forecast) {
        latestWeather = forecast;
        weatherFailed = false;
        for (auto *home : findChildren<HomePage *>()) home->showWeather(forecast);
    });
    connect(&weather, &WeatherService::failed, this, [this] {
        weatherFailed = true;
        for (auto *home : findChildren<HomePage *>()) home->showWeatherError();
    });
    connect(&weather, &WeatherService::loading, this, [this] {
        for (auto *home : findChildren<HomePage *>()) home->showWeatherLoading();
    });
    restoreGeometry(settings.value("window/geometry").toByteArray());
    this->layout()->activate();
    layout->activate();
    const auto urls = prefs.startup == "home" ? QStringList{QString()}
        : settings.value("session/urls", QStringList{QString()}).toStringList();
    for (const auto &url : urls) newTab(false, QUrl(url));
    if (tabs->count() == 0) newTab();
    tabs->setCurrentIndex(qBound(0, settings.value("session/active", 0).toInt(), tabs->count() - 1));
    weather.setEnabled(settings.value("weather/enabled", true).toBool());
    applyPreferences();
}

BrowserWindow::~BrowserWindow()
{
    // Pages must be destroyed before the profiles they use.
    delete takeCentralWidget();
    delete regularProfile;
    delete privateProfile;
}

void BrowserWindow::applyTheme()
{
    applyUiTheme(this, settings.value("appearance/theme", "system").toString());
}

QStackedWidget *BrowserWindow::activeStack() const { return qobject_cast<QStackedWidget *>(pages->currentWidget()); }
QWebEngineView *BrowserWindow::activeView() const { return activeStack() ? activeStack()->findChild<QWebEngineView *>() : nullptr; }

QWebEngineView *BrowserWindow::newTab(bool incognito, const QUrl &url)
{
    auto *stack = new QStackedWidget(pages);
    stack->setProperty("incognito", incognito);
    stack->setProperty("sessionUrl", url.toString());
    auto *home = new HomePage(incognito, stack);
    home->setWeatherEnabled(settings.value("weather/enabled", true).toBool());
    home->setMailVisible(prefs.showMailButton);
    if (weatherFailed) home->showWeatherError();
    else if (latestWeather) home->showWeather(*latestWeather);
    stack->addWidget(home);
    auto *view = new QWebEngineView(stack);
    auto *page = new QWebEnginePage(incognito ? privateProfile : regularProfile, view);
    page->setBackgroundColor(palette().color(QPalette::Window));
    view->setPage(page);
    view->setZoomFactor(prefs.zoomPercent / 100.0);
    stack->addWidget(view);
    const int index = pages->addWidget(stack);
    tabs->addTab(incognito ? tr("Инкогнито") : tr("Новая вкладка"));
    auto *close = new QToolButton(tabs);
    close->setObjectName("tabCloseButton");
    close->setProperty("neoIcon", "close");
    close->setIcon(neoIcon("close", palette().color(QPalette::Text)));
    close->setIconSize(QSize(14, 14));
    close->setFixedSize(24, 24);
    close->setToolTip(tr("Закрыть вкладку"));
    close->setAccessibleName(tr("Закрыть вкладку"));
    tabs->setTabButton(index, QTabBar::RightSide, close);
    connect(close, &QToolButton::clicked, this, [this, stack] { closeTab(pages->indexOf(stack)); });
    connect(stack, &QObject::destroyed, close, &QObject::deleteLater);
    connect(home, &HomePage::submitted, this, [this, stack](const QString &text) {
        tabs->setCurrentIndex(pages->indexOf(stack));
        navigate(text);
    });
    connect(home, &HomePage::refreshWeather, &weather, &WeatherService::refresh);
    connect(home, &HomePage::mailRequested, this, [this, stack] {
        tabs->setCurrentIndex(pages->indexOf(stack));
        navigate(mailUrl(settings).toString());
    });
    connect(view, &QWebEngineView::urlChanged, this, [this, stack](const QUrl &target) {
        stack->setProperty("sessionUrl", target.toString());
        tabs->setTabToolTip(pages->indexOf(stack), target.toDisplayString());
        if (stack == activeStack()) updateChrome();
    });
    connect(view, &QWebEngineView::titleChanged, this, [this, stack, incognito](const QString &title) {
        if (stack->currentIndex() == 1) {
            tabs->setTabText(pages->indexOf(stack), (incognito ? QString::fromUtf8("◌ ") : QString()) + title.left(32));
        }
        if (stack == activeStack()) updateChrome();
    });
    connect(view, &QWebEngineView::loadProgress, this, [this, stack](int value) {
        stack->setProperty("progress", value);
        if (stack == activeStack()) { progress->setValue(value); progress->setVisible(value > 0 && value < 100); }
    });
    connect(view, &QWebEngineView::loadFinished, this, [this, view, stack, incognito](bool ok) {
        if (stack == activeStack()) { progress->hide(); updateChrome(); }
        if (ok && !incognito && (view->url().scheme() == "http" || view->url().scheme() == "https")) {
            auto history = settings.value("history").toList();
            history.prepend(QVariantMap{{"title", view->title()}, {"url", view->url().toString()}});
            while (history.size() > 500) history.removeLast();
            settings.setValue("history", history);
        }
    });
    connect(page, &QWebEnginePage::newWindowRequested, this, [this, incognito](QWebEngineNewWindowRequest &request) {
        auto *target = newTab(incognito);
        activeStack()->setCurrentIndex(1);
        request.openIn(target->page());
        updateChrome();
    });
    connect(page, &QWebEnginePage::permissionRequested, this, [this](QWebEnginePermission permission) {
        const auto type = permission.permissionType();
        QString feature, rule;
        if (type == QWebEnginePermission::PermissionType::Geolocation) { feature = tr("местоположению"); rule = prefs.geolocation; }
        else if (type == QWebEnginePermission::PermissionType::MediaAudioCapture) { feature = tr("микрофону"); rule = prefs.microphone; }
        else if (type == QWebEnginePermission::PermissionType::MediaVideoCapture) { feature = tr("камере"); rule = prefs.camera; }
        else { permission.deny(); return; }
        if (rule == "block") { permission.deny(); return; }
        if (askYesNo(this, tr("Разрешение сайта"), tr("Разрешить %1 доступ к %2?").arg(permission.origin().host(), feature))) permission.grant();
        else permission.deny();
    });
    tabs->parentWidget()->layout()->activate();
    tabs->setCurrentIndex(index);
    if (url.isValid() && !url.isEmpty() && (url.scheme() == "http" || url.scheme() == "https")) {
        stack->setCurrentIndex(1);
        view->load(url);
    } else home->findChild<QLineEdit *>()->setFocus();
    updateChrome();
    return view;
}

void BrowserWindow::navigate(const QString &input)
{
    const auto url = AddressResolver::resolve(input, settings.value("search/engine", "Google").toString());
    if (!url.isValid() || url.isEmpty()) return;
    activeStack()->setCurrentIndex(1);
    activeStack()->setProperty("sessionUrl", url.toString());
    activeView()->load(url);
    activeView()->setFocus();
    updateChrome();
}

void BrowserWindow::navigateHome()
{
    activeView()->stop();
    activeStack()->setCurrentIndex(0);
    tabs->setTabText(tabs->currentIndex(), activeStack()->property("incognito").toBool() ? tr("Инкогнито") : tr("Новая вкладка"));
    activeStack()->findChild<HomePage *>()->findChild<QLineEdit *>()->setFocus();
    updateChrome();
}

void BrowserWindow::closeTab(int index)
{
    auto *widget = pages->widget(index);
    const bool wasPrivate = widget->property("incognito").toBool();
    {
        const QSignalBlocker tabSignals(tabs);
        const QSignalBlocker pageSignals(pages);
        tabs->removeTab(index);
        pages->removeWidget(widget);
        pages->setCurrentIndex(tabs->currentIndex());
    }
    delete widget;
    if (wasPrivate) {
        bool hasPrivateTabs = false;
        for (int i = 0; i < tabs->count(); ++i) hasPrivateTabs |= pages->widget(i)->property("incognito").toBool();
        if (!hasPrivateTabs) {
            delete privateProfile;
            privateProfile = new QWebEngineProfile(this);
            configureProfile(privateProfile);
        }
    }
    if (tabs->count() == 0) newTab();
    updateChrome();
}

void BrowserWindow::updateChrome()
{
    auto *view = activeView();
    if (!view) return;
    const bool home = activeStack()->currentIndex() == 0;
    address->setText(home ? QString() : view->url().toDisplayString());
    address->setPlaceholderText(activeStack()->property("incognito").toBool() ? tr("Поиск инкогнито") : tr("Поиск или адрес сайта"));
    findChild<QToolButton *>("backButton")->setEnabled(!home);
    findChild<QToolButton *>("forwardButton")->setEnabled(!home && view->history()->canGoForward());
    findChild<QToolButton *>("bookmarkButton")->setEnabled(!home);
    const int value = activeStack()->property("progress").toInt();
    progress->setValue(value);
    progress->setVisible(!home && value > 0 && value < 100);
    setWindowTitle(home ? "NeoSerf" : view->title() + " — NeoSerf");
}

void BrowserWindow::showMenu()
{
    auto *menu = new QMenu(this);
    menu->setObjectName("overflowMenu");
    menu->setAttribute(Qt::WA_TranslucentBackground);
    const auto iconColor = palette().color(QPalette::Text);
    menu->addAction(neoIcon("plus", iconColor), tr("Новая вкладка"), this, [this] { newTab(); })->setProperty("neoIcon", "plus");
    menu->addAction(neoIcon("eye-off", iconColor), tr("Вкладка инкогнито"), this, [this] { newTab(true); })->setProperty("neoIcon", "eye-off");
    menu->addSeparator();
    menu->addAction(neoIcon("bookmark", iconColor), tr("Закладки"), this, [this] { showLibrary(false); })->setProperty("neoIcon", "bookmark");
    menu->addAction(neoIcon("history", iconColor), tr("История"), this, [this] { showLibrary(true); })->setProperty("neoIcon", "history");
    auto *find = menu->addAction(neoIcon("search", iconColor), tr("Найти на странице"), this, &BrowserWindow::findInPage);
    find->setProperty("neoIcon", "search");
    find->setEnabled(activeStack()->currentIndex() == 1);
    menu->addSeparator();
    menu->addAction(neoIcon("settings", iconColor), tr("Настройки"), this, &BrowserWindow::showSettings)->setProperty("neoIcon", "settings");
    menu->addSeparator();
    menu->addAction(neoIcon("logout", iconColor), tr("Выход"), this, &QWidget::close)->setProperty("neoIcon", "logout");
    connect(menu, &QMenu::aboutToHide, menu, &QObject::deleteLater);
    const auto *button = findChild<QToolButton *>("menuButton");
    menu->ensurePolished();
    const auto pos = button->mapToGlobal(QPoint(button->width() - menu->sizeHint().width(), button->height() + 8));
    menu->setWindowOpacity(0);
    menu->popup(pos);
    auto *animation = new QPropertyAnimation(menu, "windowOpacity", menu);
    animation->setDuration(170);
    animation->setStartValue(0.0);
    animation->setEndValue(1.0);
    animation->setEasingCurve(QEasingCurve::OutCubic);
    animation->start(QAbstractAnimation::DeleteWhenStopped);
}

void BrowserWindow::showSettings()
{
    SettingsDialog dialog(settings, regularProfile, this);
    if (dialog.exec() != QDialog::Accepted) return;
    const int previousZoom = prefs.zoomPercent;
    if (!dialog.wasReset()) dialog.save();
    settings.sync();
    applyTheme();
    applyPreferences();
    const bool weatherOn = settings.value("weather/enabled", true).toBool();
    for (auto *home : findChildren<HomePage *>()) home->setWeatherEnabled(weatherOn);
    weather.setEnabled(weatherOn);
    if (prefs.zoomPercent != previousZoom)
        for (auto *view : findChildren<QWebEngineView *>()) view->setZoomFactor(prefs.zoomPercent / 100.0);
}

void BrowserWindow::applyPreferences()
{
    prefs = Preferences::load(settings);
    applyProfilePreferences(regularProfile);
    applyProfilePreferences(privateProfile);
    findChild<QToolButton *>("homeButton")->setVisible(prefs.showHomeButton);
    for (auto *home : findChildren<HomePage *>()) home->setMailVisible(prefs.showMailButton);
}

void BrowserWindow::applyProfilePreferences(QWebEngineProfile *profile)
{
    auto *web = profile->settings();
    web->setAttribute(QWebEngineSettings::JavascriptEnabled, prefs.javascript);
    web->setAttribute(QWebEngineSettings::AutoLoadImages, prefs.images);
    web->setAttribute(QWebEngineSettings::JavascriptCanOpenWindows, prefs.popups);
    web->setAttribute(QWebEngineSettings::PlaybackRequiresUserGesture, !prefs.autoplay);
    web->setAttribute(QWebEngineSettings::ScrollAnimatorEnabled, prefs.smoothScroll);
    web->setAttribute(QWebEngineSettings::ForceDarkMode, prefs.forceDark);
    web->setFontSize(QWebEngineSettings::DefaultFontSize, prefs.fontSize);
    web->setFontSize(QWebEngineSettings::MinimumFontSize, prefs.minimumFontSize);
    if (!profile->isOffTheRecord())
        profile->setPersistentCookiesPolicy(prefs.keepCookies ? QWebEngineProfile::AllowPersistentCookies : QWebEngineProfile::NoPersistentCookies);
    if (prefs.blockThirdPartyCookies)
        profile->cookieStore()->setCookieFilter([](const QWebEngineCookieStore::FilterRequest &request) { return !request.thirdParty; });
    else profile->cookieStore()->setCookieFilter({});
}

void BrowserWindow::bookmarkPage()
{
    if (activeStack()->currentIndex() == 0) return;
    const auto url = activeView()->url().toString();
    auto items = settings.value("bookmarks").toList();
    for (const auto &item : items) if (item.toMap().value("url").toString() == url) {
        statusBar()->showMessage(tr("Страница уже в закладках"), 2500);
        return;
    }
    items.append(QVariantMap{{"title", activeView()->title()}, {"url", url}});
    settings.setValue("bookmarks", items);
    statusBar()->showMessage(tr("Сохранено в закладки"), 2500);
}

void BrowserWindow::showLibrary(bool history)
{
    QDialog dialog(this);
    dialog.setWindowTitle(history ? tr("История") : tr("Закладки"));
    dialog.resize(620, 480);
    auto *layout = new QVBoxLayout(&dialog);
    layout->setContentsMargins(20, 20, 20, 20);
    auto *list = new QListWidget(&dialog);
    const auto key = history ? "history" : "bookmarks";
    auto items = settings.value(key).toList();
    for (const auto &value : items) {
        const auto item = value.toMap();
        auto *row = new QListWidgetItem(item.value("title").toString() + "\n" + item.value("url").toString(), list);
        row->setData(Qt::UserRole, item.value("url"));
    }
    if (items.isEmpty()) layout->addWidget(new QLabel(history ? tr("История пока пуста") : tr("Сохранённых страниц пока нет"), &dialog));
    layout->addWidget(list);
    auto *buttons = new QDialogButtonBox(QDialogButtonBox::Close, &dialog);
    localizeButtons(buttons);
    auto *remove = buttons->addButton(tr("Удалить"), QDialogButtonBox::ActionRole);
    connect(remove, &QPushButton::clicked, &dialog, [&] {
        const int index = list->currentRow();
        if (index < 0) return;
        items.removeAt(index);
        delete list->takeItem(index);
        settings.setValue(key, items);
    });
    connect(list, &QListWidget::itemActivated, &dialog, [this, &dialog](QListWidgetItem *item) {
        newTab(false, QUrl(item->data(Qt::UserRole).toString()));
        dialog.accept();
    });
    connect(buttons, &QDialogButtonBox::rejected, &dialog, &QDialog::reject);
    layout->addWidget(buttons);
    dialog.exec();
}

void BrowserWindow::findInPage()
{
    if (activeStack()->currentIndex() == 0) return;
    QInputDialog input(this);
    input.setWindowTitle(tr("Найти на странице"));
    input.setLabelText(tr("Текст"));
    input.setOkButtonText(tr("Найти"));
    input.setCancelButtonText(tr("Отмена"));
    if (input.exec() == QDialog::Accepted) activeView()->findText(input.textValue());
}

void BrowserWindow::configureProfile(QWebEngineProfile *profile)
{
    connect(profile, &QWebEngineProfile::downloadRequested, this, [this](QWebEngineDownloadRequest *download) {
        QString path = prefs.downloadFolder + "/" + download->suggestedFileName();
        if (prefs.askDownloadLocation) path = QFileDialog::getSaveFileName(this, tr("Сохранить файл"), path);
        else {
            // Silent downloads must never overwrite an existing file.
            const QFileInfo target(path);
            for (int copy = 1; QFile::exists(path); ++copy)
                path = target.absolutePath() + "/" + target.completeBaseName() + QString(" (%1)").arg(copy) + (target.suffix().isEmpty() ? QString() : "." + target.suffix());
        }
        if (path.isEmpty()) { download->cancel(); return; }
        download->setDownloadDirectory(QFileInfo(path).absolutePath());
        download->setDownloadFileName(QFileInfo(path).fileName());
        connect(download, &QWebEngineDownloadRequest::stateChanged, this, [this, path](QWebEngineDownloadRequest::DownloadState state) {
            if (state == QWebEngineDownloadRequest::DownloadCompleted) statusBar()->showMessage(tr("Загружено: %1").arg(path), 8000);
            if (state == QWebEngineDownloadRequest::DownloadInterrupted) statusBar()->showMessage(tr("Не удалось загрузить файл"), 8000);
        });
        download->accept();
        statusBar()->showMessage(tr("Загрузка: %1").arg(QFileInfo(path).fileName()), 4000);
    });
    applyProfilePreferences(profile);
}

void BrowserWindow::saveSession()
{
    QStringList urls;
    int active = 0;
    for (int i = 0; i < tabs->count(); ++i) {
        auto *stack = qobject_cast<QStackedWidget *>(pages->widget(i));
        if (stack->property("incognito").toBool()) continue;
        if (i == tabs->currentIndex()) active = urls.size();
        urls.append(stack->currentIndex() == 0 ? QString() : stack->property("sessionUrl").toString());
    }
    settings.setValue("session/urls", urls);
    settings.setValue("session/active", active);
    settings.setValue("window/geometry", saveGeometry());
    settings.sync();
}

void BrowserWindow::closeEvent(QCloseEvent *event)
{
    saveSession();
    if (prefs.clearHistoryOnExit) { settings.remove("history"); settings.sync(); }
    QMainWindow::closeEvent(event);
}
