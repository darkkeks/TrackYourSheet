package ru.darkkeks.trackyoursheet.prototype.telegram

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.prototype.NewRangeState

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
open class CallbackButton(val _id: Id<CallbackButton> = newId()) {
    val stringId
        get() = _id.toString()
}

open class StatefulButton(val state: MessageState) : CallbackButton()

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class MessageState {

    private val createdButtons = mutableListOf<StatefulButton>()

    fun button(text: String, button: StatefulButton): InlineKeyboardButton {
        createdButtons.add(button)
        return InlineKeyboardButton(text).callbackData(button.stringId)
    }

    abstract fun draw(): MessageRender

    abstract suspend fun handleButton(context: CallbackButtonContext)

    fun persistButtons(buttonManager: ButtonManager) {
        createdButtons.forEach {
            buttonManager.put(it.stringId, it)
        }
        createdButtons.clear()
    }

    suspend fun send(context: UserActionContext) {
        val render = draw()
        context.reply(render.text, replyMarkup = render.keyboard)
        persistButtons(context.controller.buttonManager)
    }

    suspend fun changeState(newState: MessageState, context: CallbackButtonContext) {
        val render = newState.draw()
        context.editMessage(render.text, render.keyboard)
        newState.persistButtons(context.controller.buttonManager)
    }

}

data class MessageRender(val text: String, val keyboard: InlineKeyboardMarkup)

class MainMenuState : MessageState() {
    class CreateNewRangeButton(state: MessageState) : StatefulButton(state)
    class ListRangesButton(state: MessageState) : StatefulButton(state)
    class SettingsButton(state: MessageState) : StatefulButton(state)

    override fun draw() = MessageRender("""
        Привет, я умею наблюдать за гугл-табличками. ${"\uD83E\uDD13"}
        
        Давай будем называть _ренжем_ (_ренж_, _ренжик_, в _ренже_, _ренжику_, от англ. _range_) диапазон не некотором листе в гугл таблице. 
        Я не смог придумать название лучше, если есть идеи -- всегда можно написать @darkkeks.
        
        Сюда еще хелпы надо, напишите @lodthe чтобы добавил))0)).
    """.trimIndent(), buildInlineKeyboard {
        row(button("*️⃣ Нам нужно больше ренжиков", CreateNewRangeButton(this@MainMenuState)))
        row(button("\uD83D\uDCDD Ренжики мои любимые", ListRangesButton(this@MainMenuState)))
        row(button("⚙️Настроечкикикики", SettingsButton(this@MainMenuState)))
    })

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is CreateNewRangeButton -> {
                val state = NewRangeState()
                context.controller.changeState(context.stateHolder, state)
                state.initiate(context)
            }
            is ListRangesButton -> {
                context.answerCallbackQuery("Тут должны были быть ваши ренжи")
            }
        }
    }
}


open class GlobalStateButton : CallbackButton()

class ButtonManager {
    private val buttons = mutableMapOf<String, CallbackButton>()

    fun get(id: String) = buttons[id]

    fun put(id: String, button: CallbackButton) {
        buttons[id] = button
    }
}