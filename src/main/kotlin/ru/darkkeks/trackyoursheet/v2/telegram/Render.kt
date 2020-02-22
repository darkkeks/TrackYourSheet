package ru.darkkeks.trackyoursheet.v2.telegram

import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup


abstract class MessageRender

class TextRender(val text: String, val keyboard: List<List<CallbackButton>>) : MessageRender() {
    fun getMarkup(state: MessageState, registry: Registry) = InlineKeyboardMarkup(*keyboard.map { row ->
        row.map { button ->
            val buffer = ButtonBuffer()
            registry.states.write(state, buffer)
            registry.buttons.write(button, buffer)
            val inlineButton = button.toInlineButton()
            inlineButton.callbackData(buffer.toString())
        }.toTypedArray()
    }.toTypedArray())
}

class ChangeStateRender(val state: MessageState) : MessageRender()
