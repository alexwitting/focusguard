package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class WebDomainTest {
    @ParameterizedTest
    @CsvSource(
        "instagram.com, instagram.com",
        "  Instagram.COM  , instagram.com",
        "https://www.instagram.com/reels/, instagram.com",
        "http://news.ycombinator.com/item?id=1, news.ycombinator.com",
        "reddit.com:443, reddit.com",
        "user@example.org/path, example.org",
        "m.youtube.com., m.youtube.com",
        "bbc.co.uk#top, bbc.co.uk",
        "xn--80ak6aa92e.xn--p1ai, xn--80ak6aa92e.xn--p1ai",
    )
    fun `normalizes typed input to a bare hostname`(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, WebDomain.normalize(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "localhost", "instagram", "insta gram.com", "-bad.com", "bad-.com", "a..com", "site.c0m", "https://", "www."])
    fun `rejects input that isn't a usable hostname`(input: String) {
        assertNull(WebDomain.normalize(input))
    }

    @Test
    fun `rejects over-long labels and hostnames`() {
        assertNull(WebDomain.normalize("a".repeat(64) + ".com"))
        assertNull(WebDomain.normalize((1..60).joinToString(".") { "abcd" } + ".com"))
        assertEquals("a".repeat(63) + ".com", WebDomain.normalize("a".repeat(63) + ".com"))
    }
}
