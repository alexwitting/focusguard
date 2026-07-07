package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin — no Android imports. Determines whether an accessibility event indicates
 * the user is viewing short-form content, and provides CDN hostnames for DNS blocking.
 *
 * Detection is best-effort: class names are heuristic and will drift as apps update.
 * The DNS layer is the reliable fallback.
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
         * Returns a [Detection] if [packageName], [className], or [nodeSignals] indicate active
         * short-form content for one of the [activeFilters], or null if nothing matched.
         *
         * [nodeSignals] is view-id/content-description text pulled from the current window's
         * accessibility node tree. The top-level Activity class name alone doesn't change when
         * an app swaps fragments in place (e.g. Instagram's Reels tab), so [nodeSignals] is the
         * primary signal for that kind of in-app navigation — [className] alone catches apps
         * that do use a dedicated Activity/Fragment class per surface.
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

        /**
         * Returns synthesised [WebBlockRule]s for all [activeFilters] to be merged into the
         * DNS block set. Rules use exact domain matching (no subdomain wildcard) because the
         * CDN hostnames are already fully-qualified.
         */
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
                    ContentFilter.REELS ->
                        listOf(
                            "i.instagram.com",
                            "scontent.cdninstagram.com",
                        )
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
