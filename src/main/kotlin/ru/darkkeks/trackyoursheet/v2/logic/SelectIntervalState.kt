package ru.darkkeks.trackyoursheet.v2.logic

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.v2.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.v2.Range
import ru.darkkeks.trackyoursheet.v2.TimeUnits
import ru.darkkeks.trackyoursheet.v2.telegram.*
import java.time.Duration

class SelectIntervalState(private var rangeId: Id<Range>) : MessageState() {
    class IntervalButton(val duration: Duration) : TextButton("\uD83D\uDD53 " + TimeUnits.durationToString(duration)) {
        override fun serialize(buffer: ButtonBuffer) = buffer.pushInt(duration.toSeconds().toInt())
    }

    override fun serialize(buffer: ButtonBuffer) = buffer.pushId(rangeId)

    override suspend fun draw(context: BaseContext) = TextRender("""
        Выберите как часто проверять ренж на обновления
    """.trimIndent(), buildInlineKeyboard {
        AVAILABLE_OPTIONS.chunked(3).forEach { row ->
            row.forEach { add(IntervalButton(it)) }
            newRow()
        }
    })

    override val handlerList = buildHandlerList {
        callback<IntervalButton> {
            if (button.duration !in AVAILABLE_OPTIONS) {
                answerCallbackQuery("Данный интервал больше недоступен")
                changeState(SelectIntervalState(rangeId))
            } else {
                val range = controller.repository.getRange(rangeId)
                if (range == null) {
                    changeState(NotFoundErrorState())
                } else {
                    val seconds = button.duration.toSeconds()
                    if (range.interval !is PeriodTrackInterval || range.interval.period != seconds) {
                        val newRange = range.copy(interval = PeriodTrackInterval(
                            seconds))
                        controller.repository.saveRange(newRange)
                        controller.stopRange(newRange)
                        controller.startRange(newRange)
                    }
                    changeState(RangeMenuState(rangeId))
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