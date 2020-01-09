package ru.darkkeks.trackyoursheet.prototype.states.menu

import ru.darkkeks.trackyoursheet.prototype.states.NewRangeState
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class MainMenuState : MessageState() {
    class CreateNewRangeButton(state: MessageState) : StatefulButton(state)
    class ListRangesButton(state: MessageState) : StatefulButton(state)
    class SettingsButton(state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext) = TextRender("""
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
                context.controller.changeState(context.userId, state)
                state.initiate(context)
            }
            is ListRangesButton -> {
                changeState(RangeListState(), context)
            }
        }
    }
}