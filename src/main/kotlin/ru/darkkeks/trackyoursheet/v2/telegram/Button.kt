package ru.darkkeks.trackyoursheet.v2.telegram

import com.pengrad.telegrambot.model.request.InlineKeyboardButton

abstract class Button {
    abstract fun toInlineButton(): InlineKeyboardButton
}

abstract class CallbackButton : Button(), CallbackSerializable {
    abstract fun getText(): String
    override fun toInlineButton() = InlineKeyboardButton(getText())
    override fun serialize(buffer: ButtonBuffer) {}
}

open class TextButton(private val label: String): CallbackButton() {
    override fun getText() = label
}


class GoBackButton : TextButton("◀ Назад")