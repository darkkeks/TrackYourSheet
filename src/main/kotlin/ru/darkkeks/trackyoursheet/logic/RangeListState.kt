package ru.darkkeks.trackyoursheet.logic

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.Range
import ru.darkkeks.trackyoursheet.telegram.*

class RangeListState : MessageState() {
    class RangeButton(val rangeId: Id<Range>, label: String) : TextButton(label) {
        override fun serialize(buffer: ButtonBuffer) = buffer.pushId(rangeId)
    }

    override suspend fun draw(context: BaseContext): MessageRender {
        val user = context.controller.repository.getOrCreateUser(context.userId)
        val ranges = context.controller.repository.getUserRanges(user._id)

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
        callback<RangeButton> {
            changeState(RangeMenuState(button.rangeId))
        }
        callback<GoBackButton> { changeState(MainMenuState()) }
    }
}