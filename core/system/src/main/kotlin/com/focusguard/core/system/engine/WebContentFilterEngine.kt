package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.WebBlockRule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin — no Android imports. Second website-blocking backend alongside the DNS-VPN path
 * (DnsVpnService), for standalone use or when another VPN app is already active. The
 * accessibility service walks the on-screen node tree (Android-specific) and hands this engine
 * a single best-effort address-bar string to match against the same web-block rules the VPN
 * path uses.
 *
 * Deliberately does NOT fall back to scanning arbitrary visible screen text when the address bar
 * can't be confirmed (e.g. a browser's tab-switcher grid, or a captcha/permission dialog — states
 * where the address bar legitimately isn't present at all). That used to be the fallback tier
 * here, and it caused real false positives: a blocked domain showing up as a tab-preview title,
 * or coincidentally as unrelated dialog copy, would trigger a block even though the user wasn't
 * actually on that page. [addressBarText] being null means "not confirmed to be viewing a page
 * right now" — the caller is responsible for producing it from a genuine address-bar signal only
 * (see FocusGuardAccessibilityService.findHeuristicAddressBarText for the unmapped-browser case).
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
             * Best-effort filter for FocusGuardAccessibilityService.findHeuristicAddressBarText
             * (the unmapped-browser fallback): true only for text shaped like a bare URL/hostname
             * — no whitespace, a dot-separated host with a plausible TLD — not ordinary prose.
             * Deliberately conservative: a false negative here just means no coverage for that
             * unmapped browser, while a false positive would reintroduce blind-substring-style
             * false blocks (a tab-preview title or dialog copy mentioning a domain in passing).
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
             * Best-effort — these resource-ids are not a stable API and will drift across
             * browser updates. Browsers not listed here get the [looksLikeUrl]-filtered
             * heuristic scan instead (see FocusGuardAccessibilityService); a browser listed here
             * whose id isn't currently found gets no detection at all for that event (see
             * [detect]'s doc comment for why).
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
