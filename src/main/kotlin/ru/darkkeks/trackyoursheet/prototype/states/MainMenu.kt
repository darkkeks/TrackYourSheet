package ru.darkkeks.trackyoursheet.prototype.states

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class MainMenuState : MessageState() {
    class CreateNewRangeButton(state: MessageState) : StatefulButton(state)
    class ListRangesButton(state: MessageState) : StatefulButton(state)
    class SettingsButton(state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext) =
        MessageRender("""
        Привет, я умею наблюдать за гугл-табличками. ${"\uD83E\uDD13"}
        
        Давай будем называть _ренжем_ (_ренж_, _ренжик_, в _ренже_, _ренжику_, от англ. _range_) диапазон не некотором листе в гугл таблице. 
        Я не смог придумать название лучше, если есть идеи -- всегда можно написать @darkkeks.
        
        Сюда еще хелпы надо, напишите @lodthe чтобы добавил))0)).
    """.trimIndent(), buildInlineKeyboard {
            row(button("*️⃣ Нам нужно больше ренжиков", CreateNewRangeButton(this@MainMenuState)))
            row(button("\uD83D\uDCDD Ренжики мои любимые", ListRangesButton(this@MainMenuState)))
            row(button("⚙️Настроечкикикики", SettingsButton(this@MainMenuState)))
        })

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is CreateNewRangeButton -> {
                val state = NewRangeState()
                context.controller.changeState(context.stateHolder, state)
                state.initiate(context)
            }
            is ListRangesButton -> {
                changeState(RangeListState(), context)
            }
        }
    }
}

// TODO Pagination
class RangeListState : MessageState() {
    class GoBackButton(state: MessageState) : StatefulButton(state)
    class RangeButton(val range: Id<TrackJob>, state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext): MessageRender {
        val user = context.controller.sheetDao.getOrCreateUser(context.userId)
        val ranges = context.controller.sheetDao.getUserJobs(user._id)

        return if (ranges.isEmpty()) {
            MessageRender("У вас нету ренжей \uD83D\uDE1E",
                                                                                           buildInlineKeyboard {
                                                                                               row(goBackButton())
                                                                                           })
        } else {
            MessageRender("Ваши ренжи:",
                                                                                           buildInlineKeyboard {
                                                                                               for (range in ranges) {
                                                                                                   row(button("${range.sheet.sheetName}!${range.range}",
                                                                                                              RangeButton(
                                                                                                                  range._id,
                                                                                                                  this@RangeListState)))
                                                                                               }
                                                                                               row(goBackButton())
                                                                                           })
        }
    }

    private fun goBackButton() = button("◀ Назад", GoBackButton(this))

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is GoBackButton -> changeState(MainMenuState(), context)
            is RangeButton -> changeState(RangeMenuState(
                context.button.range), context)
        }
    }
}

class RangeMenuState(private val rangeId: Id<TrackJob>) : MessageState() {
    class GoBackButton(state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext): MessageRender {
        // TODO Нормальная ошибка при исчезновении ренжика
        val range = context.controller.sheetDao.getJob(rangeId)
            ?: return MessageRender("Ренжик не найден \uD83D\uDE22",
                                                                                                     buildInlineKeyboard {
                                                                                                         row(goBackButton())
                                                                                                     })
        val spreadsheet = context.controller.sheetApi.getSheet(range.sheet)
        return MessageRender("""
            Инфа о ренжике:
            
            Табличка: [${spreadsheet.properties.title}](${spreadsheet.spreadsheetUrl})
            Лист: [${range.sheet.sheetName}](${range.sheet.sheetUrl})
            Ренж: [${range.range}](${range.sheet.urlTo(range.range)})
            Интервал: ${range.interval}
        """.trimIndent(), buildInlineKeyboard {
            row(goBackButton())
        })
    }

    private fun goBackButton() = button("◀ Назад", GoBackButton(this))

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is GoBackButton -> changeState(RangeListState(), context)
        }
    }
}