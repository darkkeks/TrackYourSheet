package ru.darkkeks.trackyoursheet.prototype

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> createLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}