package ru.darkkeks.trackyoursheet.v2

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

inline fun <reified T> createLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}

class ListBuilder<T> {
    private val result = mutableListOf<T>()

    fun add(vararg values: T) {
        values.forEach {
            result.add(it)
        }
    }

    fun build() = result.toList()
}

inline fun <T> buildList(block: ListBuilder<T>.() -> Unit): List<T> {
    val builder = ListBuilder<T>()
    builder.block()
    return builder.build()
}

val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    .withLocale(Locale("ru"))
    .withZone(TimeZone.getTimeZone("Europe/Moscow").toZoneId())

fun formatTime(time: Instant) = formatter.format(time)
