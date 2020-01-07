package ru.darkkeks.trackyoursheet.prototype.states.menu

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.Range
import ru.darkkeks.trackyoursheet.prototype.telegram.*

// TODO Pagination
class RangeListState : MessageState() {
    class RangeButton(val range: Id<Range>, state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext): MessageRender {
        val user = context.controller.sheetDao.getOrCreateUser(context.userId)
        val ranges = context.controller.sheetDao.getUserJobs(user._id)

        return if (ranges.isEmpty()) {
            TextRender("У вас нету ренжей \uD83D\uDE1E", buildInlineKeyboard {
                row(goBackButton())
            })
        } else {
            TextRender("Ваши ренжи:", buildInlineKeyboard {
                for (range in ranges) {
                    row(button("${range.sheet.sheetName}!${range.range}",
                               RangeButton(range._id, this@RangeListState)))
                }
                row(goBackButton())
            })
        }
    }

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is GoBackButton -> changeState(MainMenuState(), context)
            is RangeButton -> changeState(RangeMenuState(context.button.range), context)
        }
    }
}