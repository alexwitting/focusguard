package com.focusguard.core.system.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.system.service.FocusGuardAccessibilityService

class TimeLimitAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: return
        val label = intent.getStringExtra(EXTRA_BLOCKED_LABEL) ?: pkg
        val reason =
            intent
                .getStringExtra(EXTRA_BLOCK_REASON)
                ?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }
                ?: BlockReason.TIME_LIMIT_REACHED

        // The accessibility service can bring the block screen to the front reliably.
        val service = FocusGuardAccessibilityService.instance
        if (service != null) {
            service.launchBlockedScreen(pkg, label, reason)
            return
        }

        // Direct start; Android may suppress it as a background activity launch.
        runCatching {
            val blockIntent =
                Intent().apply {
                    setClassName(context.packageName, "com.focusguard.BlockedActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_PACKAGE, pkg)
                    putExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL, label)
                    putExtra(FocusGuardAccessibilityService.EXTRA_BLOCK_REASON, reason.name)
                }
            context.startActivity(blockIntent)
        }
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_LABEL = "extra_blocked_label"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
    }
}
