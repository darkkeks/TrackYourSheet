package ru.darkkeks.trackyoursheet.prototype.states.menu

import ru.darkkeks.trackyoursheet.prototype.telegram.*

class NotFoundErrorState() : MessageState() {
    inner class GoBackButton(state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext) =
        MessageRender("Что-то пошло не так :(", buildInlineKeyboard {
            row(button("◀ Назад", GoBackButton(this@NotFoundErrorState)))
        })

    override suspend fun handleButton(context: CallbackButtonContext) {
        changeState(MainMenuState(), context)
    }
}