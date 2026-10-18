#include "UiAssets.h"
#include "Widgets.h"
#include <QSettings>
#include <QAction>
#include <QEvent>
#include <QGraphicsOpacityEffect>
#include <QPropertyAnimation>
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

namespace {
// Keeps the round send button vertically centered at the right end of its field.
class SubmitPlacement : public QObject {
public:
    SubmitPlacement(QLineEdit *field, SendButton *button) : QObject(field), field(field), button(button) {}
    void place()
    {
        const int side = qBound(24, field->height() - 14, 36);
        const int inset = (field->height() - side) / 2;
        button->setGeometry(field->width() - side - inset, inset, side, side);
        field->setTextMargins(0, 0, side + inset - 6, 0);
    }
protected:
    bool eventFilter(QObject *, QEvent *event) override
    {
        if (event->type() == QEvent::Resize || event->type() == QEvent::Show) place();
        return false;
    }
private:
    QLineEdit *field;
    SendButton *button;
};
}

void addSubmitButton(QLineEdit *field)
{
    auto *submit = new SendButton(field);
    submit->setObjectName("submitButton");
    submit->setToolTip(QObject::tr("Перейти · Enter"));
    submit->setAccessibleName(QObject::tr("Перейти"));
    submit->setVisible(!field->text().isEmpty());
    auto *placement = new SubmitPlacement(field, submit);
    field->installEventFilter(placement);
    placement->place();
    QObject::connect(submit, &QAbstractButton::clicked, field, &QLineEdit::returnPressed);
    QObject::connect(field, &QLineEdit::textChanged, submit, [submit](const QString &text) {
        if (submit->isHidden() == text.isEmpty()) return;
        submit->setVisible(!text.isEmpty());
        if (text.isEmpty()) return;
        // Pops in with a quick fade.
        auto *fade = new QGraphicsOpacityEffect(submit);
        submit->setGraphicsEffect(fade);
        auto *animation = new QPropertyAnimation(fade, "opacity", fade);
        animation->setDuration(160);
        animation->setStartValue(0.0);
        animation->setEndValue(1.0);
        QObject::connect(animation, &QAbstractAnimation::finished, submit, [submit] { submit->setGraphicsEffect(nullptr); });
        animation->start(QAbstractAnimation::DeleteWhenStopped);
    });
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
