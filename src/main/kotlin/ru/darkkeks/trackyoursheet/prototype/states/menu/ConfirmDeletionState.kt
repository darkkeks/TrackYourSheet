package ru.darkkeks.trackyoursheet.prototype.states.menu

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class ConfirmDeletionState(val rangeId: Id<TrackJob>) : MessageState() {
    override suspend fun draw(context: UserActionContext) = MessageRender("""
        Вы уверены что хотите удалить ренж?))))
    """.trimIndent(), buildInlineKeyboard {
        add(button("✅ Да", YesButton(this@ConfirmDeletionState)))
        add(button("❌ Нет", NoButton(this@ConfirmDeletionState)))
        newRow()
        add(button("◀️Назад", NoButton(this@ConfirmDeletionState)))
    })

    class YesButton(state : MessageState) : StatefulButton(state)
    class NoButton(state : MessageState) : StatefulButton(state)

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is YesButton -> {
                val range = context.controller.sheetDao.getJob(rangeId)
                if (range == null) {
                    changeState(NotFoundErrorState(), context)
                } else {
                    context.controller.stopJob(range)
                    context.controller.sheetDao.deleteJob(rangeId)
                    changeState(RangeListState(), context)
                }
            }
            is NoButton -> changeState(RangeMenuState(rangeId), context)
        }
    }
}