#include "UiAssets.h"
#include <QAction>
#include <QComboBox>
#include <QFile>
#include <QGuiApplication>
#include <QStyleHints>
#include <QAbstractButton>
#include <QToolButton>
#include <QWebEngineView>
#include <QWebEnginePage>
#ifdef Q_OS_WIN
#include <windows.h>
#include <dwmapi.h>
#endif

void applyUiTheme(QWidget *widget, const QString &mode)
{
    const bool dark = mode == "dark" || (mode == "system" &&
        QGuiApplication::styleHints()->colorScheme() == Qt::ColorScheme::Dark);
    widget->setProperty("darkTheme", dark);
    const QColor background(dark ? "#191919" : "#f3f3f3");
    const QColor text(dark ? "#ededed" : "#1b1b1b");
    const QColor muted(dark ? "#a3a3a3" : "#606060");
    const QColor field(dark ? "#252525" : "#e6e6e6");
    const QColor accent(dark ? "#459fff" : "#166ad8");
    auto colors = widget->palette();
    colors.setColor(QPalette::Window, background);
    colors.setColor(QPalette::WindowText, text);
    colors.setColor(QPalette::Base, field);
    colors.setColor(QPalette::AlternateBase, background);
    colors.setColor(QPalette::Text, text);
    colors.setColor(QPalette::Button, field);
    colors.setColor(QPalette::ButtonText, text);
    colors.setColor(QPalette::Highlight, accent);
    colors.setColor(QPalette::HighlightedText, Qt::white);
    colors.setColor(QPalette::PlaceholderText, muted);
    colors.setColor(QPalette::ToolTipBase, field);
    colors.setColor(QPalette::ToolTipText, text);
    colors.setColor(QPalette::Disabled, QPalette::Text, muted);
    colors.setColor(QPalette::Disabled, QPalette::ButtonText, muted);
    widget->setPalette(colors);
    const QMap<QString, QString> tokens{
        {"background", background.name()}, {"text", text.name()}, {"field", field.name()},
        {"muted", muted.name()}, {"accent", accent.name()},
        {"hover", dark ? "#2d2d2d" : "#dddddd"}, {"pressed", dark ? "#343434" : "#d0d0d0"},
        {"border", dark ? "#353535" : "#cbcbcb"}, {"borderStrong", dark ? "#3a3a3a" : "#bfbfbf"},
        {"selection", dark ? "#245488" : "#d9e9ff"}, {"selectionText", dark ? "#ededed" : "#12385e"},
        {"subtle", dark ? "#929292" : "#686868"},
        {"disabled", dark ? "#656565" : "#989898"}, {"menuHover", dark ? "#383838" : "#e1e1e1"},
        {"menu", dark ? "rgba(37, 37, 37, 242)" : "rgba(249, 249, 249, 242)"},
        {"incognito", dark ? "#a491ff" : "#6545cc"},
        {"chevronDown", dark ? ":/assets/icons/chevron-down-dark.svg" : ":/assets/icons/chevron-down-light.svg"},
        {"chevronLeft", dark ? ":/assets/icons/chevron-left-dark.svg" : ":/assets/icons/chevron-left-light.svg"},
        {"chevronRight", dark ? ":/assets/icons/chevron-right-dark.svg" : ":/assets/icons/chevron-right-light.svg"}
    };
    QFile style(":/assets/style.qss");
    if (style.open(QIODevice::ReadOnly)) {
        auto css = QString::fromUtf8(style.readAll());
        for (auto it = tokens.cbegin(); it != tokens.cend(); ++it) css.replace("@" + it.key() + "@", it.value());
        widget->setStyleSheet(css);
    }
    for (auto *button : widget->findChildren<QAbstractButton *>()) {
        const auto name = button->property("neoIcon").toString();
        if (!name.isEmpty()) button->setIcon(neoIcon(name, text));
    }
    for (auto *action : widget->findChildren<QAction *>()) {
        const auto name = action->property("neoIcon").toString();
        if (!name.isEmpty()) action->setIcon(neoIcon(name, text));
    }
    for (auto *combo : widget->findChildren<QComboBox *>("themeSelector")) {
        const QStringList icons{"device", "sun", "moon"};
        for (int i = 0; i < icons.size(); ++i) combo->setItemIcon(i, neoIcon(icons.at(i), text));
    }
    for (auto *view : widget->findChildren<QWebEngineView *>()) view->page()->setBackgroundColor(background);
#ifdef Q_OS_WIN
    const BOOL darkTitle = dark;
    DwmSetWindowAttribute(reinterpret_cast<HWND>(widget->winId()), 20, &darkTitle, sizeof(darkTitle));
#endif
}
