package ru.darkkeks.trackyoursheet.v2.logic

import ru.darkkeks.trackyoursheet.v2.telegram.*

class MainMenuState : MessageState() {
    class CreateNewRangeButton : TextButton("*️⃣ Создать ренж")
    class ListRangesButton : TextButton("\uD83D\uDCDD Список ренжей")

    override suspend fun draw(context: BaseContext) = TextRender("""
        ${"\uD83D\uDD25"} Привет, я умею наблюдать за гугл табличками. ${"\uD83D\uDD25"}
        
        ${"\uD83D\uDCCC"} _Ренжем_ я называю диапазон на некотором листе в гугл таблице.
        
        ${"✨"} С любыми вопросами по работе бота можно писать @darkkeks.
    """.trimIndent(), buildInlineKeyboard {
        row(CreateNewRangeButton())

        val ranges = context.controller.repository.getUserRanges(context.user.model._id)
        if (ranges.isNotEmpty()) {
            row(ListRangesButton())
        }
    })

    override val handlerList = buildHandlerList {
        callback<CreateNewRangeButton> {
            changeGlobalState(NewRangeState())
        }
        callback<ListRangesButton> {
            changeState(RangeListState())
        }
    }
}