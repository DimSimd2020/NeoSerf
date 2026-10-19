import SwiftUI

struct ReaderView: View {
    let article: ReaderArticle?
    let fontSize: Int
    let theme: ReaderTheme
    let onClose: () -> Void
    let onFont: (Int) -> Void
    let onTheme: (ReaderTheme) -> Void

    private var colors: (bg: Color, text: Color, secondary: Color) {
        switch theme {
        case .graphite:
            return (Color(red: 0.08, green: 0.09, blue: 0.11), Color(red: 0.90, green: 0.93, blue: 0.95), Color(red: 0.61, green: 0.64, blue: 0.69))
        case .amoled:
            return (Color(red: 0.02, green: 0.02, blue: 0.03), Color(red: 0.85, green: 0.89, blue: 0.93), Color(red: 0.51, green: 0.55, blue: 0.61))
        case .sepia:
            return (Color(red: 0.14, green: 0.12, blue: 0.10), Color(red: 0.92, green: 0.86, blue: 0.78), Color(red: 0.72, green: 0.65, blue: 0.56))
        }
    }

    var body: some View {
        let c = colors
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Режим чтения")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(c.text)
                    if let article = article {
                        Text(article.domain)
                            .font(.system(size: 12))
                            .foregroundColor(c.secondary)
                    }
                }
                Spacer()
                Button("Закрыть чтение", action: onClose)
                    .foregroundColor(c.text)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            HStack {
                Button(action: { onFont(max(14, fontSize - 2)) }) {
                    Image(systemName: "textformat.size.smaller").foregroundColor(c.text)
                }
                Button(action: { onFont(min(28, fontSize + 2)) }) {
                    Image(systemName: "textformat.size.larger").foregroundColor(c.text)
                }
                Spacer()
                ForEach(ReaderTheme.allCases) { item in
                    Button(item.labelRu) { onTheme(item) }
                        .font(.system(size: 12, weight: theme == item ? .bold : .regular))
                        .foregroundColor(theme == item ? c.text : c.secondary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
            Divider().background(c.secondary.opacity(0.3))
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let article = article {
                        Text(article.title)
                            .font(.system(size: CGFloat(fontSize + 6), weight: .bold))
                            .foregroundColor(c.text)
                        Text("\(article.byline) · \(article.estimatedMinutes) мин")
                            .font(.system(size: CGFloat(max(12, fontSize - 4))))
                            .foregroundColor(c.secondary)
                        if article.paragraphs.isEmpty {
                            Text("На текущей странице не обнаружено длинной статьи. Откройте любую новость, документацию или статью для чтения без лишних элементов.")
                                .foregroundColor(c.secondary)
                        } else {
                            ForEach(Array(article.paragraphs.enumerated()), id: \.offset) { _, para in
                                Text(para)
                                    .font(.system(size: CGFloat(fontSize)))
                                    .foregroundColor(c.text)
                            }
                        }
                    }
                }
                .padding(20)
            }
        }
        .background(c.bg.ignoresSafeArea())
    }
}
