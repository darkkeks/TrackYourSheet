package ru.darkkeks.trackyoursheet.prototype.states.menu

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import ru.darkkeks.trackyoursheet.prototype.telegram.*

// TODO Pagination
class RangeListState : MessageState() {
    class GoBackButton(state: MessageState) : StatefulButton(state)
    class RangeButton(val range: Id<TrackJob>, state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext): MessageRender {
        val user = context.controller.sheetDao.getOrCreateUser(context.userId)
        val ranges = context.controller.sheetDao.getUserJobs(user._id)

        return if (ranges.isEmpty()) {
            MessageRender("У вас нету ренжей \uD83D\uDE1E", buildInlineKeyboard {
                row(goBackButton())
            })
        } else {
            MessageRender("Ваши ренжи:", buildInlineKeyboard {
                for (range in ranges) {
                    row(button("${range.sheet.sheetName}!${range.range}",
                               RangeButton(range._id, this@RangeListState)))
                }
                row(goBackButton())
            })
        }
    }

    private fun goBackButton() = button("◀ Назад", GoBackButton(this))

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is GoBackButton -> changeState(MainMenuState(), context)
            is RangeButton -> changeState(RangeMenuState(context.button.range), context)
        }
    }
}