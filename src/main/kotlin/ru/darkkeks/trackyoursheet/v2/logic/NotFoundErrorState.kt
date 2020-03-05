package ru.darkkeks.trackyoursheet.v2.logic

import ru.darkkeks.trackyoursheet.v2.telegram.*

class NotFoundErrorState : MessageState() {
    override suspend fun draw(context: BaseContext) =
        TextRender("Что-то пошло не так :(", buildInlineKeyboard {
            row(GoBackButton())
        })

    override val handlerList = buildHandlerList {
        anyCallback { changeState(MainMenuState()) }
    }
}