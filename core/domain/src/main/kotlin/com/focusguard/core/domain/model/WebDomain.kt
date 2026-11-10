package com.focusguard.core.domain.model

/** Turns whatever the user typed into the bare hostname a [WebBlockRule] stores. */
object WebDomain {
    private const val MAX_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    private val TLD = Regex("^([a-z]{2,63}|xn--[a-z0-9-]{2,59})$")

    /**
     * "https://www.Instagram.com/reels?x=1" → "instagram.com". Returns null when the input isn't
     * a usable hostname (spaces, no dot, an IP-less single label, bad characters). Rules store
     * hostnames only — a path or port in the stored value could never match a DNS query.
     */
    fun normalize(input: String): String? {
        var host = input.trim().lowercase()
        host = host.substringAfter("://", host)
        host = host.substringAfter('@')
        host = host.takeWhile { it != '/' && it != '?' && it != '#' }
        host = host.substringBefore(':')
        host = host.trimEnd('.').removePrefix("www.")
        return host.takeIf(::isValidHostname)
    }

    private fun isValidHostname(host: String): Boolean {
        if (host.isEmpty() || host.length > MAX_LENGTH) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        if (labels.any { it.length > MAX_LABEL_LENGTH || !LABEL.matches(it) }) return false
        return TLD.matches(labels.last())
    }
}
