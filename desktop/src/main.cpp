#include "BrowserWindow.h"
#include <QApplication>

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);
    QCoreApplication::setOrganizationName("NeoSerf");
    QCoreApplication::setApplicationName("NeoSerf");
    QCoreApplication::setApplicationVersion("1.2.1");
    QApplication::setStyle("Fusion");
    BrowserWindow window;
    const auto args = app.arguments();
    for (int i = 1; i < args.size(); ++i) {
        const QUrl url(args.at(i));
        if (url.scheme() == "http" || url.scheme() == "https") window.newTab(false, url);
    }
    window.show();
    return app.exec();
}
