package ru.darkkeks.trackyoursheet.v2.logic

import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.v2.Range
import ru.darkkeks.trackyoursheet.v2.TrackInterval
import ru.darkkeks.trackyoursheet.v2.formatTime
import ru.darkkeks.trackyoursheet.v2.telegram.*

class RangeMenuState(private val rangeId: Id<Range>) : MessageState() {
    class PostTargetButton(name: String) : TextButton("\uD83D\uDCDD Куда постим: $name")

    class DeleteButton : TextButton("\uD83D\uDDD1 Удалить")

    class IntervalButton(interval: TrackInterval) : TextButton("⏱ Интервал: $interval")

    class ToggleEnabledButton(val target: Boolean) : TextButton(if (target.not()) "✅ Включено" else "❌ Выключено") {
        override fun serialize(buffer: ButtonBuffer) = buffer.pushBoolean(target)
    }

    override fun serialize(buffer: ButtonBuffer) = buffer.pushId(rangeId)

    override suspend fun draw(context: BaseContext): MessageRender {
        val range = context.controller.repository.getRange(rangeId)
            ?: return TextRender("Ренжик не найден \uD83D\uDE22", buildInlineKeyboard {
                row(GoBackButton())
            })

        val rangeData = context.controller.repository.getLastData(range._id)
        val rangeUpdateTime = if (rangeData != null) formatTime(rangeData.time) else "Никогда"

        val spreadsheet = context.controller.sheetApi.getSheet(range.sheet)
        return TextRender("""
            Информация о ренже:

            Табличка: [${spreadsheet.properties.title}](${spreadsheet.spreadsheetUrl})
            Лист: [${range.sheet.sheetName}](${range.sheet.sheetUrl})
            Ренж: [${range.range}](${range.sheet.urlTo(range.range)})
            Последнее обновление: `${rangeUpdateTime}`
        """.trimIndent(), buildInlineKeyboard {
            add(ToggleEnabledButton(range.enabled.not()))
            add(IntervalButton(range.interval))
            newRow()
            add(PostTargetButton(range.postTarget.name))
            add(DeleteButton())
            newRow()
            row(GoBackButton())
        })
    }

    override val handlerList = buildHandlerList {
        callback<PostTargetButton> {
            changeState(SelectPostTargetState(rangeId))
        }
        callback<IntervalButton> {
            changeState(SelectIntervalState(rangeId))
        }
        callback<DeleteButton> {
            changeState(ConfirmDeletionState(rangeId))
        }
        callback<GoBackButton> { changeState(RangeListState()) }
        callback<ToggleEnabledButton> {
            val range = controller.repository.getRange(rangeId)

            if (range == null) {
                changeState(NotFoundErrorState())
            } else {
                if (range.enabled != button.target) {
                    val newRange = range.copy(enabled = button.target)
                    if (newRange.enabled) {
                        controller.startRange(newRange)
                    } else {
                        controller.stopRange(newRange)
                    }
                    controller.repository.saveRange(newRange)
                }
                changeState(RangeMenuState(rangeId))
            }
        }
    }
}