package com.focusguard.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppDestinationsTest {
    @Test
    fun `top level destinations are Home, Block, Settings in that order`() {
        assertEquals(
            listOf(HomeRoute, BlockRoute, SettingsRoute),
            TOP_LEVEL_DESTINATIONS.map { it.route },
        )
    }

    @Test
    fun `top level destination labels match their route`() {
        assertEquals(
            listOf("Home", "Block", "Settings"),
            TOP_LEVEL_DESTINATIONS.map { it.label },
        )
    }
}
