package ru.darkkeks.trackyoursheet.logic

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.Range
import ru.darkkeks.trackyoursheet.telegram.*

class ConfirmDeletionState(private var rangeId: Id<Range>) : MessageState() {
    class ConfirmButton(val confirm: Boolean) : TextButton(if (confirm) "\u2705 Да" else "\u274C Нет") {
        override fun serialize(buffer: ButtonBuffer) = buffer.pushBoolean(confirm)
    }

    override fun serialize(buffer: ButtonBuffer) = buffer.pushId(rangeId)

    override suspend fun draw(context: BaseContext) =
        TextRender("""
            Вы уверены, что хотите удалить ренж?
        """.trimIndent(), buildInlineKeyboard {
            add(ConfirmButton(true))
            add(ConfirmButton(false))
            newRow()
            add(GoBackButton())
        })

    override val handlerList = buildHandlerList {
        callback<ConfirmButton> {
            if (button.confirm) {
                val range = controller.repository.getRange(rangeId)
                if (range == null) {
                    changeState(NotFoundErrorState())
                } else {
                    controller.stopRange(range)
                    controller.repository.deleteRange(rangeId)
                    changeState(RangeListState())
                }
            } else {
                changeState(RangeMenuState(rangeId))
            }
        }
        callback<GoBackButton> {
            changeState(RangeMenuState(rangeId))
        }
    }
}