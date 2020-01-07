package ru.darkkeks.trackyoursheet.prototype.telegram

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.prototype.SheetTrackDao

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
open class CallbackButton(val _id: Id<CallbackButton> = newId()) {
    val stringId
        @JsonIgnore
        get() = _id.toString()
}

open class StatefulButton(val state: MessageState) : CallbackButton()

open class GlobalStateButton : CallbackButton()


class GoBackButton(state: MessageState) : StatefulButton(state)

fun MessageState.goBackButton() = button("◀ Назад", GoBackButton(this))


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class MessageState {

    @JsonIgnore
    private val createdButtons = mutableListOf<StatefulButton>()

    fun button(text: String, button: StatefulButton): InlineKeyboardButton {
        createdButtons.add(button)
        return InlineKeyboardButton(text).callbackData(button.stringId)
    }

    abstract suspend fun draw(context: UserActionContext): MessageRender

    abstract suspend fun handleButton(context: CallbackButtonContext)

    private suspend fun persistButtons(sheetDao: SheetTrackDao) {
        createdButtons.forEach {
            sheetDao.saveButton(it)
        }
        createdButtons.clear()
    }

    suspend fun send(context: UserActionContext) {
        when (val render = draw(context)) {
            is ChangeStateRender -> render.state.send(context)
            is TextRender -> {
                context.reply(render.text, replyMarkup = render.keyboard)
                persistButtons(context.controller.sheetDao)
            }
        }
    }

    suspend fun changeState(newState: MessageState, context: CallbackButtonContext) {
        when (val render = newState.draw(context)) {
            is ChangeStateRender -> changeState(render.state, context)
            is TextRender -> {
                context.editMessage(render.text, replyMarkup = render.keyboard)
                newState.persistButtons(context.controller.sheetDao)
            }
        }
    }

    fun hijackGlobalState(context: UserActionContext, stateFromParentState: (GlobalUserState) -> GlobalUserState) {
        val currentState = context.controller.getUserState(context.stateHolder)
            ?: throw IllegalStateException("No user global state")
        val newState = stateFromParentState.invoke(currentState)
        context.controller.changeState(context.stateHolder, newState)
    }
}

abstract class MessageRender

class TextRender(val text: String, val keyboard: InlineKeyboardMarkup) : MessageRender()

class ChangeStateRender(val state: MessageState) : MessageRender()