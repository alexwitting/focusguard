package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.WebBlockRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebContentFilterEngineTest {
    private val engine = WebContentFilterEngine(DnsFilterEngine())

    private val rules =
        listOf(WebBlockRule(domain = "example.com", blockSubdomains = true, enabled = true))

    @Test
    fun `precise match on exact hostname from address bar`() {
        val result = engine.detect("https://example.com/path", rules)
        assertEquals("example.com", result?.domain)
    }

    @Test
    fun `precise match respects blockSubdomains`() {
        val result = engine.detect("www.example.com", rules)
        assertEquals("example.com", result?.domain)
    }

    @Test
    fun `precise match returns null when subdomain blocking disabled and not exact`() {
        val strict = listOf(WebBlockRule(domain = "example.com", blockSubdomains = false, enabled = true))
        val result = engine.detect("www.example.com", strict)
        assertNull(result)
    }

    @Test
    fun `precise match returns null for unrelated hostname`() {
        val result = engine.detect("other.com", rules)
        assertNull(result)
    }

    @Test
    fun `null address bar text never matches`() {
        assertNull(engine.detect(null, rules))
    }

    @Test
    fun `disabled rules never match`() {
        val disabled = listOf(WebBlockRule(domain = "example.com", enabled = false))
        assertNull(engine.detect("example.com", disabled))
    }

    @Test
    fun `empty rules never match`() {
        assertNull(engine.detect("example.com", emptyList()))
    }

    @Test
    fun `looksLikeUrl accepts a bare hostname`() {
        assertTrue(WebContentFilterEngine.looksLikeUrl("example.com"))
    }

    @Test
    fun `looksLikeUrl accepts a full url with scheme and path`() {
        assertTrue(WebContentFilterEngine.looksLikeUrl("https://example.com/path?query=1"))
    }

    @Test
    fun `looksLikeUrl accepts a subdomain`() {
        assertTrue(WebContentFilterEngine.looksLikeUrl("www.example.com"))
    }

    @Test
    fun `looksLikeUrl rejects a sentence mentioning a domain`() {
        // This is exactly the shape of text that caused the old substring-scan false positives —
        // a domain mentioned in passing within ordinary prose, not sitting alone in a URL field.
        assertFalse(WebContentFilterEngine.looksLikeUrl("Visit example.com for more"))
    }

    @Test
    fun `looksLikeUrl rejects a plain word`() {
        assertFalse(WebContentFilterEngine.looksLikeUrl("Settings"))
    }

    @Test
    fun `looksLikeUrl rejects blank text`() {
        assertFalse(WebContentFilterEngine.looksLikeUrl("   "))
    }

    @Test
    fun `looksLikeUrl rejects text with no dot`() {
        assertFalse(WebContentFilterEngine.looksLikeUrl("localhost"))
    }
}
