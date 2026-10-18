#include "UiAssets.h"
#include <QSettings>
#include <QAction>
#include <QDialogButtonBox>
#include <QMessageBox>
#include <QPushButton>
#include <QFile>
#include <QLineEdit>
#include <QPainter>
#include <QSvgRenderer>

bool validMailUrl(const QUrl &url)
{
    return url.isValid() && url.scheme() == "https" && !url.host().isEmpty() && url.userInfo().isEmpty();
}

QUrl mailUrl(const QSettings &settings)
{
    const QUrl url(settings.value("mail/url", "https://mail.google.com/").toString());
    return validMailUrl(url) ? url : QUrl("https://mail.google.com/");
}

QIcon neoIcon(const QString &name, const QColor &color)
{
    QFile file(":/assets/icons/" + name + ".svg");
    if (!file.open(QIODevice::ReadOnly)) return {};
    auto svg = file.readAll();
    svg.replace("currentColor", color.name().toUtf8());
    QSvgRenderer renderer(svg);
    QIcon icon;
    for (int scale : {1, 2, 3}) {
        for (auto mode : {QIcon::Normal, QIcon::Disabled}) {
            QPixmap pixmap(24 * scale, 24 * scale);
            pixmap.fill(Qt::transparent);
            QPainter painter(&pixmap);
            painter.setRenderHint(QPainter::Antialiasing);
            if (mode == QIcon::Disabled) painter.setOpacity(0.35);
            renderer.render(&painter);
            painter.end();
            pixmap.setDevicePixelRatio(scale);
            icon.addPixmap(pixmap, mode);
        }
    }
    return icon;
}

void addSubmitButton(QLineEdit *field)
{
    auto *submit = field->addAction(neoIcon("forward", field->palette().color(QPalette::Text)), QLineEdit::TrailingPosition);
    submit->setObjectName("submitAction");
    submit->setProperty("neoIcon", "forward");
    submit->setText(QObject::tr("Перейти"));
    submit->setToolTip(QObject::tr("Перейти · Enter"));
    submit->setVisible(!field->text().isEmpty());
    QObject::connect(submit, &QAction::triggered, field, &QLineEdit::returnPressed);
    QObject::connect(field, &QLineEdit::textChanged, submit, [submit](const QString &text) { submit->setVisible(!text.isEmpty()); });
}

void localizeButtons(QDialogButtonBox *box)
{
    const QList<QPair<QDialogButtonBox::StandardButton, QString>> names{
        {QDialogButtonBox::Save, QObject::tr("Сохранить")}, {QDialogButtonBox::Cancel, QObject::tr("Отмена")},
        {QDialogButtonBox::Close, QObject::tr("Закрыть")}, {QDialogButtonBox::Ok, QObject::tr("ОК")},
        {QDialogButtonBox::Yes, QObject::tr("Да")}, {QDialogButtonBox::No, QObject::tr("Нет")}};
    for (const auto &[button, text] : names)
        if (auto *widget = box->button(button)) widget->setText(text);
}

bool askYesNo(QWidget *parent, const QString &title, const QString &text)
{
    QMessageBox box(QMessageBox::Question, title, text, QMessageBox::Yes | QMessageBox::No, parent);
    box.setDefaultButton(QMessageBox::No);
    box.button(QMessageBox::Yes)->setText(QObject::tr("Да"));
    box.button(QMessageBox::No)->setText(QObject::tr("Нет"));
    return box.exec() == QMessageBox::Yes;
}

void showInfo(QWidget *parent, const QString &title, const QString &text)
{
    QMessageBox box(QMessageBox::Information, title, text, QMessageBox::Ok, parent);
    box.button(QMessageBox::Ok)->setText(QObject::tr("ОК"));
    box.exec();
}
