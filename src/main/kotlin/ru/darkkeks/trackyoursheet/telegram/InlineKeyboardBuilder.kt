package ru.darkkeks.trackyoursheet.telegram

class InlineKeyboardBuilder {
    private val rows: MutableList<MutableList<CallbackButton>> = mutableListOf(mutableListOf())

    fun add(button: CallbackButton) = rows.last().add(button)

    fun row(button: CallbackButton) {
        newRow()
        add(button)
        newRow()
    }

    fun newRow() {
        if (rows.last().isNotEmpty()) {
            rows.add(mutableListOf())
        }
    }

    fun build(): List<List<CallbackButton>> = rows.filter { it.size > 0 }
}

inline fun buildInlineKeyboard(block: InlineKeyboardBuilder.() -> Unit): List<List<CallbackButton>> {
    val keyboard = InlineKeyboardBuilder()
    keyboard.block()
    return keyboard.build()
}