#include "SettingsPage.h"
#include "ImportWidget.h"
#include "Preferences.h"
#include "UiAssets.h"
#include "Widgets.h"
#include <QAction>
#include <QButtonGroup>
#include <QCoreApplication>
#include <QDateTime>
#include <QDir>
#include <QFileDialog>
#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QLocale>
#include <QPainter>
#include <QPropertyAnimation>
#include <QPushButton>
#include <QScrollArea>
#include <QSettings>
#include <QStackedWidget>
#include <QSvgRenderer>
#include <QToolButton>
#include <QVBoxLayout>
#include <QWebEngineCookieStore>
#include <QWebEngineProfile>
#include <QtWebEngineCore/qtwebenginecoreglobal.h>
#include <tuple>

namespace {
using Choices = QList<QPair<QString, QVariant>>;

void select(QComboBox *combo, const QVariant &value)
{
    for (int i = 0; i < combo->count(); ++i)
        if (combo->itemData(i).toString() == value.toString()) { combo->setCurrentIndex(i); return; }
    combo->setCurrentIndex(0);
}

QLabel *makeLabel(const QString &text, const char *name, QWidget *parent)
{
    auto *label = new QLabel(text, parent);
    label->setObjectName(name);
    label->setWordWrap(true);
    return label;
}
}

SettingsPage::SettingsPage(QSettings &settings, QWebEngineProfile *profile, QWidget *parent)
    : QWidget(parent), settings(settings), profile(profile)
{
    setObjectName("settingsPage");
    const auto prefs = Preferences::load(settings);
    auto *root = new QHBoxLayout(this);
    root->setContentsMargins(0, 0, 0, 0);
    root->setSpacing(0);
    auto *sidebar = new QWidget(this);
    sidebar->setObjectName("settingsSidebar");
    sidebar->setAttribute(Qt::WA_StyledBackground);
    sidebar->setFixedWidth(256);
    navLayout = new QVBoxLayout(sidebar);
    navLayout->setContentsMargins(14, 22, 14, 16);
    navLayout->setSpacing(2);
    auto *title = makeLabel(tr("Настройки"), "settingsTitle", sidebar);
    navLayout->addWidget(title);
    navLayout->addSpacing(12);
    // The pill behind the selected item glides between items.
    indicator = new QWidget(sidebar);
    indicator->setObjectName("navIndicator");
    indicator->setAttribute(Qt::WA_StyledBackground);
    navigation = new QButtonGroup(this);
    navigation->setExclusive(true);
    sections = new QStackedWidget(this);
    sections->setObjectName("settingsSections");
    root->addWidget(sidebar);
    root->addWidget(sections, 1);
    const auto muted = palette().color(QPalette::PlaceholderText);

    auto *look = addSection("appearance", tr("Оформление"), "palette");
    auto *lookCard = addCard(look);
    auto *theme = addChoice(lookCard, "appearance/theme", tr("Тема"),
        {{tr("Как на устройстве"), "system"}, {tr("Светлая"), "light"}, {tr("Тёмная"), "dark"}}, settings.value("appearance/theme", "system"));
    theme->setObjectName("themeSelector");
    const QStringList themeIcons{"device", "sun", "moon"};
    for (int i = 0; i < themeIcons.size(); ++i) theme->setItemIcon(i, neoIcon(themeIcons.at(i), palette().color(QPalette::Text)));
    auto *language = addChoice(lookCard, "appearance/language", tr("Язык интерфейса"),
        {{tr("Как на устройстве"), "system"}, {"Русский", "ru"}, {"English", "en"}}, settings.value("appearance/language", "system"));
    language->setObjectName("languageSelector");
    lookCard->addWidget(makeLabel(tr("Язык изменится после перезапуска NeoSerf."), "rowNote", this));
    auto *toolbarCard = addCard(look, tr("Панель и главная страница"));
    addToggle(toolbarCard, "ui/showHome", tr("Показывать кнопку «Главная»"), prefs.showHomeButton);
    addToggle(toolbarCard, "ui/showMail", tr("Показывать кнопку «Почта»"), prefs.showMailButton);
    addToggle(toolbarCard, "weather/enabled", tr("Погода на главной"), settings.value("weather/enabled", true).toBool());
    toolbarCard->addWidget(makeLabel(tr("Регион определяется по IP-адресу. VPN может изменить регион.\n"
        "Данные: Open-Meteo или MET Norway. Геолокация: ipwho.is."), "rowNote", this));

    auto *searchPage = addSection("search", tr("Поиск и почта"), "search");
    auto *searchCard = addCard(searchPage);
    Choices engines;
    for (const auto &name : {QString("Google"), QString("DuckDuckGo"), QString::fromUtf8("Яндекс"), QString("Bing"), QString("Brave")})
        engines.append({name == QString::fromUtf8("Яндекс") ? tr("Яндекс") : name, name});
    addChoice(searchCard, "search/engine", tr("Поисковая система"), engines, settings.value("search/engine", "Google"));
    const Choices mails{{"Gmail", "https://mail.google.com/"}, {tr("Яндекс Почта"), "https://mail.yandex.ru/"},
        {"Outlook", "https://outlook.live.com/mail/"}, {"Mail.ru", "https://e.mail.ru/"}, {tr("Другой сервис"), "custom"}};
    const auto configuredMail = mailUrl(settings).toString();
    mailProvider = addChoice(searchCard, QString(), tr("Почта по умолчанию"), mails, configuredMail);
    mailProvider->setObjectName("mailProvider");
    if (mailProvider->findData(configuredMail) < 0) mailProvider->setCurrentIndex(mailProvider->count() - 1);
    mailAddress = new QLineEdit(configuredMail, this);
    mailAddress->setObjectName("mailAddress");
    mailAddress->setPlaceholderText("https://mail.example.com/");
    mailError = makeLabel(QString(), "rowError", this);
    mailError->hide();
    searchCard->addWidget(mailAddress);
    searchCard->addWidget(mailError);
    searchCard->addSpacing(4);
    auto syncMail = [this] {
        const bool custom = mailProvider->currentData() == "custom";
        mailAddress->setVisible(custom);
        if (!custom) storeMail();
    };
    connect(mailProvider, &QComboBox::currentIndexChanged, this, syncMail);
    connect(mailAddress, &QLineEdit::editingFinished, this, &SettingsPage::storeMail);
    mailAddress->setVisible(mailProvider->currentData() == "custom");

    auto *startup = addSection("startup", tr("При запуске"), "power");
    addChoice(addCard(startup), "startup/mode", tr("Открывать"),
        {{tr("Вкладки с прошлого сеанса"), "restore"}, {tr("Новую вкладку с поиском"), "home"}}, prefs.startup);

    auto *privacy = addSection("privacy", tr("Конфиденциальность"), "shield");
    auto *cookies = addCard(privacy);
    addToggle(cookies, "privacy/keepCookies", tr("Сохранять cookie между запусками"), prefs.keepCookies);
    addToggle(cookies, "privacy/blockThirdPartyCookies", tr("Блокировать сторонние cookie"), prefs.blockThirdPartyCookies);
    addToggle(cookies, "privacy/clearHistoryOnExit", tr("Очищать историю при выходе"), prefs.clearHistoryOnExit);
    cookies->addWidget(makeLabel(tr("Вкладки инкогнито ничего не сохраняют: ни историю, ни cookie."), "rowNote", this));
    auto *clearCard = addCard(privacy, tr("Удалить данные о работе в браузере"));
    QList<QCheckBox *> clearOptions;
    const std::tuple<const char *, QString, bool> clearChoices[]{{"clearHistory", tr("История"), true},
        {"clearBookmarks", tr("Закладки"), false}, {"clearCookies", tr("Файлы cookie"), true}, {"clearCache", tr("Кэш изображений и файлов"), true}};
    for (const auto &[name, text, on] : clearChoices) {
        auto *option = new ToggleSwitch(text, this);
        option->setObjectName(name);
        option->setChecked(on);
        option->setMinimumHeight(48);
        addRow(clearCard, option);
        clearOptions.append(option);
    }
    auto *clearRow = new QWidget(this);
    auto *clearLayout = new QHBoxLayout(clearRow);
    clearLayout->setContentsMargins(0, 10, 0, 12);
    auto *cleared = makeLabel(QString(), "rowNote", clearRow);
    auto *clear = new QPushButton(tr("Удалить данные"), clearRow);
    clear->setObjectName("clearData");
    clear->setCursor(Qt::PointingHandCursor);
    clearLayout->addWidget(cleared, 1);
    clearLayout->addWidget(clear);
    clearCard->addWidget(clearRow);
    connect(clear, &QPushButton::clicked, this, [this, clearOptions, cleared] {
        if (clearOptions[0]->isChecked()) { this->settings.remove("history"); this->profile->clearAllVisitedLinks(); }
        if (clearOptions[1]->isChecked()) this->settings.remove("bookmarks");
        if (clearOptions[2]->isChecked()) this->profile->cookieStore()->deleteAllCookies();
        if (clearOptions[3]->isChecked()) this->profile->clearHttpCache();
        this->settings.sync();
        cleared->setText(tr("Выбранные данные удалены."));
    });

    const Choices permission{{tr("Спрашивать"), "ask"}, {tr("Блокировать"), "block"}};
    auto *sites = addSection("sites", tr("Сайты"), "globe");
    auto *content = addCard(sites, tr("Содержимое"));
    addToggle(content, "site/javascript", tr("Разрешить JavaScript"), prefs.javascript);
    addToggle(content, "site/images", tr("Показывать изображения"), prefs.images);
    addToggle(content, "site/popups", tr("Разрешить всплывающие окна без нажатия"), prefs.popups);
    addToggle(content, "site/autoplay", tr("Автовоспроизведение медиа"), prefs.autoplay);
    addToggle(content, "site/smoothScroll", tr("Плавная прокрутка"), prefs.smoothScroll);
    addToggle(content, "site/forceDark", tr("Тёмное оформление сайтов"), prefs.forceDark);
    auto *permissions = addCard(sites, tr("Разрешения"));
    addChoice(permissions, "permissions/geolocation", tr("Местоположение"), permission, prefs.geolocation);
    addChoice(permissions, "permissions/camera", tr("Камера"), permission, prefs.camera);
    addChoice(permissions, "permissions/microphone", tr("Микрофон"), permission, prefs.microphone);

    auto *fonts = addSection("fonts", tr("Шрифты и масштаб"), "text-plus");
    auto *fontCard = addCard(fonts);
    Choices zooms;
    for (int percent : {50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300}) zooms.append({QString("%1%").arg(percent), percent});
    addChoice(fontCard, "page/zoom", tr("Масштаб страниц"), zooms, prefs.zoomPercent);
    addChoice(fontCard, "page/fontSize", tr("Размер шрифта"),
        {{tr("Очень мелкий"), 12}, {tr("Мелкий"), 14}, {tr("Средний"), 16}, {tr("Крупный"), 20}, {tr("Огромный"), 24}}, prefs.fontSize);
    addChoice(fontCard, "page/minFontSize", tr("Минимальный размер шрифта"),
        {{tr("Не ограничен"), 0}, {"9", 9}, {"12", 12}, {"14", 14}, {"16", 16}}, prefs.minimumFontSize);

    auto *downloads = addSection("downloads", tr("Загрузки"), "download");
    auto *downloadCard = addCard(downloads);
    auto *folderRow = new QWidget(this);
    auto *folderLayout = new QHBoxLayout(folderRow);
    folderLayout->setContentsMargins(0, 8, 0, 8);
    auto *folderText = new QVBoxLayout;
    folderText->setSpacing(2);
    folderText->addWidget(new QLabel(tr("Папка загрузок"), folderRow));
    auto *folder = makeLabel(QDir::toNativeSeparators(prefs.downloadFolder), "rowNote", folderRow);
    folder->setObjectName("downloads_folder");
    folderText->addWidget(folder);
    folderLayout->addLayout(folderText, 1);
    auto *pick = new QPushButton(tr("Изменить"), folderRow);
    pick->setObjectName("secondaryButton");
    pick->setCursor(Qt::PointingHandCursor);
    folderLayout->addWidget(pick);
    addRow(downloadCard, folderRow);
    connect(pick, &QPushButton::clicked, this, [this, folder] {
        const auto path = QFileDialog::getExistingDirectory(this, tr("Папка загрузок"), QDir::fromNativeSeparators(folder->text()));
        if (path.isEmpty()) return;
        folder->setText(QDir::toNativeSeparators(path));
        store("downloads/folder", path);
    });
    addToggle(downloadCard, "downloads/ask", tr("Спрашивать, куда сохранять каждый файл"), prefs.askDownloadLocation);

    const std::tuple<QString, QString, QString> libraryChoices[]{{"history", tr("История"), "history"}, {"bookmarks", tr("Закладки"), "bookmark"}};
    for (const auto &[key, heading, icon] : libraryChoices) {
        auto *library = addSection(key, heading, icon, false);
        auto *bar = new QHBoxLayout;
        bar->setSpacing(10);
        auto *search = new QLineEdit(this);
        search->setObjectName(key + "Search");
        search->setPlaceholderText(key == "history" ? tr("Искать в истории") : tr("Искать в закладках"));
        search->setClearButtonEnabled(false);
        auto *glass = search->addAction(neoIcon("search", muted), QLineEdit::LeadingPosition);
        glass->setProperty("neoIcon", "search");
        bar->addWidget(search, 1);
        if (key == "history") {
            auto *wipe = new QPushButton(tr("Очистить историю"), this);
            wipe->setObjectName("secondaryButton");
            wipe->setCursor(Qt::PointingHandCursor);
            bar->addWidget(wipe);
            connect(wipe, &QPushButton::clicked, this, [this] {
                if (!askYesNo(this, tr("Очистить историю"), tr("Удалить всю историю просмотров?"))) return;
                this->settings.remove("history");
                this->settings.sync();
                this->profile->clearAllVisitedLinks();
                fillLibrary("history");
            });
        }
        library->addLayout(bar);
        auto *list = new QListWidget(this);
        list->setObjectName("libraryList");
        list->setProperty("library", key);
        list->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
        list->setSelectionMode(QAbstractItemView::NoSelection);
        list->setFocusPolicy(Qt::NoFocus);
        list->setVerticalScrollMode(QAbstractItemView::ScrollPerPixel);
        list->setMouseTracking(true);
        library->addWidget(list, 1);
        libraries.insert(key, list);
        librarySearch.insert(key, search);
        connect(search, &QLineEdit::textChanged, this, [this, key = key] { fillLibrary(key); });
        connect(list, &QListWidget::itemClicked, this, [this](QListWidgetItem *item) {
            const auto url = item->data(Qt::UserRole).toString();
            if (!url.isEmpty()) emit openUrl(QUrl(url));
        });
    }

    auto *transfer = addSection("import", tr("Перенос данных"), "transfer");
    auto *importCard = addCard(transfer);
    auto *importer = new ImportWidget(this);
    importer->setContentsMargins(0, 14, 0, 16);
    importCard->addWidget(importer);

    auto *about = addSection("about", tr("О NeoSerf"), "info");
    auto *aboutCard = addCard(about);
    auto *brandRow = new QWidget(this);
    auto *brand = new QHBoxLayout(brandRow);
    brand->setContentsMargins(0, 16, 0, 16);
    brand->setSpacing(16);
    auto *logo = new QLabel(brandRow);
    QPixmap mark(96, 96);
    mark.fill(Qt::transparent);
    QPainter painter(&mark);
    QSvgRenderer(QStringLiteral(":/assets/logo.svg")).render(&painter);
    painter.end();
    mark.setDevicePixelRatio(2);
    logo->setPixmap(mark);
    brand->addWidget(logo);
    auto *versions = new QVBoxLayout;
    versions->setSpacing(2);
    auto *name = makeLabel("NeoSerf " + QCoreApplication::applicationVersion(), "aboutName", brandRow);
    versions->addWidget(name);
    versions->addWidget(makeLabel(tr("Qt %1 · Chromium %2").arg(QString::fromLatin1(qVersion()),
        QString::fromLatin1(qWebEngineChromiumVersion())), "rowNote", brandRow));
    brand->addLayout(versions, 1);
    aboutCard->addWidget(brandRow);
    aboutCard->addWidget(makeLabel(tr("Это настольная версия. Облачная синхронизация, хранилище паролей и блокировщик рекламы "
        "пока доступны только в Android-версии."), "rowNote", this));
    aboutCard->addSpacing(12);
    auto *resetCard = addCard(about, tr("Сброс настроек"));
    auto *resetRow = new QWidget(this);
    auto *resetLayout = new QHBoxLayout(resetRow);
    resetLayout->setContentsMargins(0, 10, 0, 10);
    resetLayout->addWidget(makeLabel(tr("Вернуть настройки по умолчанию. Закладки и история останутся."), "rowNote", resetRow), 1);
    auto *restore = new QPushButton(tr("Сбросить"), resetRow);
    restore->setObjectName("resetSettings");
    restore->setCursor(Qt::PointingHandCursor);
    resetLayout->addWidget(restore);
    addRow(resetCard, resetRow);
    connect(restore, &QPushButton::clicked, this, [this] {
        if (!askYesNo(this, tr("Сбросить настройки"),
                tr("Вернуть все настройки к значениям по умолчанию? Закладки и история останутся."))) return;
        for (const auto &key : Preferences::keys()) this->settings.remove(key);
        this->settings.sync();
        emit changed();
        emit resetDone();
    });

    navLayout->addStretch();
    indicator->lower();
    showSection("appearance", false);
}

QVBoxLayout *SettingsPage::addSection(const QString &id, const QString &title, const QString &icon, bool scrollable)
{
    auto *item = new QToolButton(navLayout->parentWidget());
    item->setObjectName("settingsNavItem");
    item->setText("  " + title); // the style puts the text right against the icon otherwise
    item->setAccessibleName(title);
    item->setProperty("neoIcon", icon);
    item->setIcon(neoIcon(icon, palette().color(QPalette::Text)));
    item->setIconSize(QSize(20, 20));
    item->setToolButtonStyle(Qt::ToolButtonTextBesideIcon);
    item->setCheckable(true);
    item->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
    item->setCursor(Qt::PointingHandCursor);
    navigation->addButton(item, ids.size());
    navLayout->addWidget(item);
    connect(item, &QToolButton::clicked, this, [this, id] { showSection(id); });
    ids.append(id);
    titles.append(title);

    auto *page = new QWidget;
    page->setObjectName("settingsContent");
    auto *outer = new QHBoxLayout(page);
    outer->setContentsMargins(40, 26, 40, scrollable ? 40 : 24);
    auto *columnWidget = new QWidget(page);
    columnWidget->setMaximumWidth(780);
    auto *column = new QVBoxLayout(columnWidget);
    column->setContentsMargins(0, 0, 0, 0);
    column->setSpacing(10);
    outer->addWidget(columnWidget, 1);
    outer->addStretch(0);
    column->addWidget(makeLabel(title, "sectionTitle", columnWidget));
    column->addSpacing(6);
    if (scrollable) {
        column->setAlignment(Qt::AlignTop);
        auto *scroll = new QScrollArea(sections);
        scroll->setObjectName("settingsScroll");
        scroll->setFrameShape(QFrame::NoFrame);
        scroll->setWidgetResizable(true);
        scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
        scroll->setWidget(page);
        sections->addWidget(scroll);
    } else sections->addWidget(page);
    return column;
}

QVBoxLayout *SettingsPage::addCard(QVBoxLayout *section, const QString &heading)
{
    if (!heading.isEmpty()) {
        section->addSpacing(10);
        section->addWidget(makeLabel(heading, "cardHeading", this));
    }
    auto *card = new QFrame(this);
    card->setObjectName("settingsCard");
    auto *layout = new QVBoxLayout(card);
    layout->setContentsMargins(20, 4, 20, 4);
    layout->setSpacing(0);
    section->addWidget(card);
    return layout;
}

void SettingsPage::addRow(QVBoxLayout *card, QWidget *row)
{
    if (card->count() > 0) {
        auto *divider = new QFrame(this);
        divider->setObjectName("rowDivider");
        divider->setFixedHeight(1);
        card->addWidget(divider);
    }
    card->addWidget(row);
}

QComboBox *SettingsPage::addChoice(QVBoxLayout *card, const QString &key, const QString &label, const Choices &items, const QVariant &value)
{
    auto *row = new QWidget(this);
    row->setMinimumHeight(58);
    auto *layout = new QHBoxLayout(row);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->addWidget(new QLabel(label, row), 1);
    auto *combo = new NeoComboBox(row);
    combo->setFixedWidth(260);
    combo->setCursor(Qt::PointingHandCursor);
    for (const auto &item : items) combo->addItem(item.first, item.second);
    select(combo, value);
    layout->addWidget(combo);
    addRow(card, row);
    if (!key.isEmpty()) {
        combo->setObjectName(QString(key).replace('/', '_'));
        connect(combo, &QComboBox::currentIndexChanged, this, [this, key, combo] { store(key, combo->currentData()); });
    }
    return combo;
}

void SettingsPage::addToggle(QVBoxLayout *card, const QString &key, const QString &text, bool value)
{
    auto *toggle = new ToggleSwitch(text, this);
    toggle->setObjectName(QString(key).replace('/', '_'));
    toggle->setChecked(value);
    toggle->setMinimumHeight(52);
    addRow(card, toggle);
    connect(toggle, &QCheckBox::toggled, this, [this, key](bool on) { store(key, on); });
}

void SettingsPage::store(const QString &key, const QVariant &value)
{
    settings.setValue(key, value);
    settings.sync();
    emit changed();
}

void SettingsPage::storeMail()
{
    const bool custom = mailProvider->currentData() == "custom";
    const auto value = custom ? mailAddress->text().trimmed() : mailProvider->currentData().toString();
    if (!validMailUrl(QUrl(value))) {
        mailError->setText(tr("Введите полный HTTPS-адрес почтового сервиса."));
        mailError->show();
        return;
    }
    mailError->hide();
    if (custom) mailAddress->setText(value);
    if (settings.value("mail/url").toString() != value) store("mail/url", value);
}

void SettingsPage::showSection(const QString &id, bool animated)
{
    const int index = ids.indexOf(id);
    if (index < 0) return;
    navigation->button(index)->setChecked(true);
    if (libraries.contains(id)) fillLibrary(id);
    if (animated && isVisible()) fadeToPage(sections, index);
    else sections->setCurrentIndex(index);
    moveIndicator(animated && isVisible());
    emit sectionChanged(id);
}

QString SettingsPage::currentSection() const { return ids.value(sections->currentIndex()); }
QString SettingsPage::sectionTitle() const { return titles.value(sections->currentIndex()); }

void SettingsPage::moveIndicator(bool animated)
{
    auto *selected = navigation->checkedButton();
    if (!selected) return;
    const auto target = selected->geometry();
    indicator->show();
    if (!animated || indicator->geometry().isEmpty()) { indicator->setGeometry(target); return; }
    auto *slide = new QPropertyAnimation(indicator, "geometry", indicator);
    slide->setDuration(240);
    slide->setEasingCurve(QEasingCurve::OutCubic);
    slide->setEndValue(target);
    slide->start(QAbstractAnimation::DeleteWhenStopped);
}

void SettingsPage::showEvent(QShowEvent *event)
{
    QWidget::showEvent(event);
    if (libraries.contains(currentSection())) fillLibrary(currentSection());
    moveIndicator(false);
}

void SettingsPage::resizeEvent(QResizeEvent *event)
{
    QWidget::resizeEvent(event);
    moveIndicator(false);
}

void SettingsPage::fillLibrary(const QString &key)
{
    auto *list = libraries.value(key);
    list->clear();
    const auto entries = settings.value(key).toList();
    const auto filter = librarySearch.value(key)->text().trimmed();
    const auto color = palette().color(QPalette::Text);
    const auto locale = QLocale();
    const auto today = QDate::currentDate();
    QString group;
    auto header = [list](const QString &text) {
        auto *item = new QListWidgetItem(list);
        item->setFlags(Qt::NoItemFlags);
        auto *label = makeLabel(text, "libraryHeader", list);
        item->setSizeHint(QSize(0, 40));
        list->setItemWidget(item, label);
    };
    int shown = 0;
    for (int i = 0; i < entries.size(); ++i) {
        const auto entry = entries.at(i).toMap();
        const auto url = entry.value("url").toString();
        const auto title = entry.value("title").toString().trimmed();
        if (!filter.isEmpty() && !title.contains(filter, Qt::CaseInsensitive) && !url.contains(filter, Qt::CaseInsensitive)) continue;
        const auto time = entry.contains("time") ? QDateTime::fromSecsSinceEpoch(entry.value("time").toLongLong()) : QDateTime();
        if (key == "history") {
            QString day = tr("Ранее");
            if (time.isValid()) {
                const auto date = time.date();
                day = date == today ? tr("Сегодня") : date == today.addDays(-1) ? tr("Вчера") : locale.toString(date, "d MMMM yyyy");
            }
            if (day != group) { header(day); group = day; }
        }
        auto *item = new QListWidgetItem(list);
        item->setData(Qt::UserRole, url);
        item->setToolTip(url);
        auto *row = new QWidget(list);
        row->setObjectName("libraryRow");
        row->setAttribute(Qt::WA_StyledBackground);
        row->setCursor(Qt::PointingHandCursor);
        auto *layout = new QHBoxLayout(row);
        layout->setContentsMargins(14, 8, 8, 8);
        layout->setSpacing(14);
        auto *icon = new QLabel(row);
        icon->setPixmap(neoIcon("globe", color).pixmap(18, 18));
        layout->addWidget(icon);
        auto *text = new QVBoxLayout;
        text->setSpacing(1);
        auto *name = new QLabel(row);
        name->setObjectName("libraryTitle");
        name->setText(name->fontMetrics().elidedText(title.isEmpty() ? url : title, Qt::ElideRight, 560));
        auto *address = new QLabel(row);
        address->setObjectName("libraryUrl");
        address->setText(address->fontMetrics().elidedText(QUrl(url).host(), Qt::ElideMiddle, 560));
        text->addWidget(name);
        text->addWidget(address);
        layout->addLayout(text, 1);
        if (time.isValid()) layout->addWidget(makeLabel(locale.toString(time.time(), "HH:mm"), "libraryTime", row));
        auto *remove = new QToolButton(row);
        remove->setObjectName("libraryRemove");
        remove->setProperty("neoIcon", "close");
        remove->setIcon(neoIcon("close", color));
        remove->setIconSize(QSize(14, 14));
        remove->setToolTip(tr("Удалить"));
        remove->setCursor(Qt::PointingHandCursor);
        layout->addWidget(remove);
        connect(remove, &QToolButton::clicked, this, [this, key, i, url] {
            auto stored = settings.value(key).toList();
            if (i < stored.size() && stored.at(i).toMap().value("url").toString() == url) stored.removeAt(i);
            settings.setValue(key, stored);
            settings.sync();
            QMetaObject::invokeMethod(this, [this, key] { fillLibrary(key); }, Qt::QueuedConnection);
        });
        item->setSizeHint(QSize(0, row->sizeHint().height()));
        list->setItemWidget(item, row);
        ++shown;
    }
    if (shown == 0) {
        auto *item = new QListWidgetItem(list);
        item->setFlags(Qt::NoItemFlags);
        const auto empty = !filter.isEmpty() ? tr("Ничего не найдено")
            : key == "history" ? tr("История пока пуста") : tr("Сохранённых страниц пока нет");
        auto *label = makeLabel(empty, "libraryEmpty", list);
        label->setAlignment(Qt::AlignCenter);
        item->setSizeHint(QSize(0, 120));
        list->setItemWidget(item, label);
    }
}
