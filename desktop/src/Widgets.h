#pragma once
#include <QAbstractButton>
#include <QCheckBox>
#include <QComboBox>
#include <QMenu>
#include <QPointer>

class QStackedWidget;
class QVariantAnimation;

// Rounded popup with a soft shadow that fades and slides in. A submenu entry marked with
// setClickableSubmenu() also acts as a normal item when clicked (like History in Chrome).
class PopupMenu : public QMenu {
    Q_OBJECT
public:
    explicit PopupMenu(QWidget *parent = nullptr);
    // Shows the panel so that its visible edge, not the shadow, lands at the given point.
    void popupAt(const QPoint &panelTopLeft, QAction *active = nullptr);
    PopupMenu *addPopup(const QIcon &icon, const QString &text);
    static void setClickableSubmenu(QAction *action) { action->setProperty("clickableSubmenu", true); }
    static constexpr int shadow = 12;
protected:
    void paintEvent(QPaintEvent *event) override;
    void showEvent(QShowEvent *event) override;
    void mouseReleaseEvent(QMouseEvent *event) override;
};

// A combo box whose list opens as a PopupMenu instead of the square native list.
class NeoComboBox : public QComboBox {
    Q_OBJECT
public:
    using QComboBox::QComboBox;
    void showPopup() override;
    void hidePopup() override;
private:
    QPointer<PopupMenu> popup;
};

// A checkbox drawn as an animated switch, with the label on the left and the switch on the right.
class ToggleSwitch : public QCheckBox {
    Q_OBJECT
    Q_PROPERTY(qreal knob READ knob WRITE setKnob)
public:
    explicit ToggleSwitch(const QString &text, QWidget *parent = nullptr);
    QSize sizeHint() const override;
    QSize minimumSizeHint() const override { return sizeHint(); }
    qreal knob() const { return position; }
    void setKnob(qreal value) { position = value; update(); }
protected:
    void paintEvent(QPaintEvent *event) override;
    bool hitButton(const QPoint &pos) const override { return rect().contains(pos); }
    void focusInEvent(QFocusEvent *event) override;
    void enterEvent(QEnterEvent *event) override;
    void leaveEvent(QEvent *event) override;
private:
    qreal position = 0;
    bool hovered = false;
    bool keyboardFocus = false; // focus ring only for keyboard navigation
    QVariantAnimation *animation;
};

// Round accent button with a white arrow, like the send button of chat apps, but pointing right.
class SendButton : public QAbstractButton {
    Q_OBJECT
public:
    explicit SendButton(QWidget *parent = nullptr);
protected:
    void paintEvent(QPaintEvent *event) override;
    void enterEvent(QEnterEvent *event) override { hovered = true; update(); QAbstractButton::enterEvent(event); }
    void leaveEvent(QEvent *event) override { hovered = false; update(); QAbstractButton::leaveEvent(event); }
private:
    bool hovered = false;
};

// Switches pages with a short fade and a small slide (vertical by default).
void fadeToPage(QStackedWidget *stack, int index, Qt::Orientation orientation = Qt::Vertical);
