package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects short-form feeds (Reels, Shorts, TikTok For You) from accessibility signals, and
 * lists the hosts the VPN blocks for each filter. Detection is heuristic and depends on each
 * app's current UI.
 */
@Singleton
class ContentFilterEngine
    @Inject
    constructor() {
        data class Detection(
            val filter: ContentFilter,
            val label: String,
        )

        /**
         * A [Detection] if [className] or [nodeSignals] (view ids and descriptions of selected
         * nodes) show an active feed for one of [activeFilters]. Node signals catch in-app tab
         * changes that don't change the Activity.
         */
        fun detect(
            packageName: String,
            className: String?,
            activeFilters: Set<ContentFilter>,
            nodeSignals: Set<String> = emptySet(),
        ): Detection? {
            if (activeFilters.isEmpty()) return null
            val signals = nodeSignals + listOfNotNull(className)
            return when (packageName) {
                INSTAGRAM ->
                    if (ContentFilter.REELS in activeFilters && signals.any(::isReelsSignal)) {
                        Detection(ContentFilter.REELS, "Instagram Reels")
                    } else {
                        null
                    }

                YOUTUBE ->
                    if (ContentFilter.SHORTS in activeFilters && signals.any(::isShortsSignal)) {
                        Detection(ContentFilter.SHORTS, "YouTube Shorts")
                    } else {
                        null
                    }

                TIKTOK, TIKTOK_GLOBAL ->
                    if (ContentFilter.TIKTOK_FYP in activeFilters) {
                        Detection(ContentFilter.TIKTOK_FYP, "TikTok")
                    } else {
                        null
                    }

                else -> null
            }
        }

        /** Exact-match DNS rules for the hosts behind [activeFilters]. */
        fun dnsRulesFor(activeFilters: Set<ContentFilter>): List<WebBlockRule> =
            activeFilters.flatMap { filter ->
                domainsFor(filter).map { domain ->
                    WebBlockRule(domain = domain, blockSubdomains = false, enabled = true)
                }
            }

        private fun isReelsSignal(signal: String): Boolean =
            signal.contains("reel", ignoreCase = true) ||
                signal.contains("clips", ignoreCase = true)

        private fun isShortsSignal(signal: String): Boolean =
            signal.contains("shorts", ignoreCase = true) ||
                signal.contains("reelwatch", ignoreCase = true)

        companion object {
            const val INSTAGRAM = "com.instagram.android"
            const val YOUTUBE = "com.google.android.youtube"
            const val TIKTOK = "com.zhiliaoapp.musically"
            const val TIKTOK_GLOBAL = "com.ss.android.ugc.trill"

            private val TRACKED_PACKAGES = setOf(INSTAGRAM, YOUTUBE, TIKTOK, TIKTOK_GLOBAL)

            /** Whether [packageName] is one this engine has short-form-content rules for. */
            fun isTrackedPackage(packageName: String): Boolean = packageName in TRACKED_PACKAGES

            fun domainsFor(filter: ContentFilter): List<String> =
                when (filter) {
                    // Reels share Instagram's API and media hosts with the rest of the app, so
                    // they're only blocked by the accessibility service.
                    ContentFilter.REELS -> emptyList()
                    ContentFilter.SHORTS ->
                        listOf(
                            "reel.youtube.com",
                        )
                    ContentFilter.TIKTOK_FYP ->
                        listOf(
                            "api.tiktok.com",
                            "api16-normal-c-useast1a.tiktokv.com",
                            "v19-webapp.tiktok.com",
                        )
                }
        }
    }
