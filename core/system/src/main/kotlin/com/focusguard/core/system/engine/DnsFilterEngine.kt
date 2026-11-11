package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.WebBlockRule
import javax.inject.Inject

class DnsFilterEngine
    @Inject
    constructor() {
        fun shouldBlock(
            hostname: String,
            rules: List<WebBlockRule>,
        ): Boolean {
            // Hostnames are case-insensitive; rule domains are stored lowercase.
            val normalized = hostname.trimEnd('.').lowercase()
            return rules.any { rule ->
                rule.enabled && matches(normalized, rule)
            }
        }

        private fun matches(
            hostname: String,
            rule: WebBlockRule,
        ): Boolean {
            // www.x.com is the same site as x.com, even with subdomains off.
            if (hostname == rule.domain || hostname == "www.${rule.domain}") return true
            if (rule.blockSubdomains && hostname.endsWith(".${rule.domain}")) return true
            return false
        }
    }
