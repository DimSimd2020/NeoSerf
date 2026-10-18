#include "BrowserWindow.h"
#include "AddressResolver.h"
#include "HomePage.h"
#include "SettingsPage.h"
#include "UiAssets.h"
#include "Widgets.h"
#include <QApplication>
#include <QStyleHints>
#include <QAction>
#include <QCloseEvent>
#include <QCursor>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QFileDialog>
#include <QFileInfo>
#include <QHBoxLayout>
#include <QInputDialog>
#include <QLabel>
#include <QLineEdit>
#include <QPointer>
#include <QProgressBar>
#include <QShortcut>
#include <QSignalBlocker>
#include <QStackedWidget>
#include <QStandardPaths>
#include <QStatusBar>
#include <QTabBar>
#include <QTimer>
#include <QToolBar>
#include <QToolButton>
#include <QVBoxLayout>
#include <QWidgetAction>
#include <QWebEngineDownloadRequest>
#include <QWebEngineCookieStore>
#include <QWebEngineHistory>
#include <QWebEngineNewWindowRequest>
#include <QWebEnginePage>
#include <QWebEnginePermission>
#include <QWebEngineProfile>
#include <QWebEngineSettings>
#include <QWebEngineView>
#ifdef Q_OS_WIN
#include <windows.h>
#include <windowsx.h>
#endif

namespace {
// Stack pages of a tab: start page, web view, and the internal Settings page when it is open.
enum TabPage { HomeIndex = 0, WebIndex = 1, SettingsIndex = 2 };

QString internalUrl(const QString &section)
{
    if (section == "history" || section == "bookmarks") return "neoserf://" + section;
    return "neoserf://settings/" + section;
}
}

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
    // NeoSerf draws its own title area: tabs and window buttons share the top row.
    setWindowFlag(Qt::FramelessWindowHint);
    resize(1180, 800);
    setMinimumSize(640, 480);
    theme = settings.value("appearance/theme", "system").toString();
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
    auto addButton = [this](const QString &tip, const QString &name, auto callback) {
        auto *button = new QToolButton(toolbar);
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
    addButton(tr("Назад · Alt+Left"), "backButton", [this] { goBack(); });
    addButton(tr("Вперёд · Alt+Right"), "forwardButton", [this] { activeView()->forward(); });
    addButton(tr("Обновить · Ctrl+R"), "reloadButton", [this] {
        if (activeStack()->currentIndex() == HomeIndex) weather.refresh();
        else if (activeStack()->currentIndex() == WebIndex) activeView()->reload();
    });
    addButton(tr("Главная · Alt+Home"), "homeButton", [this] { navigateHome(); });
    address = new QLineEdit(toolbar);
    address->setObjectName("addressBar");
    address->setPlaceholderText(tr("Поиск или адрес сайта"));
    address->setAccessibleName(tr("Адресная строка"));
    addSubmitButton(address);
    toolbar->addWidget(address);
    connect(address, &QLineEdit::returnPressed, this, [this] { navigate(address->text()); });
    addButton(tr("Сохранить в закладки · Ctrl+D"), "bookmarkButton", [this] { bookmarkPage(); });
    addButton(tr("Меню"), "menuButton", [this] { showMenu(); });

    auto *central = new QWidget(this);
    auto *layout = new QVBoxLayout(central);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);
    tabStrip = new QWidget(central);
    tabStrip->setObjectName("tabStrip");
    tabStrip->setAttribute(Qt::WA_StyledBackground);
    tabStrip->setFixedHeight(42);
    auto *tabLayout = new QHBoxLayout(tabStrip);
    tabLayout->setContentsMargins(10, 0, 0, 0);
    tabLayout->setSpacing(0);
    tabs = new CompactTabBar(tabStrip);
    tabs->setObjectName("browserTabs");
    tabs->setMovable(true);
    tabs->setExpanding(false);
    tabs->setDrawBase(false);
    tabs->setUsesScrollButtons(true);
    tabs->setElideMode(Qt::ElideRight);
    tabs->setSizePolicy(QSizePolicy::Maximum, QSizePolicy::Fixed);
    tabLayout->addWidget(tabs, 0, Qt::AlignBottom);
    tabLayout->addSpacing(6);
    auto *newButton = new QToolButton(tabStrip);
    newButton->setProperty("neoIcon", "plus");
    newButton->setIcon(neoIcon("plus", palette().color(QPalette::Text)));
    newButton->setIconSize(QSize(18, 18));
    newButton->setFixedSize(32, 32);
    newButton->setToolTip(tr("Новая вкладка · Ctrl+T"));
    newButton->setAccessibleName(tr("Новая вкладка"));
    newButton->setObjectName("newTabButton");
    tabLayout->addWidget(newButton, 0, Qt::AlignBottom);
    tabLayout->addStretch();
    connect(newButton, &QToolButton::clicked, this, [this] { newTab(); });
    // Window buttons in the tab row, flush with the top-right corner like native caption buttons.
    auto windowButton = [this, tabLayout](const QString &icon, const QString &tip, const QString &name) {
        auto *button = new QToolButton(tabStrip);
        button->setObjectName(name);
        button->setProperty("windowButton", true);
        button->setProperty("neoIcon", icon);
        button->setIcon(neoIcon(icon, palette().color(QPalette::Text)));
        button->setIconSize(QSize(16, 16));
        button->setFixedSize(46, 34);
        button->setToolTip(tip);
        button->setAccessibleName(tip);
        button->setFocusPolicy(Qt::NoFocus);
        tabLayout->addWidget(button, 0, Qt::AlignTop);
        return button;
    };
    auto *minimize = windowButton("minimize", tr("Свернуть"), "minimizeButton");
    maximizeButton = windowButton("maximize", tr("Развернуть"), "maximizeButton");
    auto *closeWindow = windowButton("window-close", tr("Закрыть"), "closeWindowButton");
    closeWindow->installEventFilter(this);
    connect(minimize, &QToolButton::clicked, this, &QWidget::showMinimized);
    connect(maximizeButton, &QToolButton::clicked, this, [this] {
#ifdef Q_OS_WIN
        // Go through Windows like a native caption button, so the button, a double-click and snapping
        // all produce the same maximized state and restore the same size.
        if (QGuiApplication::platformName() == "windows") {
            SendMessage(reinterpret_cast<HWND>(winId()), WM_SYSCOMMAND, isMaximized() ? SC_RESTORE : SC_MAXIMIZE, 0);
            return;
        }
#endif
        isMaximized() ? showNormal() : showMaximized();
    });
    connect(closeWindow, &QToolButton::clicked, this, &QWidget::close);
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
    shortcut(QKeySequence("Ctrl+Shift+T"), [this] { reopenClosedTab(); });
    shortcut(QKeySequence("Ctrl+F4"), [this] { closeTab(tabs->currentIndex()); });
    shortcut(QKeySequence("Ctrl+W"), [this] { closeTab(tabs->currentIndex()); });
    shortcut(QKeySequence("Ctrl+L"), [this] { address->setFocus(); address->selectAll(); });
    shortcut(QKeySequence("Ctrl+R"), [this] { if (activeStack()->currentIndex() == WebIndex) activeView()->reload(); });
    shortcut(QKeySequence("Alt+Left"), [this] { goBack(); });
    shortcut(QKeySequence("Alt+Right"), [this] { activeView()->forward(); });
    shortcut(QKeySequence("Alt+Home"), [this] { navigateHome(); });
    shortcut(QKeySequence("Ctrl+D"), [this] { bookmarkPage(); });
    shortcut(QKeySequence("Ctrl+H"), [this] { showSettings("history"); });
    shortcut(QKeySequence("Ctrl+Shift+O"), [this] { showSettings("bookmarks"); });
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
#ifdef Q_OS_WIN
    // Keep the native frame styles so Windows still snaps, animates and shadows the window;
    // nativeEvent() then turns the whole window into client area.
    const auto hwnd = reinterpret_cast<HWND>(winId());
    SetWindowLongPtr(hwnd, GWL_STYLE, GetWindowLongPtr(hwnd, GWL_STYLE) | WS_THICKFRAME | WS_CAPTION | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_SYSMENU);
    SetWindowPos(hwnd, nullptr, 0, 0, 0, 0, SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
#endif
    this->layout()->activate();
    layout->activate();
    const auto urls = prefs.startup == "home" ? QStringList{QString()}
        : settings.value("session/urls", QStringList{QString()}).toStringList();
    for (const auto &url : urls) newTab(false, QUrl(url));
    if (tabs->count() == 0) newTab();
    tabs->setCurrentIndex(qBound(0, settings.value("session/active", 0).toInt(), tabs->count() - 1));
    weatherOn = settings.value("weather/enabled", true).toBool();
    weather.setEnabled(weatherOn);
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
SettingsPage *BrowserWindow::settingsPageIn(QStackedWidget *stack)
{
    return stack ? stack->findChild<SettingsPage *>(QString(), Qt::FindDirectChildrenOnly) : nullptr;
}

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
    // Page context menus use the same rounded popup as the rest of NeoSerf.
    view->setContextMenuPolicy(Qt::CustomContextMenu);
    connect(view, &QWidget::customContextMenuRequested, this, [view](const QPoint &pos) {
        auto *standard = view->createStandardContextMenu();
        auto *menu = new PopupMenu(view);
        for (auto *action : standard->actions()) menu->addAction(action);
        connect(menu, &QMenu::aboutToHide, menu, [menu, standard] { menu->deleteLater(); standard->deleteLater(); });
        menu->popupAt(view->mapToGlobal(pos));
    });
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
        if (stack->currentIndex() == WebIndex) {
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
            history.prepend(QVariantMap{{"title", view->title()}, {"url", view->url().toString()},
                {"time", QDateTime::currentSecsSinceEpoch()}});
            while (history.size() > 500) history.removeLast();
            settings.setValue("history", history);
        }
    });
    connect(page, &QWebEnginePage::newWindowRequested, this, [this, incognito](QWebEngineNewWindowRequest &request) {
        auto *target = newTab(incognito);
        activeStack()->setCurrentIndex(WebIndex);
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
        stack->setCurrentIndex(WebIndex);
        view->load(url);
    } else if (url.scheme() == "neoserf" && !incognito) {
        const auto section = url.host() == "settings" ? url.path().mid(1) : url.host();
        openSettingsIn(stack, section.isEmpty() ? "appearance" : section);
    } else home->findChild<QLineEdit *>()->setFocus();
    updateChrome();
    return view;
}

void BrowserWindow::showSettings(const QString &section)
{
    // Like Chrome, an open settings tab is reused instead of opening another one.
    for (int i = 0; i < pages->count(); ++i) {
        auto *stack = qobject_cast<QStackedWidget *>(pages->widget(i));
        if (auto *page = settingsPageIn(stack); page && stack->currentWidget() == page) {
            tabs->setCurrentIndex(i);
            page->showSection(section);
            return;
        }
    }
    auto *stack = activeStack();
    // An untouched new tab turns into Settings; otherwise Settings get a tab of their own.
    if (stack->currentIndex() != HomeIndex || stack->property("incognito").toBool()) {
        newTab();
        stack = activeStack();
    }
    openSettingsIn(stack, section);
}

void BrowserWindow::openSettingsIn(QStackedWidget *stack, const QString &section)
{
    auto *page = settingsPageIn(stack);
    if (!page) {
        page = new SettingsPage(settings, regularProfile, stack);
        stack->addWidget(page);
        connect(page, &SettingsPage::changed, this, &BrowserWindow::applySettings);
        connect(page, &SettingsPage::openUrl, this, [this](const QUrl &url) { newTab(false, url); });
        connect(page, &SettingsPage::sectionChanged, this, [this, stack] { updateInternalTab(stack); });
        connect(page, &SettingsPage::resetDone, this, [this, stack = QPointer<QStackedWidget>(stack), page] {
            // Rebuild the page after the signal returns, so it shows the restored defaults.
            QTimer::singleShot(0, this, [this, stack, page] {
                if (!stack) return;
                const bool shown = stack->currentWidget() == page;
                delete page;
                if (shown) openSettingsIn(stack, "about");
            });
        });
    }
    stack->setCurrentWidget(page);
    page->showSection(section, false);
    tabs->setCurrentIndex(pages->indexOf(stack));
    updateInternalTab(stack);
}

void BrowserWindow::updateInternalTab(QStackedWidget *stack)
{
    auto *page = settingsPageIn(stack);
    if (!page || stack->currentWidget() != page) return;
    const auto section = page->currentSection();
    tabs->setTabText(pages->indexOf(stack), section == "history" || section == "bookmarks" ? page->sectionTitle() : tr("Настройки"));
    stack->setProperty("sessionUrl", internalUrl(section));
    if (stack == activeStack()) updateChrome();
}

void BrowserWindow::navigate(const QString &input)
{
    const QUrl internal(input.trimmed());
    if (internal.scheme() == "neoserf") {
        const auto section = internal.host() == "settings" ? internal.path().mid(1) : internal.host();
        openSettingsIn(activeStack(), section.isEmpty() ? "appearance" : section);
        return;
    }
    const auto url = AddressResolver::resolve(input, settings.value("search/engine", "Google").toString());
    if (!url.isValid() || url.isEmpty()) return;
    activeStack()->setCurrentIndex(WebIndex);
    activeStack()->setProperty("sessionUrl", url.toString());
    activeView()->load(url);
    activeView()->setFocus();
    updateChrome();
}

void BrowserWindow::navigateHome()
{
    activeView()->stop();
    activeStack()->setCurrentIndex(HomeIndex);
    tabs->setTabText(tabs->currentIndex(), activeStack()->property("incognito").toBool() ? tr("Инкогнито") : tr("Новая вкладка"));
    activeStack()->findChild<HomePage *>()->findChild<QLineEdit *>()->setFocus();
    updateChrome();
}

void BrowserWindow::goBack()
{
    auto *stack = activeStack();
    if (stack->currentIndex() == SettingsIndex) {
        // Leaving Settings returns to the page that was open in this tab, if any.
        if (activeView()->url().isEmpty()) navigateHome();
        else {
            stack->setCurrentIndex(WebIndex);
            tabs->setTabText(tabs->currentIndex(), activeView()->title().left(32));
            stack->setProperty("sessionUrl", activeView()->url().toString());
            updateChrome();
        }
    } else if (activeView()->history()->canGoBack()) activeView()->back();
    else navigateHome();
}

void BrowserWindow::closeTab(int index)
{
    auto *widget = qobject_cast<QStackedWidget *>(pages->widget(index));
    const bool wasPrivate = widget->property("incognito").toBool();
    if (!wasPrivate && widget->currentIndex() == WebIndex) {
        auto *view = widget->findChild<QWebEngineView *>();
        closedTabs.prepend({view->title(), view->url().toString()});
        while (closedTabs.size() > 10) closedTabs.removeLast();
    }
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

void BrowserWindow::reopenClosedTab()
{
    if (closedTabs.isEmpty()) return;
    newTab(false, QUrl(closedTabs.takeFirst().second));
}

void BrowserWindow::updateChrome()
{
    auto *view = activeView();
    if (!view) return;
    const int index = activeStack()->currentIndex();
    const bool web = index == WebIndex;
    auto *settingsPage = settingsPageIn(activeStack());
    if (index == SettingsIndex && settingsPage) address->setText(internalUrl(settingsPage->currentSection()));
    else address->setText(web ? view->url().toDisplayString() : QString());
    address->setPlaceholderText(activeStack()->property("incognito").toBool() ? tr("Поиск инкогнито") : tr("Поиск или адрес сайта"));
    findChild<QToolButton *>("backButton")->setEnabled(index != HomeIndex);
    findChild<QToolButton *>("forwardButton")->setEnabled(web && view->history()->canGoForward());
    findChild<QToolButton *>("bookmarkButton")->setEnabled(web);
    const int value = activeStack()->property("progress").toInt();
    progress->setValue(value);
    progress->setVisible(web && value > 0 && value < 100);
    if (index == SettingsIndex && settingsPage) setWindowTitle(settingsPage->sectionTitle() + " — NeoSerf");
    else setWindowTitle(web ? view->title() + " — NeoSerf" : "NeoSerf");
}

void BrowserWindow::showMenu()
{
    auto *menu = new PopupMenu(this);
    menu->setObjectName("overflowMenu");
    const auto iconColor = palette().color(QPalette::Text);
    auto add = [iconColor](QMenu *target, const QString &icon, const QString &text, const QString &keys, auto slot) {
        auto *action = target->addAction(neoIcon(icon, iconColor), keys.isEmpty() ? text : text + "\t" + keys);
        action->setProperty("neoIcon", icon);
        QObject::connect(action, &QAction::triggered, target, slot);
        return action;
    };
    auto header = [](QMenu *target, const QString &text) {
        auto *label = new QLabel(text);
        label->setObjectName("menuHeader");
        auto *action = new QWidgetAction(target);
        action->setDefaultWidget(label);
        target->addAction(action);
    };
    const bool web = activeStack()->currentIndex() == WebIndex;
    add(menu, "plus", tr("Новая вкладка"), "Ctrl+T", [this] { newTab(); });
    add(menu, "eye-off", tr("Вкладка инкогнито"), "Ctrl+Shift+N", [this] { newTab(true); });
    menu->addSeparator();

    // History opens on hover like in Chrome; clicking the item itself opens the full history.
    auto *history = menu->addPopup(neoIcon("history", iconColor), tr("История"));
    history->setObjectName("historyMenu");
    history->menuAction()->setProperty("neoIcon", "history");
    PopupMenu::setClickableSubmenu(history->menuAction());
    connect(history->menuAction(), &QAction::triggered, this, [this] { showSettings("history"); });
    add(history, "history", tr("Открыть историю"), "Ctrl+H", [this] { showSettings("history"); });
    if (!closedTabs.isEmpty()) {
        history->addSeparator();
        header(history, tr("Недавно закрытые"));
        for (int i = 0; i < qMin(5, int(closedTabs.size())); ++i) {
            const auto entry = closedTabs.at(i);
            add(history, "globe", entry.first.isEmpty() ? entry.second : entry.first.left(60), i == 0 ? "Ctrl+Shift+T" : QString(),
                [this, entry] { closedTabs.removeOne(entry); newTab(false, QUrl(entry.second)); });
        }
    }
    const auto visited = settings.value("history").toList();
    if (!visited.isEmpty()) {
        history->addSeparator();
        header(history, tr("Недавно посещённые"));
        for (int i = 0; i < qMin(8, int(visited.size())); ++i) {
            const auto entry = visited.at(i).toMap();
            const auto title = entry.value("title").toString();
            const auto url = entry.value("url").toString();
            add(history, "globe", (title.isEmpty() ? url : title).left(60), QString(), [this, url] { navigate(url); });
        }
    }

    auto *bookmarks = menu->addPopup(neoIcon("bookmark", iconColor), tr("Закладки"));
    bookmarks->setObjectName("bookmarksMenu");
    bookmarks->menuAction()->setProperty("neoIcon", "bookmark");
    PopupMenu::setClickableSubmenu(bookmarks->menuAction());
    connect(bookmarks->menuAction(), &QAction::triggered, this, [this] { showSettings("bookmarks"); });
    add(bookmarks, "bookmark", tr("Добавить в закладки"), "Ctrl+D", [this] { bookmarkPage(); })->setEnabled(web);
    add(bookmarks, "open-new", tr("Все закладки"), "Ctrl+Shift+O", [this] { showSettings("bookmarks"); });
    const auto saved = settings.value("bookmarks").toList();
    if (!saved.isEmpty()) bookmarks->addSeparator();
    for (int i = 0; i < qMin(12, int(saved.size())); ++i) {
        const auto entry = saved.at(i).toMap();
        const auto title = entry.value("title").toString();
        const auto url = entry.value("url").toString();
        add(bookmarks, "globe", (title.isEmpty() ? url : title).left(60), QString(), [this, url] { navigate(url); });
    }
    menu->addSeparator();

    // Zoom stepper inside the menu, like Chrome.
    auto *zoomRow = new QWidget;
    zoomRow->setObjectName("zoomRow");
    auto *zoomLayout = new QHBoxLayout(zoomRow);
    zoomLayout->setContentsMargins(18, 2, 10, 2);
    zoomLayout->setSpacing(4);
    auto *zoomIcon = new QLabel(zoomRow);
    zoomIcon->setPixmap(neoIcon("search", iconColor).pixmap(20, 20));
    zoomLayout->addWidget(zoomIcon);
    zoomLayout->addSpacing(14);
    zoomLayout->addWidget(new QLabel(tr("Масштаб"), zoomRow), 1);
    auto *zoomValue = new QLabel(zoomRow);
    zoomValue->setObjectName("zoomValue");
    zoomValue->setAlignment(Qt::AlignCenter);
    zoomValue->setMinimumWidth(52);
    auto stepper = [zoomRow, iconColor](const QString &icon, const QString &name) {
        auto *button = new QToolButton(zoomRow);
        button->setObjectName(name);
        button->setIcon(neoIcon(icon, iconColor));
        button->setIconSize(QSize(16, 16));
        button->setFixedSize(32, 32);
        button->setEnabled(false);
        return button;
    };
    auto *zoomOut = stepper("minus", "zoomOut");
    auto *zoomIn = stepper("plus", "zoomIn");
    zoomLayout->addWidget(zoomOut);
    zoomLayout->addWidget(zoomValue);
    zoomLayout->addWidget(zoomIn);
    auto *view = activeView();
    auto showZoom = [zoomValue, view] { zoomValue->setText(QString("%1 %").arg(qRound(view->zoomFactor() * 100))); };
    showZoom();
    zoomOut->setEnabled(web);
    zoomIn->setEnabled(web);
    connect(zoomOut, &QToolButton::clicked, zoomRow, [view, showZoom] { view->setZoomFactor(qMax(0.3, view->zoomFactor() - 0.1)); showZoom(); });
    connect(zoomIn, &QToolButton::clicked, zoomRow, [view, showZoom] { view->setZoomFactor(qMin(3.0, view->zoomFactor() + 0.1)); showZoom(); });
    auto *zoomAction = new QWidgetAction(menu);
    zoomAction->setDefaultWidget(zoomRow);
    menu->addAction(zoomAction);
    menu->addSeparator();
    add(menu, "search", tr("Найти на странице"), "Ctrl+F", [this] { findInPage(); })->setEnabled(web);
    menu->addSeparator();
    add(menu, "settings", tr("Настройки"), QString(), [this] { showSettings(); });
    add(menu, "logout", tr("Выход"), QString(), [this] { close(); });
    connect(menu, &QMenu::aboutToHide, menu, [menu] {
        // Submenu clicks are delivered after the menu hides, so keep it alive until then.
        QTimer::singleShot(0, menu, &QObject::deleteLater);
    });
    const auto *button = findChild<QToolButton *>("menuButton");
    menu->ensurePolished();
    const int panelWidth = menu->sizeHint().width() - 2 * PopupMenu::shadow;
    menu->popupAt(button->mapToGlobal(QPoint(button->width() - panelWidth, button->height() + 6)));
}

void BrowserWindow::applySettings()
{
    const int previousZoom = prefs.zoomPercent;
    const auto selectedTheme = settings.value("appearance/theme", "system").toString();
    if (selectedTheme != theme) {
        theme = selectedTheme;
        applyTheme();
    }
    applyPreferences();
    const bool weatherWanted = settings.value("weather/enabled", true).toBool();
    if (weatherWanted != weatherOn) {
        weatherOn = weatherWanted;
        for (auto *home : findChildren<HomePage *>()) home->setWeatherEnabled(weatherOn);
        weather.setEnabled(weatherOn);
    }
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
    if (activeStack()->currentIndex() != WebIndex) return;
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

void BrowserWindow::findInPage()
{
    if (activeStack()->currentIndex() != WebIndex) return;
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
        urls.append(stack->currentIndex() == HomeIndex ? QString() : stack->property("sessionUrl").toString());
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

void BrowserWindow::changeEvent(QEvent *event)
{
    if (event->type() == QEvent::WindowStateChange) {
        const bool maximized = isMaximized();
        maximizeButton->setProperty("neoIcon", maximized ? "restore" : "maximize");
        maximizeButton->setIcon(neoIcon(maximized ? "restore" : "maximize", palette().color(QPalette::Text)));
        maximizeButton->setToolTip(maximized ? tr("Свернуть в окно") : tr("Развернуть"));
    }
    QMainWindow::changeEvent(event);
}

bool BrowserWindow::eventFilter(QObject *watched, QEvent *event)
{
    // The close button turns red with a white cross on hover, like the native one.
    if (auto *button = qobject_cast<QToolButton *>(watched); button && button->objectName() == "closeWindowButton") {
        if (event->type() == QEvent::Enter) button->setIcon(neoIcon("window-close", Qt::white));
        else if (event->type() == QEvent::Leave) button->setIcon(neoIcon("window-close", palette().color(QPalette::Text)));
    }
    return QMainWindow::eventFilter(watched, event);
}

int BrowserWindow::hitTest(const QPoint &pos) const
{
#ifdef Q_OS_WIN
    if (!isMaximized() && !isFullScreen()) {
        const int border = 6;
        const bool left = pos.x() < border, right = pos.x() >= width() - border;
        const bool top = pos.y() < border, bottom = pos.y() >= height() - border;
        if (top && left) return HTTOPLEFT;
        if (top && right) return HTTOPRIGHT;
        if (bottom && left) return HTBOTTOMLEFT;
        if (bottom && right) return HTBOTTOMRIGHT;
        if (left) return HTLEFT;
        if (right) return HTRIGHT;
        if (top) return HTTOP;
        if (bottom) return HTBOTTOM;
    }
    // Empty space in the tab row drags the window; tabs and buttons stay clickable.
    const QPoint inStrip = tabStrip->mapFrom(this, pos);
    if (tabStrip->rect().contains(inStrip)) {
        auto *child = tabStrip->childAt(inStrip);
        if (!child || (child == tabs && tabs->tabAt(tabs->mapFrom(tabStrip, inStrip)) < 0)) return HTCAPTION;
    }
    return HTCLIENT;
#else
    Q_UNUSED(pos);
    return 0;
#endif
}

bool BrowserWindow::nativeEvent(const QByteArray &eventType, void *message, qintptr *result)
{
#ifdef Q_OS_WIN
    auto *msg = static_cast<MSG *>(message);
    if (msg->message == WM_NCCALCSIZE && msg->wParam) {
        if (IsZoomed(msg->hwnd)) {
            // A maximized window may overhang the work area by its frame; trim exactly the overhang so the
            // content stays on screen. Trimming a window that already fits would leave a black border.
            auto &client = reinterpret_cast<NCCALCSIZE_PARAMS *>(msg->lParam)->rgrc[0];
            MONITORINFO monitor{sizeof(monitor)};
            if (GetMonitorInfo(MonitorFromRect(&client, MONITOR_DEFAULTTONEAREST), &monitor)) {
                const RECT &work = monitor.rcWork;
                client.left = qMax(client.left, work.left);
                client.top = qMax(client.top, work.top);
                client.right = qMin(client.right, work.right);
                client.bottom = qMin(client.bottom, work.bottom);
            }
        }
        *result = 0;
        return true;
    }
    if (msg->message == WM_NCHITTEST) {
        *result = hitTest(mapFromGlobal(QCursor::pos()));
        return true;
    }
#endif
    return QMainWindow::nativeEvent(eventType, message, result);
}
