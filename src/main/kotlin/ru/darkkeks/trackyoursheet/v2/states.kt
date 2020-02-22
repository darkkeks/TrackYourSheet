package ru.darkkeks.trackyoursheet.v2

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.v2.telegram.*

class DefaultState : GlobalState() {
    override val handlerList = buildHandlerList {
        command("list_ranges") {
            RangeListState().send(this)
        }
        command("new_range") {
            changeGlobalState(NewRangeState())
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
        callback<CreateNewRangeButton> {  }
        callback<ListRangesButton> { changeState(RangeListState()) }
    }
}

class RangeListState : MessageState() {
    class RangeButton(val range: Id<Range>, label: String) : TextButton(label)

    override suspend fun draw(context: BaseContext): MessageRender {
        val user = context.controller.dao.getOrCreateUser(context.userId)
        val ranges = context.controller.dao.getUserJobs(user._id)

        return if (ranges.isEmpty()) {
            TextRender("У вас нету ренжей \uD83D\uDE1E", buildInlineKeyboard {
                row(GoBackButton())
            })
        } else {
            TextRender("Ваши ренжи:", buildInlineKeyboard {
                for (range in ranges) {
                    row(RangeButton(range._id, "${range.sheet.sheetName}!${range.range}"))
                }
                row(GoBackButton())
            })
        }
    }

    override val handlerList = buildHandlerList {

    }
}

class NewRangeState: GlobalState() {
    override val handlerList = buildHandlerList {

    }
}
