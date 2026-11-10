package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.WebBlockRule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsFilterEngineTest {
    private val engine = DnsFilterEngine()

    @Test
    fun `exact domain match blocks`() {
        val rules = listOf(rule("instagram.com"))
        assertTrue(engine.shouldBlock("instagram.com", rules))
    }

    @Test
    fun `subdomain blocks when blockSubdomains is true`() {
        val rules = listOf(rule("instagram.com", blockSubdomains = true))
        assertTrue(engine.shouldBlock("www.instagram.com", rules))
    }

    @Test
    fun `subdomain allows when blockSubdomains is false`() {
        val rules = listOf(rule("instagram.com", blockSubdomains = false))
        assertFalse(engine.shouldBlock("m.instagram.com", rules))
    }

    @Test
    fun `www counts as the same site even when blockSubdomains is false`() {
        val rules = listOf(WebBlockRule(domain = "instagram.com", blockSubdomains = false))
        assertTrue(engine.shouldBlock("www.instagram.com", rules))
        assertFalse(engine.shouldBlock("www2.instagram.com", rules))
    }

    @Test
    fun `deep subdomain blocks when blockSubdomains is true`() {
        val rules = listOf(rule("instagram.com", blockSubdomains = true))
        assertTrue(engine.shouldBlock("api.cdn.instagram.com", rules))
    }

    @Test
    fun `hostname with trailing dot is normalised`() {
        val rules = listOf(rule("instagram.com"))
        assertTrue(engine.shouldBlock("instagram.com.", rules))
    }

    @Test
    fun `uppercase hostname matches a lowercase rule exactly`() {
        val rules = listOf(rule("instagram.com"))
        assertTrue(engine.shouldBlock("Instagram.COM", rules))
    }

    @Test
    fun `mixed case subdomain blocks when blockSubdomains is true`() {
        val rules = listOf(rule("instagram.com", blockSubdomains = true))
        assertTrue(engine.shouldBlock("WWW.Instagram.com", rules))
    }

    @Test
    fun `partial suffix match does not block`() {
        val rules = listOf(rule("instagram.com"))
        assertFalse(engine.shouldBlock("notinstagram.com", rules))
    }

    @Test
    fun `unrelated hostname allows`() {
        val rules = listOf(rule("instagram.com"))
        assertFalse(engine.shouldBlock("twitter.com", rules))
    }

    @Test
    fun `empty rules list allows everything`() {
        assertFalse(engine.shouldBlock("instagram.com", emptyList()))
    }

    @Test
    fun `disabled rule does not block`() {
        val rules = listOf(rule("instagram.com", enabled = false))
        assertFalse(engine.shouldBlock("instagram.com", rules))
    }

    @Test
    fun `one matching rule in a list blocks`() {
        val rules =
            listOf(
                rule("twitter.com"),
                rule("instagram.com"),
                rule("reddit.com"),
            )
        assertTrue(engine.shouldBlock("instagram.com", rules))
    }

    @Test
    fun `no matching rule in a list allows`() {
        val rules =
            listOf(
                rule("twitter.com"),
                rule("reddit.com"),
            )
        assertFalse(engine.shouldBlock("instagram.com", rules))
    }

    @Test
    fun `mixed enabled and disabled rules — only enabled one matches — blocks`() {
        val rules =
            listOf(
                rule("instagram.com", enabled = false),
                rule("instagram.com", enabled = true),
            )
        assertTrue(engine.shouldBlock("instagram.com", rules))
    }

    private fun rule(
        domain: String,
        blockSubdomains: Boolean = true,
        enabled: Boolean = true,
    ) = WebBlockRule(domain = domain, blockSubdomains = blockSubdomains, enabled = enabled)
}
