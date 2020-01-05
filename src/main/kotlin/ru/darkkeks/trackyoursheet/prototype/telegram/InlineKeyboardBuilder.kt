package ru.darkkeks.trackyoursheet.prototype.telegram

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup

class InlineKeyboardBuilder {
    private val rows: MutableList<MutableList<InlineKeyboardButton>> = mutableListOf(mutableListOf())

    fun add(button: InlineKeyboardButton) = rows.last().add(button)

    fun row(button: InlineKeyboardButton) {
        newRow()
        add(button)
        newRow()
    }

    fun newRow() {
        if (rows.last().isNotEmpty()) {
            rows.add(mutableListOf())
        }
    }

    fun build(): InlineKeyboardMarkup {
        val rowArray = rows
            .filter { it.size > 0 }
            .map { it.toTypedArray() }
            .toTypedArray()
        return InlineKeyboardMarkup(*rowArray)
    }
}

inline fun buildInlineKeyboard(block: InlineKeyboardBuilder.() -> Unit): InlineKeyboardMarkup {
    val keyboard = InlineKeyboardBuilder()
    keyboard.block()
    return keyboard.build()
}