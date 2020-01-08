package ru.darkkeks.trackyoursheet.prototype.states.menu

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.Range
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class RangeMenuState(val rangeId: Id<Range>) : MessageState() {
    override suspend fun draw(context: UserActionContext): MessageRender {
        // TODO Нормальная ошибка при исчезновении ренжика
        val range = context.controller.sheetDao.getJob(rangeId)
            ?: return TextRender("Ренжик не найден \uD83D\uDE22", buildInlineKeyboard {
                row(goBackButton())
            })
        val spreadsheet = context.controller.sheetApi.getSheet(range.sheet)
        return TextRender("""
            Инфа о ренжике:
            
            Табличка: [${spreadsheet.properties.title}](${spreadsheet.spreadsheetUrl})
            Лист: [${range.sheet.sheetName}](${range.sheet.sheetUrl})
            Ренж: [${range.range}](${range.sheet.urlTo(range.range)})
        """.trimIndent(), buildInlineKeyboard {
            add(toggleEnabledButton(range))
            add(intervalButton(range))
            newRow()
            add(postTargetButton(range))
            add(deleteButton())
            newRow()
            row(goBackButton())
        })
    }

    class PostTargetButton(state: MessageState) : StatefulButton(state)

    private fun postTargetButton(range: Range): InlineKeyboardButton {
        return button("\uD83D\uDCDD Куда постим: ${range.postTarget.name}", PostTargetButton(this))
    }

    class DeleteButton(state: MessageState) : StatefulButton(state)

    private fun deleteButton(): InlineKeyboardButton {
        return button("\uD83D\uDDD1 Удалить", DeleteButton(this))
    }

    class SelectIntervalButton(state: MessageState) : StatefulButton(state)

    private fun intervalButton(range: Range): InlineKeyboardButton {
        return button("⏱ Интервал: ${range.interval}", SelectIntervalButton(this))
    }

    class ToggleEnabledButton(val target: Boolean, state: MessageState) : StatefulButton(state)

    private fun toggleEnabledButton(range: Range): InlineKeyboardButton {
        val text = if (range.enabled) "✅ Включено" else "❌ Выключено"
        return button(text, ToggleEnabledButton(range.enabled.not(), this))
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
            is PostTargetButton -> changeState(SelectPostTargetState(rangeId), context)
            is SelectIntervalButton -> changeState(SelectIntervalState(rangeId), context)
            is DeleteButton -> changeState(ConfirmDeletionState(rangeId), context)
        }
    }
}