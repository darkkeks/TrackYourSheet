package ru.darkkeks.trackyoursheet.prototype.telegram

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.pengrad.telegrambot.model.request.InlineKeyboardButton


@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
abstract class GlobalUserState(val parentState: GlobalUserState? = null) {

    open suspend fun handleMessage(context: UserActionContext): Boolean = false

    open suspend fun handleCommand(context: CommandContext): Boolean = false

    open suspend fun handleCallback(context: CallbackButtonContext) {}

    fun toParentState(context: UserActionContext) {
        if (parentState != null) {
            context.changeState(parentState)
        }
    }

    suspend fun button(text: String, context: UserActionContext, button: GlobalStateButton): InlineKeyboardButton {
        context.controller.sheetDao.saveButton(button)
        return InlineKeyboardButton(text).callbackData(button.stringId)
    }
}

inline fun <T> GlobalUserState.handle(context: T, block: (T) -> Unit): Boolean {
    block(context)
    return true
}