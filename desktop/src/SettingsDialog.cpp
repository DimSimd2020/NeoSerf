#include "SettingsDialog.h"
#include "ImportWidget.h"
#include "Preferences.h"
#include "UiAssets.h"
#include <QApplication>
#include <QCheckBox>
#include <QComboBox>
#include <QDialogButtonBox>
#include <QFileDialog>
#include <QFormLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QPushButton>
#include <QSettings>
#include <QStackedWidget>
#include <QVBoxLayout>
#include <QWebEngineCookieStore>
#include <QWebEngineProfile>
#include <QtWebEngineCore/qtwebenginecoreglobal.h>

namespace {
using Choices = QList<QPair<QString, QVariant>>;

void select(QComboBox *combo, const QVariant &value)
{
    for (int i = 0; i < combo->count(); ++i)
        if (combo->itemData(i).toString() == value.toString()) { combo->setCurrentIndex(i); return; }
    combo->setCurrentIndex(0);
}
}

SettingsDialog::SettingsDialog(QSettings &settings, QWebEngineProfile *profile, QWidget *parent)
    : QDialog(parent), settings(settings), profile(profile)
{
    setWindowTitle(tr("Настройки"));
    setMinimumSize(780, 540);
    const auto prefs = Preferences::load(settings);
    auto *root = new QVBoxLayout(this);
    root->setContentsMargins(20, 20, 20, 16);
    auto *body = new QHBoxLayout;
    body->setSpacing(20);
    auto *nav = new QListWidget(this);
    nav->setObjectName("settingsNav");
    nav->setFixedWidth(200);
    auto *stack = new QStackedWidget(this);
    body->addWidget(nav);
    body->addWidget(stack, 1);
    root->addLayout(body, 1);

    auto page = [&](const QString &title) {
        auto *widget = new QWidget(stack);
        auto *outer = new QVBoxLayout(widget);
        outer->setContentsMargins(0, 0, 0, 0);
        auto *heading = new QLabel(title, widget);
        heading->setObjectName("welcomeSection");
        outer->addWidget(heading);
        auto *form = new QFormLayout;
        form->setSpacing(14);
        form->setFieldGrowthPolicy(QFormLayout::AllNonFixedFieldsGrow);
        outer->addLayout(form);
        outer->addStretch();
        nav->addItem(title);
        stack->addWidget(widget);
        return form;
    };
    auto checkbox = [&](QFormLayout *form, const QString &key, const QString &text, bool value) {
        auto *box = new QCheckBox(text, this);
        box->setObjectName(QString(key).replace('/', '_'));
        box->setChecked(value);
        fields.insert(key, box);
        form->addRow(box);
        return box;
    };
    auto choice = [&](QFormLayout *form, const QString &key, const QString &label, const Choices &items, const QVariant &value) {
        auto *combo = new QComboBox(this);
        combo->setObjectName(QString(key).replace('/', '_'));
        for (const auto &item : items) combo->addItem(item.first, item.second);
        select(combo, value);
        fields.insert(key, combo);
        form->addRow(label, combo);
        return combo;
    };
    auto note = [&](QFormLayout *form, const QString &text) {
        auto *label = new QLabel(text, this);
        label->setObjectName("welcomeDetail");
        label->setWordWrap(true);
        form->addRow(label);
    };
    const auto iconColor = palette().color(QPalette::Text);

    auto *look = page(tr("Оформление"));
    auto *theme = choice(look, "appearance/theme", tr("Тема"), {{tr("Как на устройстве"), "system"}, {tr("Светлая"), "light"}, {tr("Тёмная"), "dark"}},
        settings.value("appearance/theme", "system"));
    theme->setObjectName("themeSelector");
    const QStringList themeIcons{"device", "sun", "moon"};
    for (int i = 0; i < themeIcons.size(); ++i) theme->setItemIcon(i, neoIcon(themeIcons.at(i), iconColor));
    auto *language = choice(look, "appearance/language", tr("Язык интерфейса"),
        {{tr("Как на устройстве"), "system"}, {"Русский", "ru"}, {"English", "en"}}, settings.value("appearance/language", "system"));
    language->setObjectName("languageSelector");
    note(look, tr("Язык изменится после перезапуска NeoSerf."));
    checkbox(look, "ui/showHome", tr("Показывать кнопку «Главная»"), prefs.showHomeButton);
    checkbox(look, "ui/showMail", tr("Показывать кнопку «Почта»"), prefs.showMailButton);
    checkbox(look, "weather/enabled", tr("Погода на главной"), settings.value("weather/enabled", true).toBool());
    note(look, tr("Регион определяется по IP-адресу. VPN может изменить регион.\nДанные: Open-Meteo. Геолокация: ipwho.is."));

    auto *searchPage = page(tr("Поиск и почта"));
    const QStringList engines{"Google", "DuckDuckGo", QString::fromUtf8("Яндекс"), "Bing", "Brave"};
    Choices engineChoices;
    for (const auto &name : engines) engineChoices.append({name == QString::fromUtf8("Яндекс") ? tr("Яндекс") : name, name});
    choice(searchPage, "search/engine", tr("Поисковая система"), engineChoices, settings.value("search/engine", "Google"));
    mailProvider = new QComboBox(this);
    mailProvider->setObjectName("mailProvider");
    mailProvider->addItem("Gmail", "https://mail.google.com/");
    mailProvider->addItem(tr("Яндекс Почта"), "https://mail.yandex.ru/");
    mailProvider->addItem("Outlook", "https://outlook.live.com/mail/");
    mailProvider->addItem("Mail.ru", "https://e.mail.ru/");
    mailProvider->addItem(tr("Другой сервис"), "custom");
    const auto configuredMail = mailUrl(settings).toString();
    const auto known = mailProvider->findData(configuredMail);
    mailProvider->setCurrentIndex(known < 0 ? mailProvider->count() - 1 : known);
    mailAddress = new QLineEdit(configuredMail, this);
    mailAddress->setObjectName("mailAddress");
    mailAddress->setPlaceholderText("https://mail.example.com/");
    mailError = new QLabel(this);
    mailError->setWordWrap(true);
    searchPage->addRow(tr("Почта по умолчанию"), mailProvider);
    searchPage->addRow(tr("Адрес почты"), mailAddress);
    searchPage->addRow(mailError);
    auto syncMail = [this] { mailAddress->setVisible(mailProvider->currentData() == "custom"); };
    connect(mailProvider, &QComboBox::currentIndexChanged, this, syncMail);
    syncMail();

    auto *startup = page(tr("При запуске"));
    choice(startup, "startup/mode", tr("Открывать"), {{tr("Вкладки с прошлого сеанса"), "restore"}, {tr("Новую вкладку с поиском"), "home"}}, prefs.startup);

    auto *privacy = page(tr("Конфиденциальность"));
    checkbox(privacy, "privacy/keepCookies", tr("Сохранять cookie между запусками"), prefs.keepCookies);
    checkbox(privacy, "privacy/blockThirdPartyCookies", tr("Блокировать сторонние cookie"), prefs.blockThirdPartyCookies);
    checkbox(privacy, "privacy/clearHistoryOnExit", tr("Очищать историю при выходе"), prefs.clearHistoryOnExit);
    auto *clear = new QPushButton(tr("Очистить данные просмотра…"), this);
    clear->setObjectName("clearData");
    privacy->addRow(clear);
    connect(clear, &QPushButton::clicked, this, &SettingsDialog::clearBrowsingData);
    note(privacy, tr("Вкладки инкогнито ничего не сохраняют: ни историю, ни cookie."));

    const Choices permission{{tr("Спрашивать"), "ask"}, {tr("Блокировать"), "block"}};
    auto *sites = page(tr("Сайты"));
    checkbox(sites, "site/javascript", tr("Разрешить JavaScript"), prefs.javascript);
    checkbox(sites, "site/images", tr("Показывать изображения"), prefs.images);
    checkbox(sites, "site/popups", tr("Разрешить всплывающие окна без нажатия"), prefs.popups);
    checkbox(sites, "site/autoplay", tr("Автовоспроизведение медиа"), prefs.autoplay);
    checkbox(sites, "site/smoothScroll", tr("Плавная прокрутка"), prefs.smoothScroll);
    checkbox(sites, "site/forceDark", tr("Тёмное оформление сайтов"), prefs.forceDark);
    choice(sites, "permissions/geolocation", tr("Местоположение"), permission, prefs.geolocation);
    choice(sites, "permissions/camera", tr("Камера"), permission, prefs.camera);
    choice(sites, "permissions/microphone", tr("Микрофон"), permission, prefs.microphone);

    auto *fonts = page(tr("Шрифты и масштаб"));
    Choices zooms;
    for (int percent : {50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300}) zooms.append({QString("%1%").arg(percent), percent});
    choice(fonts, "page/zoom", tr("Масштаб страниц"), zooms, prefs.zoomPercent);
    choice(fonts, "page/fontSize", tr("Размер шрифта"),
        {{tr("Очень мелкий"), 12}, {tr("Мелкий"), 14}, {tr("Средний"), 16}, {tr("Крупный"), 20}, {tr("Огромный"), 24}}, prefs.fontSize);
    choice(fonts, "page/minFontSize", tr("Минимальный размер шрифта"),
        {{tr("Не ограничен"), 0}, {"9", 9}, {"12", 12}, {"14", 14}, {"16", 16}}, prefs.minimumFontSize);

    auto *downloads = page(tr("Загрузки"));
    checkbox(downloads, "downloads/ask", tr("Спрашивать, куда сохранять файл"), prefs.askDownloadLocation);
    auto *folder = new QLineEdit(prefs.downloadFolder, this);
    folder->setObjectName("downloads_folder");
    folder->setReadOnly(true);
    fields.insert("downloads/folder", folder);
    auto *pick = new QPushButton(tr("Выбрать…"), this);
    auto *folderRow = new QHBoxLayout;
    folderRow->addWidget(folder, 1);
    folderRow->addWidget(pick);
    downloads->addRow(tr("Папка загрузок"), folderRow);
    connect(pick, &QPushButton::clicked, this, [this, folder] {
        const auto path = QFileDialog::getExistingDirectory(this, tr("Папка загрузок"), folder->text());
        if (!path.isEmpty()) folder->setText(path);
    });

    auto *transfer = page(tr("Перенос данных"));
    transfer->addRow(new ImportWidget(this));

    auto *about = page(tr("О NeoSerf"));
    note(about, tr("NeoSerf %1\nQt %2 · Chromium %3").arg(QCoreApplication::applicationVersion(), QString::fromLatin1(qVersion()),
        QString::fromLatin1(qWebEngineChromiumVersion())));
    note(about, tr("Это настольная версия. Облачная синхронизация, хранилище паролей и блокировщик рекламы пока доступны только в Android-версии."));

    nav->setCurrentRow(0);
    connect(nav, &QListWidget::currentRowChanged, stack, &QStackedWidget::setCurrentIndex);

    auto *buttons = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, this);
    localizeButtons(buttons);
    auto *restore = buttons->addButton(tr("Сбросить настройки"), QDialogButtonBox::ResetRole);
    restore->setObjectName("resetSettings");
    root->addWidget(buttons);
    connect(restore, &QPushButton::clicked, this, &SettingsDialog::resetSettings);
    connect(buttons, &QDialogButtonBox::rejected, this, &QDialog::reject);
    connect(buttons, &QDialogButtonBox::accepted, this, [this] {
        const auto value = mailProvider->currentData() == "custom" ? mailAddress->text().trimmed() : mailProvider->currentData().toString();
        if (!validMailUrl(QUrl(value))) {
            mailError->setText(tr("Введите полный HTTPS-адрес почтового сервиса."));
            mailAddress->setFocus();
            return;
        }
        accept();
    });
}

void SettingsDialog::save()
{
    for (auto it = fields.cbegin(); it != fields.cend(); ++it) {
        if (auto *box = qobject_cast<QCheckBox *>(it.value())) settings.setValue(it.key(), box->isChecked());
        else if (auto *combo = qobject_cast<QComboBox *>(it.value())) settings.setValue(it.key(), combo->currentData());
        else if (auto *line = qobject_cast<QLineEdit *>(it.value())) settings.setValue(it.key(), line->text());
    }
    settings.setValue("mail/url", mailProvider->currentData() == "custom" ? mailAddress->text().trimmed() : mailProvider->currentData().toString());
}

void SettingsDialog::clearBrowsingData()
{
    QDialog dialog(this);
    dialog.setWindowTitle(tr("Очистить данные просмотра"));
    auto *layout = new QVBoxLayout(&dialog);
    layout->setContentsMargins(24, 24, 24, 24);
    layout->setSpacing(12);
    auto *history = new QCheckBox(tr("История"), &dialog);
    auto *bookmarks = new QCheckBox(tr("Закладки"), &dialog);
    auto *cookies = new QCheckBox(tr("Файлы cookie"), &dialog);
    auto *cache = new QCheckBox(tr("Кэш изображений и файлов"), &dialog);
    history->setChecked(true);
    cookies->setChecked(true);
    cache->setChecked(true);
    for (auto *box : {history, bookmarks, cookies, cache}) layout->addWidget(box);
    auto *buttons = new QDialogButtonBox(QDialogButtonBox::Cancel, &dialog);
    localizeButtons(buttons);
    auto *confirm = buttons->addButton(tr("Очистить"), QDialogButtonBox::AcceptRole);
    confirm->setObjectName("confirmClear");
    layout->addWidget(buttons);
    connect(buttons, &QDialogButtonBox::accepted, &dialog, &QDialog::accept);
    connect(buttons, &QDialogButtonBox::rejected, &dialog, &QDialog::reject);
    if (dialog.exec() != QDialog::Accepted) return;
    if (history->isChecked()) { settings.remove("history"); profile->clearAllVisitedLinks(); }
    if (bookmarks->isChecked()) settings.remove("bookmarks");
    if (cookies->isChecked()) profile->cookieStore()->deleteAllCookies();
    if (cache->isChecked()) profile->clearHttpCache();
    settings.sync();
    showInfo(this, tr("Очистить данные просмотра"), tr("Выбранные данные удалены."));
}

void SettingsDialog::resetSettings()
{
    if (!askYesNo(this, tr("Сбросить настройки"),
            tr("Вернуть все настройки к значениям по умолчанию? Закладки и история останутся."))) return;
    for (const auto &key : Preferences::keys()) settings.remove(key);
    reset = true;
    accept();
}
