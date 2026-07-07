package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ContentFilterEngineTest {
    private val engine = ContentFilterEngine()

    // ── detect() — no active filters ─────────────────────────────────────────

    @Test
    fun `detect returns null when active filters empty`() {
        assertNull(engine.detect(ContentFilterEngine.INSTAGRAM, "com.instagram.ReelViewerActivity", emptySet()))
    }

    // ── detect() — Instagram Reels ────────────────────────────────────────────

    @Test
    fun `detect returns Reels for Instagram with reel class name`() {
        val result =
            engine.detect(
                ContentFilterEngine.INSTAGRAM,
                "com.instagram.mainactivity.ReelViewerActivity",
                setOf(ContentFilter.REELS),
            )
        assertNotNull(result)
        assertEquals(ContentFilter.REELS, result!!.filter)
    }

    @Test
    fun `detect returns Reels for Instagram with clips in class name`() {
        val result =
            engine.detect(
                ContentFilterEngine.INSTAGRAM,
                "com.instagram.ClipsViewerFragment",
                setOf(ContentFilter.REELS),
            )
        assertNotNull(result)
        assertEquals(ContentFilter.REELS, result!!.filter)
    }

    @Test
    fun `detect returns null for Instagram Stories class when only Reels filter active`() {
        val result =
            engine.detect(
                ContentFilterEngine.INSTAGRAM,
                "com.instagram.story.StoryViewerActivity",
                setOf(ContentFilter.REELS),
            )
        assertNull(result)
    }

    @Test
    fun `detect returns Reels via node signal when class name is generic MainActivity`() {
        val result =
            engine.detect(
                ContentFilterEngine.INSTAGRAM,
                "com.instagram.mainactivity.MainActivity",
                setOf(ContentFilter.REELS),
                nodeSignals = setOf("com.instagram.android:id/clips_tab", "Reels"),
            )
        assertNotNull(result)
        assertEquals(ContentFilter.REELS, result!!.filter)
    }

    @Test
    fun `detect returns null when neither class name nor node signals mention reels`() {
        val result =
            engine.detect(
                ContentFilterEngine.INSTAGRAM,
                "com.instagram.mainactivity.MainActivity",
                setOf(ContentFilter.REELS),
                nodeSignals = setOf("com.instagram.android:id/feed_tab", "Home"),
            )
        assertNull(result)
    }

    @Test
    fun `detect returns null for Instagram when Reels filter not active`() {
        val result =
            engine.detect(
                ContentFilterEngine.INSTAGRAM,
                "com.instagram.mainactivity.ReelViewerActivity",
                setOf(ContentFilter.SHORTS),
            )
        assertNull(result)
    }

    // ── detect() — YouTube Shorts ─────────────────────────────────────────────

    @Test
    fun `detect returns Shorts for YouTube with shorts in class name`() {
        val result =
            engine.detect(
                ContentFilterEngine.YOUTUBE,
                "com.google.android.youtube.ui.shortsvideo.ShortsFragment",
                setOf(ContentFilter.SHORTS),
            )
        assertNotNull(result)
        assertEquals(ContentFilter.SHORTS, result!!.filter)
    }

    @Test
    fun `detect returns Shorts for YouTube with ReelWatch in class name`() {
        val result =
            engine.detect(
                ContentFilterEngine.YOUTUBE,
                "com.google.android.youtube.ReelWatchFragment",
                setOf(ContentFilter.SHORTS),
            )
        assertNotNull(result)
        assertEquals(ContentFilter.SHORTS, result!!.filter)
    }

    @Test
    fun `detect returns null for YouTube home class when Shorts filter active`() {
        val result =
            engine.detect(
                ContentFilterEngine.YOUTUBE,
                "com.google.android.youtube.HomeActivity",
                setOf(ContentFilter.SHORTS),
            )
        assertNull(result)
    }

    @Test
    fun `detect returns null for YouTube when Shorts filter not active`() {
        val result =
            engine.detect(
                ContentFilterEngine.YOUTUBE,
                "com.google.android.youtube.ShortsFragment",
                setOf(ContentFilter.REELS),
            )
        assertNull(result)
    }

    // ── detect() — TikTok ─────────────────────────────────────────────────────

    @Test
    fun `detect returns TikTok for musically package when filter active`() {
        val result =
            engine.detect(
                ContentFilterEngine.TIKTOK,
                "com.zhiliaoapp.musically.SomeActivity",
                setOf(ContentFilter.TIKTOK_FYP),
            )
        assertNotNull(result)
        assertEquals(ContentFilter.TIKTOK_FYP, result!!.filter)
    }

    @Test
    fun `detect returns TikTok for trill package when filter active`() {
        val result =
            engine.detect(
                ContentFilterEngine.TIKTOK_GLOBAL,
                null,
                setOf(ContentFilter.TIKTOK_FYP),
            )
        assertNotNull(result)
        assertEquals(ContentFilter.TIKTOK_FYP, result!!.filter)
    }

    @Test
    fun `detect returns null for TikTok when filter not active`() {
        assertNull(engine.detect(ContentFilterEngine.TIKTOK, null, setOf(ContentFilter.SHORTS)))
    }

    // ── detect() — unknown package ────────────────────────────────────────────

    @Test
    fun `detect returns null for unknown package regardless of filters`() {
        assertNull(engine.detect("com.some.other.app", null, ContentFilter.entries.toSet()))
    }

    // ── isTrackedPackage() ───────────────────────────────────────────────────

    @Test
    fun `isTrackedPackage returns true for known short-form apps`() {
        assert(ContentFilterEngine.isTrackedPackage(ContentFilterEngine.INSTAGRAM))
        assert(ContentFilterEngine.isTrackedPackage(ContentFilterEngine.YOUTUBE))
        assert(ContentFilterEngine.isTrackedPackage(ContentFilterEngine.TIKTOK))
        assert(ContentFilterEngine.isTrackedPackage(ContentFilterEngine.TIKTOK_GLOBAL))
    }

    @Test
    fun `isTrackedPackage returns false for unrelated package`() {
        assert(!ContentFilterEngine.isTrackedPackage("com.some.other.app"))
    }

    // ── dnsRulesFor() ─────────────────────────────────────────────────────────

    @Test
    fun `dnsRulesFor returns empty list for empty filters`() {
        assertEquals(emptyList<WebBlockRule>(), engine.dnsRulesFor(emptySet()))
    }

    @Test
    fun `dnsRulesFor Reels contains expected CDN hostnames`() {
        val rules = engine.dnsRulesFor(setOf(ContentFilter.REELS))
        val domains = rules.map { it.domain }
        assert("i.instagram.com" in domains)
        assert("scontent.cdninstagram.com" in domains)
        assert(rules.all { it.enabled })
        assert(rules.all { !it.blockSubdomains })
    }

    @Test
    fun `dnsRulesFor Shorts contains reel youtube com`() {
        val rules = engine.dnsRulesFor(setOf(ContentFilter.SHORTS))
        assert(rules.any { it.domain == "reel.youtube.com" })
    }

    @Test
    fun `dnsRulesFor TikTok contains known api endpoints`() {
        val rules = engine.dnsRulesFor(setOf(ContentFilter.TIKTOK_FYP))
        val domains = rules.map { it.domain }
        assert("api.tiktok.com" in domains)
        assert("v19-webapp.tiktok.com" in domains)
    }

    @Test
    fun `dnsRulesFor all filters returns combined list`() {
        val rules = engine.dnsRulesFor(ContentFilter.entries.toSet())
        val expected = ContentFilter.entries.sumOf { ContentFilterEngine.domainsFor(it).size }
        assertEquals(expected, rules.size)
    }
}
