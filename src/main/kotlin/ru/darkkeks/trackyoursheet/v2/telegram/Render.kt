package ru.darkkeks.trackyoursheet.v2.telegram

import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup


fun List<List<CallbackButton>>.getMarkup(state: MessageState, registry: Registry) = InlineKeyboardMarkup(*this.map { row ->
    row.map { button ->
        val buffer = ButtonBuffer()
        registry.states.write(state, buffer)
        registry.buttons.write(button, buffer)
        val inlineButton = button.toInlineButton()
        inlineButton.callbackData(buffer.serialize())
    }.toTypedArray()
}.toTypedArray())


abstract class MessageRender

class TextRender(val text: String, private val keyboard: List<List<CallbackButton>> = buildInlineKeyboard { }) : MessageRender() {
    fun getMarkup(state: MessageState, registry: Registry) = keyboard.getMarkup(state, registry)
}

class ChangeStateRender(val state: MessageState) : MessageRender()
