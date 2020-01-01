package ru.darkkeks.trackyoursheet.prototype.telegram

import com.pengrad.telegrambot.model.request.InlineKeyboardButton


// TODO Persist user global state
abstract class GlobalUserState(private val parentState: GlobalUserState? = null) {

    open suspend fun handleMessage(context: UserActionContext): Boolean = false

    open suspend fun handleCommand(context: CommandContext): Boolean = false

    open suspend fun handleCallback(context: CallbackButtonContext) {}

    fun toParentState(context: UserActionContext) {
        if (parentState != null) {
            context.changeState(parentState)
        }
    }

    fun button(text: String, context: UserActionContext, button: GlobalStateButton): InlineKeyboardButton {
        context.controller.buttonManager.put(button.stringId, button)
        return InlineKeyboardButton(text).callbackData(button.stringId)
    }
}

inline fun <T> GlobalUserState.handle(context: T, block: (T) -> Unit): Boolean {
    block(context)
    return true
}