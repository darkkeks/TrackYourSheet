package ru.darkkeks.trackyoursheet.v2

import ru.darkkeks.trackyoursheet.v2.telegram.*

class DefaultState : GlobalState() {
    override val handlerList = buildHandlerList {
        command("new_range") {
            send("Her")
        }
        command("list_ranges") {
            send("Zalupa")
        }
        fallback {
            MainMenuState().send(this)
        }
    }
}

class MainMenuState: MessageState() {
    class CreateNewRangeButton : TextButton("*️⃣ Нам нужно больше ренжиков")
    class ListRangesButton : TextButton("\uD83D\uDCDD Ренжики мои любимые")
    class SettingsButton : TextButton("⚙️Настроечкикикики")

    override suspend fun draw(context: BaseContext) = TextRender("""
        Привет, я умею наблюдать за гугл-табличками. ${"\uD83E\uDD13"}
        
        Давай будем называть _ренжем_ (_ренж_, _ренжик_, в _ренже_, _ренжику_, от англ. _range_) диапазон не некотором листе в гугл таблице. 
        Я не смог придумать название лучше, если есть идеи -- всегда можно написать @darkkeks.
        
        Сюда еще хелпы надо, напишите @lodthe чтобы добавил))0)).
    """.trimIndent(), buildInlineKeyboard {
        row(CreateNewRangeButton())
        row(ListRangesButton())
        row(SettingsButton())
    })


    override val handlerList = buildHandlerList {
        callback<CreateNewRangeButton> { send("1") }
        callback<ListRangesButton> { send("2") }
        callback<SettingsButton> { send("3") }
    }
}
