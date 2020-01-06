package ru.darkkeks.trackyoursheet.prototype

import java.time.Duration

enum class TimeUnits(private val form1: String,
                     private val form2: String,
                     private val form3: String,
                     val getter: (Duration) -> Long) {

    SECOND("секунда", "секунды", "секунд", { it.toSecondsPart().toLong() }),
    MINUTE("минута", "минуты", "минут", { it.toMinutesPart().toLong() }),
    HOUR("час", "часа", "часов", { it.toHoursPart().toLong() }),
    DAY("день", "дня", "дней", { it.toDaysPart() });

    fun getCountForm(count: Long) = when {
        count in 11..14 -> form3
        count % 10 == 1L -> form1
        count % 10 in 2..4 -> form2
        else -> form3
    }

    companion object {
        fun durationToString(duration: Duration) = listOf(DAY, HOUR, MINUTE, SECOND)
            .filter { it.getter(duration) != 0L }.joinToString {
                val count = it.getter(duration)
                "$count ${it.getCountForm(count)}"
            }
    }
}