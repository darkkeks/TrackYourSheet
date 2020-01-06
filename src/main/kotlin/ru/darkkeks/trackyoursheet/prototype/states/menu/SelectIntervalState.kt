package ru.darkkeks.trackyoursheet.prototype.states.menu

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.prototype.TimeUnits
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import ru.darkkeks.trackyoursheet.prototype.telegram.*
import java.time.Duration

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
                            context.controller.stopJob(newRange)
                            context.controller.startJob(newRange)
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