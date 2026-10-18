#include "BrowserWindow.h"
#include "Language.h"
#include "WelcomeDialog.h"
#include <QApplication>

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);
    QCoreApplication::setOrganizationName("NeoSerf");
    QCoreApplication::setApplicationName("NeoSerf");
    QCoreApplication::setApplicationVersion("1.3.0");
    QApplication::setStyle("Fusion");
    Language language;
    language.apply(QSettings().value("appearance/language", "system").toString());
    if (WelcomeDialog::needed()) {
        WelcomeDialog welcome(language);
        if (welcome.exec() != QDialog::Accepted) return 0;
    }
    BrowserWindow window;
    const auto args = app.arguments();
    for (int i = 1; i < args.size(); ++i) {
        const QUrl url(args.at(i));
        if (url.scheme() == "http" || url.scheme() == "https") window.newTab(false, url);
    }
    window.show();
    return app.exec();
}
