package ru.darkkeks.trackyoursheet.prototype.states.menu

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class RangeMenuState(val rangeId: Id<TrackJob>) : MessageState() {

    override suspend fun draw(context: UserActionContext): MessageRender {
        // TODO Нормальная ошибка при исчезновении ренжика
        val range = context.controller.sheetDao.getJob(rangeId)
            ?: return MessageRender("Ренжик не найден \uD83D\uDE22", buildInlineKeyboard {
                row(goBackButton())
            })
        val spreadsheet = context.controller.sheetApi.getSheet(range.sheet)
        return MessageRender("""
            Инфа о ренжике:
            
            Табличка: [${spreadsheet.properties.title}](${spreadsheet.spreadsheetUrl})
            Лист: [${range.sheet.sheetName}](${range.sheet.sheetUrl})
            Ренж: [${range.range}](${range.sheet.urlTo(range.range)})
        """.trimIndent(), buildInlineKeyboard {
            row(goBackButton())
            row(intervalButton(range))
            row(toggleEnabledButton(range))
            row(deleteButton(range))
        })
    }

    class DeleteButton(state: MessageState) : StatefulButton(state)

    private fun deleteButton(range: TrackJob): InlineKeyboardButton {
        return button("\uD83D\uDDD1 Удалить", DeleteButton(this))
    }

    class SelectIntervalButton(state: MessageState) : StatefulButton(state)

    private fun intervalButton(range: TrackJob): InlineKeyboardButton {
        return button("⏱ Интервал: ${range.interval}", SelectIntervalButton(this))
    }

    class ToggleEnabledButton(val target: Boolean, state: MessageState) : StatefulButton(state)

    private fun toggleEnabledButton(range: TrackJob): InlineKeyboardButton {
        val text = if (range.enabled) "✅ Включено" else "❌ Выключено"
        return button(text, ToggleEnabledButton(range.enabled.not(), this))
    }

    class GoBackButton(state: MessageState) : StatefulButton(state)

    private fun goBackButton(): InlineKeyboardButton {
        return button("◀ Назад", GoBackButton(this))
    }

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is GoBackButton -> changeState(RangeListState(), context)
            is ToggleEnabledButton -> {
                val range = context.controller.sheetDao.getJob(rangeId)
                if (range == null) {
                    changeState(NotFoundErrorState(), context)
                } else {
                    if (range.enabled != context.button.target) {
                        val newJob = range.copy(enabled = context.button.target)
                        if (newJob.enabled) {
                            context.controller.startJob(newJob)
                        } else {
                            context.controller.stopJob(newJob)
                        }
                        context.controller.sheetDao.saveJob(newJob)
                    }
                    changeState(RangeMenuState(rangeId), context)
                }
            }
            is SelectIntervalButton -> changeState(SelectIntervalState(rangeId), context)
            is DeleteButton -> changeState(ConfirmDeletionState(rangeId), context)
        }
    }
}