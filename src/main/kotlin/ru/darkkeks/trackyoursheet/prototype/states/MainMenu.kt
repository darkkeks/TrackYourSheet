package ru.darkkeks.trackyoursheet.prototype.states

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.prototype.TimeUnits
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import ru.darkkeks.trackyoursheet.prototype.telegram.*
import java.time.Duration

class MainMenuState : MessageState() {
    class CreateNewRangeButton(state: MessageState) : StatefulButton(state)
    class ListRangesButton(state: MessageState) : StatefulButton(state)
    class SettingsButton(state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext) = MessageRender("""
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
            MessageRender("У вас нету ренжей \uD83D\uDE1E", buildInlineKeyboard {
                row(goBackButton())
            })
        } else {
            MessageRender("Ваши ренжи:", buildInlineKeyboard {
                for (range in ranges) {
                    row(button("${range.sheet.sheetName}!${range.range}", RangeButton(range._id, this@RangeListState)))
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

class RangeMenuState(val rangeId: Id<TrackJob>) : MessageState() {
    class GoBackButton(state: MessageState) : StatefulButton(state)
    class ToggleEnabledButton(val target: Boolean, state: MessageState) : StatefulButton(state)
    class SelectIntervalButton(state: MessageState) : StatefulButton(state)

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
            Интервал: ${range.interval}
        """.trimIndent(), buildInlineKeyboard {
            row(goBackButton())
            row(intervalButton(range))
            row(toggleEnabledButton(range))
        })
    }

    private fun intervalButton(range: TrackJob): InlineKeyboardButton {
        return button("⏱ Интервал: ${range.interval}", SelectIntervalButton(this))
    }

    private fun toggleEnabledButton(range: TrackJob): InlineKeyboardButton {
        val text = if (range.enabled) "✅ Включено" else "❌ Выключено"
        return button(text, ToggleEnabledButton(range.enabled.not(), this))
    }

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
                            context.controller.addJob(newJob)
                        } else {
                            context.controller.removeJob(newJob)
                        }
                        context.controller.sheetDao.saveJob(newJob)
                    }
                    changeState(RangeMenuState(rangeId), context)
                }
            }
            is SelectIntervalButton -> {
                changeState(SelectIntervalState(rangeId), context)
            }
        }
    }
}

class SelectIntervalState(val rangeId: Id<TrackJob>) : MessageState() {
    class IntervalButton(val seconds: Long, state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext) = MessageRender("""
        Выберите как часто проверять ренж на обновления
    """.trimIndent(), buildInlineKeyboard {
        AVAILABLE_OPTIONS.chunked(3).forEach { row ->
            row.forEach { duration ->
                val button = IntervalButton(duration.toSeconds(), this@SelectIntervalState)
                add(button("\uD83D\uDD53 " + TimeUnits.durationToString(duration), button))
            }
            newRow()
        }
    })

    override suspend fun handleButton(context: CallbackButtonContext) {
        when (context.button) {
            is IntervalButton -> {
                val seconds = context.button.seconds
                if (Duration.ofSeconds(seconds) !in AVAILABLE_OPTIONS) {
                    context.answerCallbackQuery("Данный интервал больше недоступен")
                    changeState(SelectIntervalState(rangeId), context)
                } else {
                    val range = context.controller.sheetDao.getJob(rangeId)
                    if (range == null) {
                        changeState(NotFoundErrorState(), context)
                    } else {
                        if (range.interval !is PeriodTrackInterval || range.interval.period != seconds) {
                            val newRange = range.copy(interval = PeriodTrackInterval(seconds))
                            context.controller.sheetDao.saveJob(newRange)
                            context.controller.removeJob(newRange)
                            context.controller.addJob(newRange)
                        }
                        changeState(RangeMenuState(rangeId), context)
                    }
                }
            }
        }
    }

    companion object {
        private val AVAILABLE_OPTIONS = listOf(
            Duration.ofSeconds(5),
            Duration.ofMinutes(1),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(3),
            Duration.ofHours(12),
            Duration.ofDays(1)
        )
    }
}

class NotFoundErrorState() : MessageState() {
    inner class GoBackButton(state: MessageState) : StatefulButton(state)

    override suspend fun draw(context: UserActionContext) = MessageRender(
        "Что-то пошло не так :(", buildInlineKeyboard {
            row(button("◀ Назад", GoBackButton(this@NotFoundErrorState)))
        })

    override suspend fun handleButton(context: CallbackButtonContext) {
        changeState(MainMenuState(), context)
    }
}