package com.focusguard.core.system.service

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Minimal Activity that gives the instrumented tests a real window to generate accessibility events. */
class AccessibilityProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "probe"
                contentDescription = "probe"
                // Selected, so selectedNodeSignals() picks it up.
                isSelected = true
            },
        )
    }
}
