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
            // DNS hostnames are case-insensitive (RFC 4343); rule.domain is already normalized
            // to lowercase where rules are created (see BlockViewModel.addWeb()), but an
            // incoming query hostname isn't guaranteed to be.
            val normalized = hostname.trimEnd('.').lowercase()
            return rules.any { rule ->
                rule.enabled && matches(normalized, rule)
            }
        }

        private fun matches(
            hostname: String,
            rule: WebBlockRule,
        ): Boolean {
            // "www." is the same site to a person: a rule for x.com with subdomains off still
            // has to catch www.x.com, which is what browsers usually load.
            if (hostname == rule.domain || hostname == "www.${rule.domain}") return true
            if (rule.blockSubdomains && hostname.endsWith(".${rule.domain}")) return true
            return false
        }
    }
