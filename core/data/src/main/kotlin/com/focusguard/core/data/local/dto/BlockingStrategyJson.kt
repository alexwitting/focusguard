package com.focusguard.core.data.local.dto

import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek

@Serializable
internal sealed class BlockingStrategyJson {
    @Serializable
    @SerialName("always")
    internal data object AlwaysBlock : BlockingStrategyJson()

    @Serializable
    @SerialName("scheduled")
    internal data class ScheduledBlock(
        val windows: List<TimeWindowJson>,
    ) : BlockingStrategyJson()

    @Serializable
    @SerialName("time_limit")
    internal data class TimeLimit(
        val dailyLimitMinutes: Int,
    ) : BlockingStrategyJson()
}

@Serializable
internal data class TimeWindowJson(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: List<Int>,
)

internal val dataJson = Json { ignoreUnknownKeys = true }

internal fun BlockingStrategy.toJson(): String = dataJson.encodeToString(toStrategyDto())

internal fun String.toBlockingStrategy(): BlockingStrategy = dataJson.decodeFromString<BlockingStrategyJson>(this).toDomain()

internal fun TimeWindow.toJson(): String = dataJson.encodeToString(toWindowDto())

internal fun String.toTimeWindow(): TimeWindow = dataJson.decodeFromString<TimeWindowJson>(this).toDomain()

private fun BlockingStrategy.toStrategyDto(): BlockingStrategyJson =
    when (this) {
        is BlockingStrategy.AlwaysBlock -> BlockingStrategyJson.AlwaysBlock
        is BlockingStrategy.ScheduledBlock -> BlockingStrategyJson.ScheduledBlock(windows.map { it.toWindowDto() })
        is BlockingStrategy.TimeLimit -> BlockingStrategyJson.TimeLimit(dailyLimitMinutes)
    }

internal fun TimeWindow.toWindowDto() =
    TimeWindowJson(
        startHour,
        startMinute,
        endHour,
        endMinute,
        days.map { it.value },
    )

private fun BlockingStrategyJson.toDomain(): BlockingStrategy =
    when (this) {
        is BlockingStrategyJson.AlwaysBlock -> BlockingStrategy.AlwaysBlock
        is BlockingStrategyJson.ScheduledBlock -> BlockingStrategy.ScheduledBlock(windows.map { it.toDomain() })
        is BlockingStrategyJson.TimeLimit -> BlockingStrategy.TimeLimit(dailyLimitMinutes)
    }

internal fun TimeWindowJson.toDomain() =
    TimeWindow(
        startHour,
        startMinute,
        endHour,
        endMinute,
        days.map { DayOfWeek.of(it) }.toSet(),
    )
