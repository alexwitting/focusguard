package com.focusguard.core.system.service

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Minimal real Activity (no Compose/AppCompat dependency needed) used only by instrumented
 * tests to generate genuine `TYPE_WINDOW_STATE_CHANGED`/content-changed accessibility events
 * against a real rendered window — something Robolectric's simulated framework can't provide.
 */
class AccessibilityProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "probe"
                contentDescription = "probe"
                // collectNodeSignals() only reads *selected* nodes (see its header comment) —
                // this view must genuinely report isSelected via the real accessibility tree for
                // FocusGuardAccessibilityServiceInstrumentedTest's node-walk scenario to find it.
                isSelected = true
            },
        )
    }
}
