package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.WebBlockRule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches a browser's address-bar text against website rules, for the accessibility service's
 * browser scan. A null [addressBarText] means no address bar is visible, so nothing is matched.
 */
@Singleton
class WebContentFilterEngine
    @Inject
    constructor(
        private val dnsFilterEngine: DnsFilterEngine,
    ) {
        fun detect(
            addressBarText: String?,
            rules: List<WebBlockRule>,
        ): WebBlockRule? {
            if (rules.isEmpty()) return null
            val hostname = addressBarText?.let(::extractHostname) ?: return null
            return rules.firstOrNull { it.enabled && dnsFilterEngine.shouldBlock(hostname, listOf(it)) }
        }

        private fun extractHostname(text: String): String {
            val withoutScheme = text.substringAfter("://", text)
            val end =
                withoutScheme
                    .indexOfFirst { it == '/' || it == '?' || it == '#' || it.isWhitespace() }
                    .let { if (it == -1) withoutScheme.length else it }
            return withoutScheme.substring(0, end).trim().lowercase()
        }

        companion object {
            /**
             * True for text shaped like a URL or hostname (no spaces, dotted host, letter TLD).
             * Used to pick out an address bar in browsers with no known address-bar id.
             */
            fun looksLikeUrl(text: String): Boolean {
                val trimmed = text.trim()
                if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
                val withoutScheme = trimmed.substringAfter("://", trimmed)
                val host = withoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")
                if (host.length < 4 || '.' !in host) return false
                val labels = host.split('.')
                if (labels.size < 2 || labels.any { it.isEmpty() }) return false
                val tld = labels.last()
                return tld.length in 2..24 && tld.all { it.isLetter() }
            }

            const val CHROME = "com.android.chrome"
            const val FIREFOX = "org.mozilla.firefox"
            const val SAMSUNG_INTERNET = "com.sec.android.app.sbrowser"
            const val EDGE = "com.microsoft.emmx"

            val KNOWN_BROWSERS =
                setOf(
                    CHROME,
                    FIREFOX,
                    SAMSUNG_INTERNET,
                    EDGE,
                    "com.brave.browser",
                    "com.opera.browser",
                    "com.duckduckgo.mobile.android",
                    "com.kiwibrowser.browser",
                )

            /**
             * Address-bar view ids per browser. Not a stable API; may change with browser updates.
             * Browsers not listed fall back to the [looksLikeUrl] heuristic.
             */
            val URL_BAR_RESOURCE_IDS =
                mapOf(
                    CHROME to "com.android.chrome:id/url_bar",
                    FIREFOX to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                    SAMSUNG_INTERNET to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                    EDGE to "com.microsoft.emmx:id/url_bar",
                )
        }
    }
