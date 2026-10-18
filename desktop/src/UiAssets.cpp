#include "UiAssets.h"
#include <QAction>
#include <QFile>
#include <QLineEdit>
#include <QPainter>
#include <QSvgRenderer>

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

void addClearButton(QLineEdit *field)
{
    auto *clear = field->addAction(neoIcon("close", field->palette().color(QPalette::Text)), QLineEdit::TrailingPosition);
    clear->setProperty("neoIcon", "close");
    clear->setText(QObject::tr("Очистить"));
    clear->setVisible(!field->text().isEmpty());
    QObject::connect(clear, &QAction::triggered, field, &QLineEdit::clear);
    QObject::connect(field, &QLineEdit::textChanged, clear, [clear](const QString &text) { clear->setVisible(!text.isEmpty()); });
}
