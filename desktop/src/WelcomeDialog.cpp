#include "WelcomeDialog.h"
#include "ImportWidget.h"
#include "Language.h"
#include "UiAssets.h"
#include <QButtonGroup>
#include <QComboBox>
#include <QEvent>
#include <QFormLayout>
#include <QGraphicsOpacityEffect>
#include <QGridLayout>
#include <QGuiApplication>
#include <QHBoxLayout>
#include <QLabel>
#include <QPainter>
#include <QParallelAnimationGroup>
#include <QPropertyAnimation>
#include <QPushButton>
#include <QSettings>
#include <QStackedWidget>
#include <QStyle>
#include <QStyleHints>
#include <QSvgRenderer>
#include <QToolButton>
#include <QVBoxLayout>

namespace {
const char *const yandex = "Яндекс";
const char *const yandexMailUrl = "https://mail.yandex.ru/";
}

bool WelcomeDialog::needed() { return !QSettings().value("onboarding/completed", false).toBool(); }

WelcomeDialog::WelcomeDialog(Language &language, QWidget *parent) : QDialog(parent)
{
    Q_INIT_RESOURCE(resources);
    setObjectName("welcomeDialog");
    setWindowIcon(QIcon(":/assets/logo.svg"));
    setWindowFlag(Qt::WindowContextHelpButtonHint, false);
    setMinimumWidth(680);
    const QSettings settings;
    auto *root = new QVBoxLayout(this);
    root->setContentsMargins(44, 28, 44, 24);
    root->setSpacing(18);
    pages = new QStackedWidget(this);
    pages->setObjectName("welcomePages");
    root->addWidget(pages, 1);

    // Page 1: look and language, then import.
    auto *intro = new QWidget(pages);
    auto *layout = new QVBoxLayout(intro);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(16);
    auto *logo = new QLabel(intro);
    QPixmap mark(144, 144);
    mark.fill(Qt::transparent);
    QPainter painter(&mark);
    QSvgRenderer renderer(QStringLiteral(":/assets/logo.svg"));
    renderer.render(&painter);
    painter.end();
    mark.setDevicePixelRatio(2);
    logo->setPixmap(mark);
    layout->addWidget(logo, 0, Qt::AlignHCenter);
    title = new QLabel(intro);
    title->setObjectName("welcomeTitle");
    title->setAlignment(Qt::AlignCenter);
    subtitle = new QLabel(intro);
    subtitle->setObjectName("welcomeDetail");
    subtitle->setAlignment(Qt::AlignCenter);
    layout->addWidget(title);
    layout->addWidget(subtitle);
    layout->addSpacing(8);
    auto *form = new QFormLayout;
    form->setSpacing(14);
    appearanceLabel = new QLabel(intro);
    languageLabel = new QLabel(intro);
    theme = new QComboBox(intro);
    theme->setObjectName("themeSelector");
    theme->addItem(QString(), "system");
    theme->addItem(QString(), "light");
    theme->addItem(QString(), "dark");
    theme->setCurrentIndex(qMax(0, theme->findData(settings.value("appearance/theme", "system"))));
    languages = new QComboBox(intro);
    languages->setObjectName("languageSelector");
    languages->addItem(QString(), "system");
    languages->addItem("Русский", "ru");
    languages->addItem("English", "en");
    languages->setCurrentIndex(qMax(0, languages->findData(settings.value("appearance/language", "system"))));
    appearanceLabel->setBuddy(theme);
    languageLabel->setBuddy(languages);
    form->addRow(appearanceLabel, theme);
    form->addRow(languageLabel, languages);
    layout->addLayout(form);
    layout->addSpacing(8);
    importLabel = new QLabel(intro);
    importLabel->setObjectName("welcomeSection");
    layout->addWidget(importLabel);
    importer = new ImportWidget(intro);
    layout->addWidget(importer);
    layout->addStretch();
    footnote = new QLabel(intro);
    footnote->setObjectName("welcomeDetail");
    footnote->setAlignment(Qt::AlignCenter);
    layout->addWidget(footnote);
    pages->addWidget(intro);

    // Page 2: default search engine and mail.
    auto *services = new QWidget(pages);
    auto *serviceLayout = new QVBoxLayout(services);
    serviceLayout->setContentsMargins(0, 12, 0, 0);
    serviceLayout->setSpacing(12);
    serviceLayout->addStretch(1);
    servicesTitle = new QLabel(services);
    servicesTitle->setObjectName("welcomeTitle");
    servicesTitle->setAlignment(Qt::AlignCenter);
    servicesSubtitle = new QLabel(services);
    servicesSubtitle->setObjectName("welcomeDetail");
    servicesSubtitle->setAlignment(Qt::AlignCenter);
    serviceLayout->addWidget(servicesTitle);
    serviceLayout->addWidget(servicesSubtitle);
    serviceLayout->addSpacing(14);
    engineLabel = new QLabel(services);
    engineLabel->setObjectName("importHeading");
    serviceLayout->addWidget(engineLabel);
    auto *engineGrid = new QGridLayout;
    engineGrid->setSpacing(8);
    serviceLayout->addLayout(engineGrid);
    engines = new QButtonGroup(this);
    for (const auto &name : {QString("Google"), QString::fromUtf8(yandex), QString("DuckDuckGo"), QString("Bing"), QString("Brave")}) {
        auto *card = addChoice(engineGrid, engines, name, 3);
        card->setText(name);
        card->setObjectName("engineChoice");
        if (name == QString::fromUtf8(yandex)) yandexEngine = card;
        card->setChecked(name == settings.value("search/engine", "Google").toString());
    }
    serviceLayout->addSpacing(12);
    mailLabel = new QLabel(services);
    mailLabel->setObjectName("importHeading");
    serviceLayout->addWidget(mailLabel);
    auto *mailGrid = new QGridLayout;
    mailGrid->setSpacing(8);
    serviceLayout->addLayout(mailGrid);
    mails = new QButtonGroup(this);
    const auto configuredMail = mailUrl(settings).toString();
    const QList<QPair<QString, QString>> mailServices{{"Gmail", "https://mail.google.com/"}, {QString(), yandexMailUrl},
        {"Outlook", "https://outlook.live.com/mail/"}, {"Mail.ru", "https://e.mail.ru/"}};
    for (const auto &service : mailServices) {
        auto *card = addChoice(mailGrid, mails, service.second, 2);
        card->setText(service.first);
        card->setObjectName("mailChoice");
        if (service.second == yandexMailUrl) yandexMail = card;
        card->setChecked(service.second == configuredMail);
    }
    if (!mails->checkedButton()) mails->buttons().first()->setChecked(true);
    if (!engines->checkedButton()) engines->buttons().first()->setChecked(true);
    serviceLayout->addStretch(1);
    servicesNote = new QLabel(services);
    servicesNote->setObjectName("welcomeDetail");
    servicesNote->setAlignment(Qt::AlignCenter);
    serviceLayout->addWidget(servicesNote);
    pages->addWidget(services);

    // Navigation: back, step dots, primary action.
    auto *navigation = new QHBoxLayout;
    back = new QPushButton(this);
    back->setObjectName("welcomeBack");
    back->setProperty("neoIcon", "back");
    back->setIcon(neoIcon("back", palette().color(QPalette::Text)));
    back->setMinimumWidth(140);
    auto policy = back->sizePolicy();
    policy.setRetainSizeWhenHidden(true);
    back->setSizePolicy(policy);
    navigation->addWidget(back);
    navigation->addStretch();
    for (int i = 0; i < pages->count(); ++i) {
        auto *dot = new QLabel(this);
        dot->setObjectName("stepDot");
        dots.append(dot);
        navigation->addWidget(dot, 0, Qt::AlignVCenter);
    }
    navigation->addStretch();
    primary = new QPushButton(this);
    primary->setObjectName("welcomeFinish");
    primary->setDefault(true);
    primary->setAutoDefault(true);
    primary->setMinimumSize(220, 46);
    primary->setCursor(Qt::PointingHandCursor);
    navigation->addWidget(primary);
    root->addLayout(navigation);

    connect(theme, &QComboBox::currentIndexChanged, this, [this] { applyUiTheme(this, theme->currentData().toString()); });
    connect(QGuiApplication::styleHints(), &QStyleHints::colorSchemeChanged, this, [this] {
        if (theme->currentData() == "system") applyUiTheme(this, "system");
    });
    connect(languages, &QComboBox::currentIndexChanged, this, [this, &language] { language.apply(languages->currentData().toString()); retranslate(); });
    connect(importer, &ImportWidget::busyChanged, this, [this](bool busy) {
        primary->setEnabled(!busy);
        languages->setEnabled(!busy);
    });
    connect(back, &QPushButton::clicked, this, [this] { showPage(0); });
    connect(primary, &QPushButton::clicked, this, [this] {
        if (pages->currentIndex() + 1 < pages->count()) showPage(pages->currentIndex() + 1);
        else finish();
    });
    retranslate();
    updateNavigation();
    applyUiTheme(this, theme->currentData().toString());
}

QToolButton *WelcomeDialog::addChoice(QGridLayout *grid, QButtonGroup *group, const QString &value, int columns)
{
    auto *card = new QToolButton(this);
    card->setCheckable(true);
    card->setProperty("choice", value);
    card->setProperty("choiceCard", true);
    card->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
    card->setCursor(Qt::PointingHandCursor);
    const int index = group->buttons().size();
    group->addButton(card);
    grid->addWidget(card, index / columns, index % columns);
    return card;
}

void WelcomeDialog::showPage(int index)
{
    if (transition || index == pages->currentIndex() || index < 0 || index >= pages->count()) return;
    const int direction = index > pages->currentIndex() ? 1 : -1;
    // The outgoing page leaves as a snapshot while the real incoming page slides in underneath it.
    auto *snapshot = new QLabel(pages);
    snapshot->setPixmap(pages->currentWidget()->grab());
    snapshot->setGeometry(pages->currentWidget()->geometry());
    pages->setCurrentIndex(index);
    snapshot->show();
    snapshot->raise();
    auto *incoming = pages->currentWidget();
    auto *fadeIn = new QGraphicsOpacityEffect(incoming);
    incoming->setGraphicsEffect(fadeIn);
    auto *fadeOut = new QGraphicsOpacityEffect(snapshot);
    snapshot->setGraphicsEffect(fadeOut);
    const int shift = qMax(80, pages->width() / 3) * direction;
    auto *group = new QParallelAnimationGroup(this);
    auto animate = [group](QObject *target, const QByteArray &property, const QVariant &from, const QVariant &to, int duration) {
        auto *animation = new QPropertyAnimation(target, property, group);
        animation->setDuration(duration);
        animation->setEasingCurve(QEasingCurve::OutCubic);
        animation->setStartValue(from);
        animation->setEndValue(to);
        group->addAnimation(animation);
    };
    animate(incoming, "pos", QPoint(shift, 0), QPoint(0, 0), 420);
    animate(fadeIn, "opacity", 0.0, 1.0, 360);
    animate(snapshot, "pos", QPoint(0, 0), QPoint(-shift, 0), 420);
    animate(fadeOut, "opacity", 1.0, 0.0, 240);
    connect(group, &QAbstractAnimation::finished, this, [incoming, snapshot] {
        incoming->setGraphicsEffect(nullptr);
        incoming->move(0, 0);
        snapshot->deleteLater();
    });
    transition = group;
    group->start(QAbstractAnimation::DeleteWhenStopped);
    updateNavigation();
}

void WelcomeDialog::updateNavigation()
{
    const int index = pages->currentIndex();
    back->setVisible(index > 0);
    primary->setText(index + 1 < pages->count() ? tr("Далее") : tr("Начать просмотр"));
    for (int i = 0; i < dots.size(); ++i) {
        dots[i]->setProperty("active", i == index);
        dots[i]->setFixedSize(i == index ? 22 : 8, 8);
        dots[i]->style()->unpolish(dots[i]);
        dots[i]->style()->polish(dots[i]);
    }
}

void WelcomeDialog::finish()
{
    QSettings settings;
    settings.setValue("appearance/theme", theme->currentData());
    settings.setValue("appearance/language", languages->currentData());
    settings.setValue("search/engine", engines->checkedButton()->property("choice"));
    settings.setValue("mail/url", mails->checkedButton()->property("choice"));
    settings.setValue("onboarding/completed", true);
    settings.sync();
    accept();
}

void WelcomeDialog::retranslate()
{
    setWindowTitle(tr("Добро пожаловать в NeoSerf"));
    title->setText(tr("Ваш NeoSerf. С первого запуска."));
    subtitle->setText(tr("Выберите удобный вид и возьмите привычное с собой."));
    appearanceLabel->setText(tr("Оформление"));
    languageLabel->setText(tr("Язык интерфейса"));
    theme->setItemText(0, tr("Как на устройстве"));
    theme->setItemText(1, tr("Светлая"));
    theme->setItemText(2, tr("Тёмная"));
    languages->setItemText(0, tr("Как на устройстве"));
    importLabel->setText(tr("Перенесите привычное"));
    importer->retranslate();
    footnote->setText(tr("Импорт можно пропустить и выполнить позже в настройках."));
    servicesTitle->setText(tr("Поиск и почта"));
    servicesSubtitle->setText(tr("Что открывать, когда вы ищете в строке и нажимаете «Почта»."));
    engineLabel->setText(tr("Поисковая система"));
    mailLabel->setText(tr("Почта по умолчанию"));
    servicesNote->setText(tr("Всё это можно изменить позже в настройках."));
    yandexEngine->setText(tr("Яндекс"));
    yandexMail->setText(tr("Яндекс Почта"));
    back->setText(tr("Назад"));
    updateNavigation();
}

void WelcomeDialog::changeEvent(QEvent *event)
{
    if (event->type() == QEvent::LanguageChange) retranslate();
    QDialog::changeEvent(event);
}

void WelcomeDialog::reject() { if (!importer->isBusy()) QDialog::reject(); }
