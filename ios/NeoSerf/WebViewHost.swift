import SwiftUI
import WebKit

private let desktopUA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15 NeoSerf/1.3.3"
private let mobileUA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1 NeoSerf/1.3.3"

struct WebViewHost: UIViewRepresentable {
    @ObservedObject var model: BrowserViewModel

    func makeCoordinator() -> Coordinator {
        Coordinator(model: model)
    }

    func makeUIView(context: Context) -> WebContainerView {
        let view = WebContainerView()
        context.coordinator.container = view
        view.coordinator = context.coordinator
        return view
    }

    func updateUIView(_ uiView: WebContainerView, context: Context) {
        context.coordinator.model = model
        uiView.sync(model: model)
        if let command = model.webCommand, command.token != context.coordinator.lastCommandToken {
            context.coordinator.lastCommandToken = command.token
            uiView.handle(command.kind, model: model)
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var model: BrowserViewModel
        weak var container: WebContainerView?
        var lastCommandToken = 0
        private var progressObs: NSKeyValueObservation?

        init(model: BrowserViewModel) {
            self.model = model
        }

        func attachProgress(_ webView: WKWebView) {
            progressObs = webView.observe(\.estimatedProgress, options: [.new]) { [weak self] view, _ in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    self.model.onProgress(tabId: self.model.activeTabId, progress: view.estimatedProgress)
                }
            }
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            let url = webView.url?.absoluteString ?? model.activeTab.url
            DispatchQueue.main.async {
                self.model.onPageStarted(tabId: self.model.activeTabId, url: url)
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let url = webView.url?.absoluteString ?? ""
            let title = webView.title
            let back = webView.canGoBack
            let forward = webView.canGoForward
            let tabId = model.activeTabId
            DispatchQueue.main.async {
                self.model.onPageFinished(
                    tabId: tabId,
                    url: url,
                    title: title,
                    canGoBack: back,
                    canGoForward: forward
                )
            }
            if model.preferences.adBlockEnabled {
                webView.evaluateJavaScript(AdBlocker.cosmeticJS) { result, _ in
                    let count = (result as? Int) ?? (result as? NSNumber)?.intValue ?? 0
                    DispatchQueue.main.async {
                        self.model.applyCosmeticCount(tabId: tabId, count: count)
                    }
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.model.updateTab(self.model.activeTabId) {
                    $0.isLoading = false
                    $0.canGoBack = webView.canGoBack
                    $0.canGoForward = webView.canGoForward
                }
            }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.model.updateTab(self.model.activeTabId) { $0.isLoading = false }
            }
        }

        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if navigationAction.targetFrame == nil, let url = navigationAction.request.url?.absoluteString {
                DispatchQueue.main.async {
                    self.model.submitAddressOrQuery(url)
                }
            }
            return nil
        }

        func webView(
            _ webView: WKWebView,
            runJavaScriptAlertPanelWithMessage message: String,
            initiatedByFrame frame: WKFrameInfo,
            completionHandler: @escaping () -> Void
        ) {
            let alert = UIAlertController(title: "NeoSerf", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
            var presenter: UIViewController? = container?.window?.rootViewController
            while let shown = presenter?.presentedViewController {
                presenter = shown
            }
            if let presenter = presenter {
                presenter.present(alert, animated: true)
            } else {
                completionHandler()
            }
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            if let url = navigationAction.request.url, let scheme = url.scheme?.lowercased(),
               scheme != "http", scheme != "https", scheme != "about", scheme != "blob" {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }
    }
}

final class WebContainerView: UIView {
    weak var coordinator: WebViewHost.Coordinator?
    private var pool: [UUID: WKWebView] = [:]
    private var currentId: UUID?
    private let regularStore = WKWebsiteDataStore.default()
    private let incognitoStore = WKWebsiteDataStore.nonPersistent()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        clipsToBounds = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        subviews.forEach { $0.frame = bounds }
    }

    func sync(model: BrowserViewModel) {
        let tab = model.activeTab
        if tab.isStartPage || tab.isReaderMode {
            subviews.forEach { $0.isHidden = true }
            currentId = tab.id
            return
        }
        let webView = webView(for: tab, model: model)
        if currentId != tab.id || webView.superview !== self {
            subviews.forEach { $0.removeFromSuperview() }
            addSubview(webView)
            webView.frame = bounds
            webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            currentId = tab.id
            coordinator?.attachProgress(webView)
        }
        webView.isHidden = false
        applySettings(webView, tab: tab, model: model)
        let live = webView.url?.absoluteString ?? ""
        if !tab.url.isEmpty, live != tab.url, !live.hasPrefix("about:") {
            // Keep in sync only when the model URL is a distinct navigation, not a redirect race.
        }
        dropClosedTabs(model)
    }

    func handle(_ kind: WebCommandKind, model: BrowserViewModel) {
        let tab = model.activeTab
        let webView = webView(for: tab, model: model)
        switch kind {
        case .load(let urlString):
            if let url = URL(string: urlString) {
                if url.isFileURL {
                    if let html = try? String(contentsOf: url, encoding: .utf8) {
                        webView.loadHTMLString(html, baseURL: url.deletingLastPathComponent())
                    } else {
                        webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
                    }
                } else {
                    webView.load(URLRequest(url: url))
                }
            }
        case .loadHTML(let html, let baseURL):
            webView.loadHTMLString(html, baseURL: URL(string: baseURL))
        case .reload:
            webView.reload()
        case .back:
            if webView.canGoBack {
                webView.goBack()
            } else {
                DispatchQueue.main.async { model.navigateHome() }
            }
        case .forward:
            if webView.canGoForward { webView.goForward() }
        case .extractReader:
            webView.evaluateJavaScript(AdBlocker.readerJS) { result, _ in
                let raw = result as? String ?? ""
                DispatchQueue.main.async {
                    model.onReaderArticle(Self.parseReader(raw, fallbackTitle: tab.title, fallbackUrl: tab.url), tabId: tab.id)
                }
            }
        case .autofill(let username, let password):
            let js = AdBlocker.buildAutofillJs(username: username, password: password)
            webView.evaluateJavaScript(js, completionHandler: nil)
        }
    }

    private func webView(for tab: BrowserTab, model: BrowserViewModel) -> WKWebView {
        if let existing = pool[tab.id] { return existing }
        let config = WKWebViewConfiguration()
        config.websiteDataStore = tab.isIncognito ? incognitoStore : regularStore
        config.allowsInlineMediaPlayback = true
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.preferences.javaScriptCanOpenWindowsAutomatically = true
        if let list = ContentBlockerStore.shared.list, model.preferences.adBlockEnabled {
            let domain = AdBlocker.extractDomain(tab.url)
            let whitelisted = model.preferences.whitelistedDomains.contains(where: { domain == $0 || domain.hasSuffix(".\($0)") })
            if !whitelisted {
                config.userContentController.add(list)
            }
        }
        let webView = WKWebView(frame: bounds, configuration: config)
        webView.navigationDelegate = coordinator
        webView.uiDelegate = coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.keyboardDismissMode = .interactive
        webView.backgroundColor = .clear
        webView.isOpaque = false
        applySettings(webView, tab: tab, model: model)
        pool[tab.id] = webView
        return webView
    }

    private func applySettings(_ webView: WKWebView, tab: BrowserTab, model: BrowserViewModel) {
        webView.customUserAgent = tab.isDesktopMode ? desktopUA : mobileUA
        if #available(iOS 13.0, *) {
            webView.configuration.defaultWebpagePreferences.preferredContentMode = tab.isDesktopMode ? .desktop : .mobile
        }
        webView.configuration.userContentController.removeAllContentRuleLists()
        let domain = AdBlocker.extractDomain(tab.url)
        let whitelisted = model.preferences.whitelistedDomains.contains(where: { domain == $0 || domain.hasSuffix(".\($0)") })
        if model.preferences.adBlockEnabled, !whitelisted, let list = ContentBlockerStore.shared.list {
            webView.configuration.userContentController.add(list)
        }
    }

    private func dropClosedTabs(_ model: BrowserViewModel) {
        let ids = Set(model.tabs.map(\.id))
        for key in pool.keys where !ids.contains(key) {
            pool[key]?.stopLoading()
            pool[key]?.removeFromSuperview()
            pool[key] = nil
        }
    }

    private static func parseReader(_ raw: String, fallbackTitle: String, fallbackUrl: String) -> ReaderArticle {
        let domain = AdBlocker.extractDomain(fallbackUrl)
        let fallback = ReaderArticle(
            title: fallbackTitle.isEmpty ? domain : fallbackTitle,
            domain: domain.isEmpty ? "neoserf.reader" : domain,
            byline: domain,
            paragraphs: [],
            estimatedMinutes: 1
        )
        guard let data = raw.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return fallback
        }
        let title = (obj["title"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? fallback.title
        let parsedDomain = (obj["domain"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? fallback.domain
        let byline = (obj["byline"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? parsedDomain
        let paragraphs = (obj["paragraphs"] as? [String])?.filter { !$0.isEmpty } ?? []
        let words = paragraphs.joined(separator: " ").split { $0.isWhitespace }.count
        return ReaderArticle(
            title: title,
            domain: parsedDomain,
            byline: byline,
            paragraphs: paragraphs,
            estimatedMinutes: max(1, words / 180)
        )
    }
}
