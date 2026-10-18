#include "AddressResolver.h"
#include <QRegularExpression>

QUrl AddressResolver::resolve(const QString &input, const QString &engine)
{
    const auto text = input.trimmed();
    if (text.isEmpty()) return {};
    const QUrl direct(text);
    if (direct.scheme() == "https" || direct.scheme() == "http") return direct;
    const bool hasSpaces = text.contains(QRegularExpression("\\s"));
    if (!hasSpaces && (text.contains('.') || text.startsWith("localhost") || text.startsWith('['))
        && !text.contains("://")) {
        return QUrl((text.startsWith("localhost") || text.startsWith("127.")) ? "http://" + text : "https://" + text);
    }
    QUrl search;
    QString key = "q";
    if (engine == "DuckDuckGo") search = QUrl("https://duckduckgo.com/");
    else if (engine == QString::fromUtf8("Яндекс")) { search = QUrl("https://ya.ru/search/"); key = "text"; }
    else if (engine == "Bing") search = QUrl("https://www.bing.com/search");
    else if (engine == "Brave") search = QUrl("https://search.brave.com/search");
    else search = QUrl("https://www.google.com/search");
    search.setQuery(key + "=" + QString::fromLatin1(QUrl::toPercentEncoding(text)), QUrl::StrictMode);
    return search;
}
