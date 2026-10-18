#include "Widgets.h"
#include <QApplication>
#include <QGraphicsOpacityEffect>
#include <QLabel>
#include <QMouseEvent>
#include <QPainter>
#include <QPainterPath>
#include <QParallelAnimationGroup>
#include <QPropertyAnimation>
#include <QScreen>
#include <QStackedWidget>
#include <QVariantAnimation>

PopupMenu::PopupMenu(QWidget *parent) : QMenu(parent)
{
    // Without the system drop shadow and with a transparent window, only the rounded panel is visible.
    setWindowFlags(windowFlags() | Qt::FramelessWindowHint | Qt::NoDropShadowWindowHint);
    setAttribute(Qt::WA_TranslucentBackground);
    setProperty("neoPopup", true); // style.qss reserves the shadow margin for these menus
}

void PopupMenu::popupAt(const QPoint &panelTopLeft, QAction *active)
{
    popup(panelTopLeft - QPoint(shadow, shadow));
    if (active) setActiveAction(active);
}

PopupMenu *PopupMenu::addPopup(const QIcon &icon, const QString &text)
{
    auto *menu = new PopupMenu(this);
    menu->setTitle(text);
    menu->setIcon(icon);
    addMenu(menu);
    return menu;
}

void PopupMenu::paintEvent(QPaintEvent *event)
{
    {
        QPainter painter(this);
        painter.setRenderHint(QPainter::Antialiasing);
        const QRectF panel = QRectF(rect()).adjusted(shadow, shadow, -shadow, -shadow);
        const bool dark = palette().color(QPalette::Window).lightness() < 128;
        // Concentric strokes, fainter towards the outside, read as a soft blur.
        for (int i = shadow; i > 0; --i) {
            const qreal t = 1.0 - qreal(i) / shadow;
            QColor color(0, 0, 0, int((dark ? 70 : 34) * t * t));
            painter.setPen(QPen(color, 1.0));
            painter.setBrush(Qt::NoBrush);
            painter.drawRoundedRect(panel.adjusted(-i, -i + 3, i, i + 3), 16 + i, 16 + i);
        }
    }
    QMenu::paintEvent(event);
}

void PopupMenu::showEvent(QShowEvent *event)
{
    QMenu::showEvent(event);
    QPoint target = pos();
    if (auto *parentMenu = qobject_cast<QMenu *>(parentWidget()); parentMenu && parentMenu->isVisible()) {
        // Qt lines submenus up with the parent's window edge; bring the visible panels together instead.
        const int overlap = 2 * shadow - 4;
        target.rx() += target.x() >= parentMenu->x() ? -overlap : overlap;
        move(target);
    }
    setWindowOpacity(0.0);
    auto *animation = new QVariantAnimation(this);
    animation->setDuration(180);
    animation->setStartValue(0.0);
    animation->setEndValue(1.0);
    animation->setEasingCurve(QEasingCurve::OutCubic);
    connect(animation, &QVariantAnimation::valueChanged, this, [this, target](const QVariant &value) {
        const qreal t = value.toReal();
        setWindowOpacity(t);
        move(target + QPoint(0, qRound((1.0 - t) * -8)));
    });
    animation->start(QAbstractAnimation::DeleteWhenStopped);
}

void PopupMenu::mouseReleaseEvent(QMouseEvent *event)
{
    auto *action = actionAt(event->position().toPoint());
    if (action && action->menu() && action->isEnabled() && action->property("clickableSubmenu").toBool()) {
        while (auto *open = QApplication::activePopupWidget()) open->close();
        action->trigger();
        return;
    }
    QMenu::mouseReleaseEvent(event);
}

void NeoComboBox::showPopup()
{
    if (popup) return;
    popup = new PopupMenu(this);
    popup->setObjectName("comboPopup");
    popup->setMinimumWidth(width() + 2 * PopupMenu::shadow);
    for (int i = 0; i < count(); ++i) {
        auto *action = popup->addAction(itemIcon(i), itemText(i));
        if (i == currentIndex()) popup->setDefaultAction(action); // shown in bold
        connect(action, &QAction::triggered, this, [this, i] {
            setCurrentIndex(i);
            emit activated(i);
        });
    }
    connect(popup, &QMenu::aboutToHide, popup, &QObject::deleteLater);
    popup->popupAt(mapToGlobal(QPoint(0, height() + 6)), popup->actions().value(currentIndex()));
}

void NeoComboBox::hidePopup()
{
    if (popup) popup->close();
    QComboBox::hidePopup();
}

ToggleSwitch::ToggleSwitch(const QString &text, QWidget *parent) : QCheckBox(text, parent), animation(new QVariantAnimation(this))
{
    setCursor(Qt::PointingHandCursor);
    setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
    animation->setDuration(160);
    animation->setEasingCurve(QEasingCurve::OutCubic);
    connect(animation, &QVariantAnimation::valueChanged, this, [this](const QVariant &value) { setKnob(value.toReal()); });
    connect(this, &QCheckBox::toggled, this, [this](bool on) {
        animation->stop();
        if (!isVisible()) { setKnob(on ? 1 : 0); return; }
        animation->setStartValue(position);
        animation->setEndValue(on ? 1.0 : 0.0);
        animation->start();
    });
}

QSize ToggleSwitch::sizeHint() const
{
    const auto metrics = fontMetrics();
    return QSize(metrics.horizontalAdvance(text()) + 24 + 40, qMax(30, metrics.height() + 10));
}

void ToggleSwitch::focusInEvent(QFocusEvent *event)
{
    keyboardFocus = event->reason() == Qt::TabFocusReason || event->reason() == Qt::BacktabFocusReason;
    QCheckBox::focusInEvent(event);
}

void ToggleSwitch::enterEvent(QEnterEvent *event) { hovered = true; update(); QCheckBox::enterEvent(event); }
void ToggleSwitch::leaveEvent(QEvent *event) { hovered = false; update(); QCheckBox::leaveEvent(event); }

void ToggleSwitch::paintEvent(QPaintEvent *)
{
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing);
    const auto colors = palette();
    const QColor text = colors.color(isEnabled() ? QPalette::Active : QPalette::Disabled, QPalette::Text);
    const QRectF track(width() - 40, (height() - 22) / 2.0, 40, 22);
    painter.setPen(text);
    painter.drawText(rect().adjusted(0, 0, -56, 0), Qt::AlignVCenter | Qt::AlignLeft | Qt::TextSingleLine,
        fontMetrics().elidedText(this->text(), Qt::ElideRight, width() - 56));

    QColor off = text;
    off.setAlphaF(hovered ? 0.55f : 0.4f);
    QColor on = colors.color(QPalette::Highlight);
    if (!isEnabled()) on.setAlphaF(0.4f);
    // Off: outlined track with a small knob. On: filled track with a larger white knob.
    QColor fill = on;
    fill.setAlphaF(on.alphaF() * position);
    painter.setPen(QPen(position < 0.5 ? off : on, 1.5));
    painter.setBrush(fill);
    painter.drawRoundedRect(track.adjusted(0.75, 0.75, -0.75, -0.75), 10.25, 10.25);
    const qreal radius = 5 + 3 * position;
    const QPointF center(track.left() + 11 + (track.width() - 22) * position, track.center().y());
    QColor knobColor = position < 0.5 ? off : QColor(Qt::white);
    knobColor.setAlphaF(position < 0.5 ? qMin(1.0, off.alphaF() + 0.3) : 1.0);
    painter.setPen(Qt::NoPen);
    painter.setBrush(knobColor);
    painter.drawEllipse(center, radius, radius);
    if (hasFocus() && keyboardFocus) {
        painter.setPen(QPen(colors.color(QPalette::Highlight), 1.5));
        painter.setBrush(Qt::NoBrush);
        painter.drawRoundedRect(track.adjusted(-3, -3, 3, 3), 14, 14);
    }
}

SendButton::SendButton(QWidget *parent) : QAbstractButton(parent)
{
    setCursor(Qt::PointingHandCursor);
    setFocusPolicy(Qt::NoFocus);
}

void SendButton::paintEvent(QPaintEvent *)
{
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing);
    QColor fill = palette().color(QPalette::Highlight);
    if (isDown()) fill = fill.darker(115);
    else if (hovered) fill = fill.lighter(112);
    const qreal side = qMin(width(), height());
    const QRectF circle((width() - side) / 2.0, (height() - side) / 2.0, side, side);
    painter.setPen(Qt::NoPen);
    painter.setBrush(fill);
    painter.drawEllipse(circle);
    // Arrow pointing right: a shaft and a chevron, sized relative to the circle.
    const qreal unit = side / 24.0;
    const QPointF c = circle.center();
    QPainterPath arrow;
    arrow.moveTo(c + QPointF(-6.5 * unit, 0));
    arrow.lineTo(c + QPointF(6.5 * unit, 0));
    arrow.moveTo(c + QPointF(1 * unit, -5.5 * unit));
    arrow.lineTo(c + QPointF(6.5 * unit, 0));
    arrow.lineTo(c + QPointF(1 * unit, 5.5 * unit));
    painter.setPen(QPen(Qt::white, qMax(1.8, 2.1 * unit), Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin));
    painter.setBrush(Qt::NoBrush);
    painter.drawPath(arrow);
}

void fadeToPage(QStackedWidget *stack, int index, Qt::Orientation orientation)
{
    if (index == stack->currentIndex() || index < 0 || index >= stack->count()) return;
    if (auto *running = stack->findChild<QParallelAnimationGroup *>("pageFade", Qt::FindDirectChildrenOnly))
        running->setCurrentTime(running->totalDuration());
    const int direction = index > stack->currentIndex() ? 1 : -1;
    auto *snapshot = new QLabel(stack);
    snapshot->setPixmap(stack->currentWidget()->grab());
    snapshot->setGeometry(stack->currentWidget()->geometry());
    stack->setCurrentIndex(index);
    snapshot->show();
    snapshot->raise();
    auto *incoming = stack->currentWidget();
    auto *fadeIn = new QGraphicsOpacityEffect(incoming);
    incoming->setGraphicsEffect(fadeIn);
    auto *fadeOut = new QGraphicsOpacityEffect(snapshot);
    snapshot->setGraphicsEffect(fadeOut);
    const QPoint shift = (orientation == Qt::Vertical ? QPoint(0, 18) : QPoint(qMax(60, stack->width() / 4), 0)) * direction;
    auto *group = new QParallelAnimationGroup(stack);
    group->setObjectName("pageFade");
    auto animate = [group](QObject *target, const QByteArray &property, const QVariant &from, const QVariant &to, int duration) {
        auto *animation = new QPropertyAnimation(target, property, group);
        animation->setDuration(duration);
        animation->setEasingCurve(QEasingCurve::OutCubic);
        animation->setStartValue(from);
        animation->setEndValue(to);
        group->addAnimation(animation);
    };
    const QPoint home = incoming->pos();
    animate(incoming, "pos", home + shift, home, 280);
    animate(fadeIn, "opacity", 0.0, 1.0, 240);
    animate(fadeOut, "opacity", 1.0, 0.0, 140);
    QObject::connect(group, &QAbstractAnimation::finished, stack, [group, incoming, snapshot, home] {
        group->setObjectName(QString());
        incoming->setGraphicsEffect(nullptr);
        incoming->move(home);
        snapshot->deleteLater();
    });
    group->start(QAbstractAnimation::DeleteWhenStopped);
}
