package ru.darkkeks.trackyoursheet.prototype.telegram

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import org.litote.kmongo.Id
import org.litote.kmongo.newId

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
open class CallbackButton(val _id: Id<CallbackButton> = newId()) {
    val stringId
        get() = _id.toString()
}

open class StatefulButton(val state: MessageState) : CallbackButton()

open class GlobalStateButton : CallbackButton()

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class MessageState {

    private val createdButtons = mutableListOf<StatefulButton>()

    fun button(text: String, button: StatefulButton): InlineKeyboardButton {
        createdButtons.add(button)
        return InlineKeyboardButton(text).callbackData(button.stringId)
    }

    abstract suspend fun draw(context: UserActionContext): MessageRender

    abstract suspend fun handleButton(context: CallbackButtonContext)

    private fun persistButtons(buttonManager: ButtonManager) {
        createdButtons.forEach {
            buttonManager.put(it.stringId, it)
        }
        createdButtons.clear()
    }

    suspend fun send(context: UserActionContext) {
        val render = draw(context)
        context.reply(render.text, replyMarkup = render.keyboard)
        persistButtons(context.controller.buttonManager)
    }

    suspend fun changeState(newState: MessageState, context: CallbackButtonContext) {
        val render = newState.draw(context)
        context.editMessage(render.text, render.keyboard)
        newState.persistButtons(context.controller.buttonManager)
    }

}


data class MessageRender(val text: String, val keyboard: InlineKeyboardMarkup)

// TODO Persist buttons
class ButtonManager {
    private val buttons = mutableMapOf<String, CallbackButton>()

    fun get(id: String) = buttons[id]

    fun put(id: String, button: CallbackButton) {
        buttons[id] = button
    }
}