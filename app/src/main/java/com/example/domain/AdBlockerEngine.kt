package com.example.domain

import android.net.Uri
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

enum class BlockCategory {
  ALLOWED,
  BLOCKED_AD,
  BLOCKED_TRACKER
}

object AdBlockerEngine {

  private val AD_DOMAINS = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.com",
    "pagead2.googlesyndication.com",
    "adnxs.com",
    "criteo.com",
    "criteo.net",
    "taboola.com",
    "outbrain.com",
    "popads.net",
    "popcash.net",
    "propellerads.com",
    "yandexadexchange.net",
    "an.yandex.ru",
    "awaps.yandex.ru",
    "ads.yahoo.com",
    "rubiconproject.com",
    "pubmatic.com",
    "openx.net",
    "moatads.com",
    "smartadserver.com",
    "adform.net",
    "teads.tv",
    "revcontent.com",
    "mgid.com",
    "adfox.ru",
    "target.my.com",
    "banners.adfox.ru",
    "ad.mail.ru",
    "reklama.mail.ru",
    "ads.twitter.com",
    "ads-api.twitter.com",
    "amazon-adsystem.com",
    "aax.amazon-adsystem.com",
    "adroll.com",
    "bidswitch.net",
    "casalemedia.com",
    "indexexchange.com",
    "lijit.com",
    "media.net",
    "sharethrough.com",
    "sovrn.com",
    "triplelift.com"
  )

  private val TRACKER_DOMAINS = setOf(
    "google-analytics.com",
    "googletagmanager.com",
    "analytics.google.com",
    "stats.g.doubleclick.net",
    "connect.facebook.net",
    "pixel.facebook.com",
    "mc.yandex.ru",
    "top-fwz1.mail.ru",
    "counter.yadro.ru",
    "hotjar.com",
    "static.hotjar.com",
    "mixpanel.com",
    "api.mixpanel.com",
    "segment.io",
    "api.segment.io",
    "scorecardresearch.com",
    "sb.scorecardresearch.com",
    "quantserve.com",
    "pixel.quantserve.com",
    "newrelic.com",
    "bam.nr-data.net",
    "clarity.ms",
    "bat.bing.com",
    "analytics.tiktok.com",
    "tr.snapchat.com",
    "ct.pinterest.com",
    "fullstory.com",
    "mouseflow.com",
    "amplitude.com",
    "api.amplitude.com",
    "bugsnag.com",
    "sentry-cdn.com"
  )

  private val AD_PATH_KEYWORDS = listOf(
    "/pagead/",
    "/adserver/",
    "/adframe",
    "/adbanner",
    "/popunder",
    "/sponsored_links",
    "/ads/banner"
  )

  fun extractDomain(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return try {
      val host = Uri.parse(url).host?.lowercase() ?: return ""
      host.removePrefix("www.")
    } catch (e: Exception) {
      ""
    }
  }

  fun classifyRequest(
    requestUrl: String,
    currentPageUrl: String?,
    isAdBlockEnabled: Boolean,
    whitelistedDomains: Set<String>,
  ): BlockCategory {
    if (!isAdBlockEnabled) return BlockCategory.ALLOWED

    val pageDomain = extractDomain(currentPageUrl)
    if (pageDomain.isNotEmpty() && whitelistedDomains.any { pageDomain == it || pageDomain.endsWith(".$it") }) {
      return BlockCategory.ALLOWED
    }

    val requestHost = extractDomain(requestUrl)
    if (requestHost.isEmpty()) return BlockCategory.ALLOWED

    if (TRACKER_DOMAINS.any { requestHost == it || requestHost.endsWith(".$it") }) {
      return BlockCategory.BLOCKED_TRACKER
    }

    if (AD_DOMAINS.any { requestHost == it || requestHost.endsWith(".$it") }) {
      return BlockCategory.BLOCKED_AD
    }

    val lowerUrl = requestUrl.lowercase()
    if (AD_PATH_KEYWORDS.any { lowerUrl.contains(it) }) {
      return BlockCategory.BLOCKED_AD
    }

    return BlockCategory.ALLOWED
  }

  fun createEmptyResourceResponse(): WebResourceResponse {
    return WebResourceResponse(
      "text/plain",
      "UTF-8",
      ByteArrayInputStream(ByteArray(0))
    )
  }

  const val COSMETIC_ADBLOCK_JS = """
    (function() {
      try {
        var styleId = 'neoserf-adblock-cosmetic';
        if (document.getElementById(styleId)) return;
        var style = document.createElement('style');
        style.id = styleId;
        style.innerHTML = [
          'ins.adsbygoogle',
          '[id^="google_ads_iframe"]',
          '[id*="yandex_rtb"]',
          '.ya-partner',
          '.ad-banner',
          '.ad-container',
          '.advertisement',
          '.sponsored-content',
          '.taboola-container',
          '.outbrain-widget',
          '[data-ad-client]',
          '[data-ad-slot]'
        ].join(', ') + ' { display: none !important; height: 0 !important; min-height: 0 !important; visibility: hidden !important; pointer-events: none !important; }';
        document.head.appendChild(style);
      } catch (e) {}
    })();
  """

  const val READER_EXTRACTION_JS = """
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
          var text = (nodes[i].innerText || '').replace(/\s+/g, ' ').trim();
          if (text.length >= 35 && !seen[text]) {
            seen[text] = true;
            paragraphs.push(text);
          }
          if (paragraphs.length >= 80) break;
        }
        if (paragraphs.length === 0 && document.body && document.body.innerText) {
          var rawLines = document.body.innerText.split('\n');
          for (var j = 0; j < rawLines.length; j++) {
            var line = rawLines[j].trim();
            if (line.length >= 45 && !seen[line]) {
              seen[line] = true;
              paragraphs.push(line);
            }
            if (paragraphs.length >= 50) break;
          }
        }
        return JSON.stringify({
          title: title,
          domain: window.location.hostname || '',
          byline: byline,
          paragraphs: paragraphs
        });
      } catch (e) {
        return JSON.stringify({
          title: document.title || 'Статья',
          domain: window.location.hostname || '',
          byline: '',
          paragraphs: []
        });
      }
    })();
  """

  fun parseReaderArticleJson(rawEvaluateResult: String?, fallbackTitle: String, fallbackUrl: String): com.example.data.model.ReaderArticle {
    val domain = extractDomain(fallbackUrl).ifEmpty { "neoserf.reader" }
    if (rawEvaluateResult.isNullOrBlank() || rawEvaluateResult == "null") {
      return com.example.data.model.ReaderArticle(
        title = fallbackTitle.ifBlank { domain },
        domain = domain,
        byline = domain,
        paragraphs = emptyList(),
        estimatedMinutes = 1
      )
    }
    return try {
      val unescaped = if (rawEvaluateResult.startsWith("\"") && rawEvaluateResult.endsWith("\"")) {
        org.json.JSONTokener(rawEvaluateResult).nextValue().toString()
      } else {
        rawEvaluateResult
      }
      val obj = JSONObject(unescaped)
      val title = obj.optString("title").ifBlank { fallbackTitle.ifBlank { domain } }
      val parsedDomain = obj.optString("domain").ifBlank { domain }
      val byline = obj.optString("byline").ifBlank { parsedDomain }
      val arr = obj.optJSONArray("paragraphs") ?: JSONArray()
      val list = mutableListOf<String>()
      var wordCount = 0
      for (i in 0 until arr.length()) {
        val p = arr.optString(i).trim()
        if (p.isNotEmpty()) {
          list.add(p)
          wordCount += p.split(Regex("\\s+")).size
        }
      }
      val minutes = (wordCount / 180).coerceAtLeast(1)
      com.example.data.model.ReaderArticle(
        title = title,
        domain = parsedDomain,
        byline = byline,
        paragraphs = list,
        estimatedMinutes = minutes
      )
    } catch (e: Exception) {
      com.example.data.model.ReaderArticle(
        title = fallbackTitle.ifBlank { domain },
        domain = domain,
        byline = domain,
        paragraphs = emptyList(),
        estimatedMinutes = 1
      )
    }
  }

  fun buildAutofillJs(username: String, plainPassword: String): String {
    val safeUser = JSONObject.quote(username)
    val safePass = JSONObject.quote(plainPassword)
    return """
      (function() {
        try {
          var userVal = $safeUser;
          var passVal = $safePass;
          var passInputs = document.querySelectorAll('input[type="password"]');
          var userInputs = document.querySelectorAll('input[type="email"], input[type="text"], input[name*="user"], input[name*="login"], input[name*="email"]');
          function setNativeValue(el, value) {
            el.focus();
            el.value = value;
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
          }
          for (var i = 0; i < userInputs.length; i++) {
            if (userInputs[i].offsetParent !== null) {
              setNativeValue(userInputs[i], userVal);
              break;
            }
          }
          for (var j = 0; j < passInputs.length; j++) {
            if (passInputs[j].offsetParent !== null) {
              setNativeValue(passInputs[j], passVal);
              break;
            }
          }
        } catch (e) {}
      })();
    """.trimIndent()
  }
}
