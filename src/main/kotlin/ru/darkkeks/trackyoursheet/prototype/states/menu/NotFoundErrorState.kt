package ru.darkkeks.trackyoursheet.prototype.states.menu

import ru.darkkeks.trackyoursheet.prototype.telegram.*

class NotFoundErrorState() : MessageState() {
    override suspend fun draw(context: UserActionContext) =
        TextRender("Что-то пошло не так :(", buildInlineKeyboard {
            row(goBackButton())
        })

    override suspend fun handleButton(context: CallbackButtonContext) {
        changeState(MainMenuState(), context)
    }
}