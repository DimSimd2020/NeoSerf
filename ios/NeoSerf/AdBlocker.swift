import Foundation
import WebKit

enum AdBlocker {
    static let adDomains: [String] = [
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "adnxs.com",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "popads.net",
        "popcash.net", "propellerads.com", "yandexadexchange.net", "an.yandex.ru",
        "awaps.yandex.ru", "ads.yahoo.com", "rubiconproject.com", "pubmatic.com",
        "openx.net", "moatads.com", "smartadserver.com", "adform.net", "teads.tv",
        "revcontent.com", "mgid.com", "adfox.ru", "target.my.com", "banners.adfox.ru",
        "ad.mail.ru", "reklama.mail.ru", "ads.twitter.com", "ads-api.twitter.com",
        "amazon-adsystem.com", "aax.amazon-adsystem.com", "adroll.com",
        "bidswitch.net", "casalemedia.com", "indexexchange.com", "lijit.com",
        "media.net", "sharethrough.com", "sovrn.com", "triplelift.com"
    ]

    static let trackerDomains: [String] = [
        "google-analytics.com", "googletagmanager.com", "analytics.google.com",
        "stats.g.doubleclick.net", "connect.facebook.net", "pixel.facebook.com",
        "mc.yandex.ru", "top-fwz1.mail.ru", "counter.yadro.ru", "hotjar.com",
        "static.hotjar.com", "mixpanel.com", "api.mixpanel.com", "segment.io",
        "api.segment.io", "scorecardresearch.com", "sb.scorecardresearch.com",
        "quantserve.com", "pixel.quantserve.com", "newrelic.com", "bam.nr-data.net",
        "clarity.ms", "bat.bing.com", "analytics.tiktok.com", "tr.snapchat.com",
        "ct.pinterest.com", "fullstory.com", "mouseflow.com", "amplitude.com",
        "api.amplitude.com", "bugsnag.com", "sentry-cdn.com"
    ]

    static let cosmeticJS = """
    (function() {
      try {
        var styleId = 'neoserf-adblock-cosmetic';
        if (document.getElementById(styleId)) return;
        var style = document.createElement('style');
        style.id = styleId;
        style.innerHTML = [
          'ins.adsbygoogle','[id^="google_ads_iframe"]','[id*="yandex_rtb"]',
          '.ya-partner','.ad-banner','.ad-container','.advertisement',
          '.sponsored-content','.taboola-container','.outbrain-widget',
          '[data-ad-client]','[data-ad-slot]'
        ].join(', ') + ' { display: none !important; height: 0 !important; min-height: 0 !important; visibility: hidden !important; pointer-events: none !important; }';
        document.head.appendChild(style);
        var blocked = document.querySelectorAll(style.innerHTML.split(' {')[0]).length;
        return blocked;
      } catch (e) { return 0; }
    })();
    """

    static let readerJS = """
    (function() {
      try {
        var title = document.title || '';
        var h1 = document.querySelector('h1');
        if (h1 && h1.innerText && h1.innerText.trim().length > 4) {
          title = h1.innerText.trim();
        }
        var metaAuthor = document.querySelector('meta[name="author"], meta[property="article:author"], [rel="author"]');
        var byline = metaAuthor ? (metaAuthor.content || metaAuthor.innerText || '').trim() : window.location.hostname;
        var root = document.querySelector('article') || document.querySelector('main') || document.body;
        var nodes = root ? root.querySelectorAll('p, h2, h3, blockquote, li') : [];
        var paragraphs = [];
        var seen = {};
        for (var i = 0; i < nodes.length; i++) {
          var text = (nodes[i].innerText || '').replace(/\\s+/g, ' ').trim();
          if (text.length >= 35 && !seen[text]) {
            seen[text] = true;
            paragraphs.push(text);
          }
          if (paragraphs.length >= 80) break;
        }
        return JSON.stringify({
          title: title,
          domain: window.location.hostname || '',
          byline: byline,
          paragraphs: paragraphs
        });
      } catch (e) {
        return JSON.stringify({ title: document.title || 'Статья', domain: window.location.hostname || '', byline: '', paragraphs: [] });
      }
    })();
    """

    static func buildAutofillJs(username: String, password: String) -> String {
        let user = username.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "'", with: "\\'")
        let pass = password.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "'", with: "\\'")
        return """
        (function() {
          try {
            var user = '\(user)';
            var pass = '\(pass)';
            var inputs = Array.prototype.slice.call(document.querySelectorAll('input'));
            var userEl = inputs.find(function(el) {
              var t = (el.type || '').toLowerCase();
              var n = ((el.name || '') + (el.id || '') + (el.autocomplete || '')).toLowerCase();
              return t === 'email' || t === 'text' || n.indexOf('user') >= 0 || n.indexOf('login') >= 0 || n.indexOf('email') >= 0;
            });
            var passEl = inputs.find(function(el) { return (el.type || '').toLowerCase() === 'password'; });
            if (userEl) { userEl.focus(); userEl.value = user; userEl.dispatchEvent(new Event('input', { bubbles: true })); }
            if (passEl) { passEl.focus(); passEl.value = pass; passEl.dispatchEvent(new Event('input', { bubbles: true })); }
            return true;
          } catch (e) { return false; }
        })();
        """
    }

    static func extractDomain(_ urlString: String?) -> String {
        guard let urlString = urlString, !urlString.isEmpty,
              let url = URL(string: urlString), let host = url.host?.lowercased() else {
            return ""
        }
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }

    static func classify(requestUrl: String, pageUrl: String?, enabled: Bool, whitelist: [String]) -> BlockCategory {
        guard enabled else { return .allowed }
        let pageDomain = extractDomain(pageUrl)
        if !pageDomain.isEmpty, whitelist.contains(where: { pageDomain == $0 || pageDomain.hasSuffix(".\($0)") }) {
            return .allowed
        }
        let host = extractDomain(requestUrl)
        guard !host.isEmpty else { return .allowed }
        if trackerDomains.contains(where: { host == $0 || host.hasSuffix(".\($0)") }) { return .tracker }
        if adDomains.contains(where: { host == $0 || host.hasSuffix(".\($0)") }) { return .ad }
        let lower = requestUrl.lowercased()
        if ["/pagead/", "/adserver/", "/adframe", "/adbanner", "/popunder", "/sponsored_links", "/ads/banner"]
            .contains(where: { lower.contains($0) }) {
            return .ad
        }
        return .allowed
    }

    static func contentRuleJSON() -> String {
        var rules: [[String: Any]] = []
        for domain in adDomains + trackerDomains {
            let escaped = domain.replacingOccurrences(of: ".", with: "\\.")
            rules.append([
                "trigger": ["url-filter": escaped],
                "action": ["type": "block"]
            ])
        }
        let data = try? JSONSerialization.data(withJSONObject: rules, options: [])
        return String(data: data ?? Data("[]".utf8), encoding: .utf8) ?? "[]"
    }
}

final class ContentBlockerStore {
    static let shared = ContentBlockerStore()
    private(set) var list: WKContentRuleList?
    private var compiling = false

    func prepare() {
        if list != nil || compiling { return }
        compiling = true
        WKContentRuleListStore.default().compileContentRuleList(
            forIdentifier: "neoserf-ads-v1",
            encodedContentRuleList: AdBlocker.contentRuleJSON()
        ) { [weak self] list, _ in
            self?.list = list
            self?.compiling = false
        }
    }
}
