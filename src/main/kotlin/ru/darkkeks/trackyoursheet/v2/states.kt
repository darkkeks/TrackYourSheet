package ru.darkkeks.trackyoursheet.v2

import com.pengrad.telegrambot.model.Chat
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.v2.telegram.*
import java.time.Duration

class DefaultState : GlobalState() {
    override val handlerList = buildHandlerList {
        command("new_range") { changeGlobalState(NewRangeState()) }
        command("list_ranges") { RangeListState().send(this) }
        fallback { MainMenuState().send(this) }
    }
}

class MainMenuState : MessageState() {
    class CreateNewRangeButton : TextButton("*️⃣ Нам нужно больше ренжиков")
    class ListRangesButton : TextButton("\uD83D\uDCDD Ренжики мои любимые")
    class SettingsButton : TextButton("⚙️Настроечкикикики")

    override suspend fun draw(context: BaseContext) = TextRender("""
        Привет, я умею наблюдать за гугл-табличками. ${"\uD83E\uDD13"}
        
        Давай будем называть _ренжем_ (_ренж_, _ренжик_, в _ренже_, _ренжику_, от англ. _range_) диапазон не некотором листе в гугл таблице. 
        Я не смог придумать название лучше, если есть идеи -- всегда можно написать @darkkeks.
        
        Сюда еще хелпы надо, напишите @lodthe чтобы добавил))0)).
    """.trimIndent(), buildInlineKeyboard {
        row(CreateNewRangeButton())
        row(ListRangesButton())
        row(SettingsButton())
    })


    override val handlerList = buildHandlerList {
        callback<CreateNewRangeButton> { changeGlobalState(NewRangeState()) }
        callback<ListRangesButton> { changeState(RangeListState()) }
    }
}

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
        callback<RangeButton> { changeState(RangeMenuState(button.rangeId)) }
        callback<GoBackButton> { changeState(MainMenuState()) }
    }
}

class RangeMenuState(private val rangeId: Id<Range>) : MessageState() {
    class PostTargetButton(name: String) : TextButton("\uD83D\uDCDD Куда постим: $name")

    class DeleteButton : TextButton("\uD83D\uDDD1 Удалить")

    class IntervalButton(interval: TrackInterval) : TextButton("⏱ Интервал: $interval")

    class ToggleEnabledButton(val target: Boolean) : TextButton(if (target.not()) "✅ Включено" else "❌ Выключено") {
        override fun serialize(buffer: ButtonBuffer) = buffer.pushBoolean(target)
    }

    override suspend fun draw(context: BaseContext): MessageRender {
        val range = context.controller.repository.getRange(rangeId)
            ?: return TextRender("Ренжик не найден \uD83D\uDE22", buildInlineKeyboard {
                row(GoBackButton())
            })

        val spreadsheet = context.controller.sheetApi.getSheet(range.sheet)
        return TextRender("""
            Инфа о ренжике:
            
            Табличка: [${spreadsheet.properties.title}](${spreadsheet.spreadsheetUrl})
            Лист: [${range.sheet.sheetName}](${range.sheet.sheetUrl})
            Ренж: [${range.range}](${range.sheet.urlTo(range.range)})
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
        callback<PostTargetButton> { changeState(SelectPostTargetState(rangeId)) }
        callback<IntervalButton> { changeState(SelectIntervalState(rangeId)) }
        callback<DeleteButton> { changeState(ConfirmDeletionState(rangeId)) }
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

class SelectPostTargetState(private var rangeId: Id<Range>) : MessageState() {
    class PostTargetButton(val target: Chat.Type)
        : TextButton(if (target == Chat.Type.Private) "Постить сюда" else "Постить в группу/канал") {

        override fun serialize(buffer: ButtonBuffer) = buffer.pushByte(target.ordinal.toByte())
    }

    override suspend fun draw(context: BaseContext): MessageRender {
        val range = context.controller.repository.getRange(rangeId)
            ?: return ChangeStateRender(NotFoundErrorState())

        val target = range.postTarget
        val name = when (target.type) {
            Chat.Type.Private -> "сюда"
            Chat.Type.channel -> ""
            Chat.Type.group -> ""
            Chat.Type.supergroup -> ""
        }
        return TextRender("""
            Сейчас сообщения отправляются $name.
        """.trimIndent(), buildInlineKeyboard {
            row(GoBackButton())
            if (target.type != Chat.Type.Private) {
                row(PostTargetButton(Chat.Type.Private))
            }
            row(PostTargetButton(Chat.Type.group))
        })
    }

    override val handlerList = buildHandlerList {
        callback<PostTargetButton> {
            val range = controller.repository.getRange(rangeId)
            when {
                range == null -> changeState(NotFoundErrorState())
                button.target == Chat.Type.Private -> {
                    val newTarget = PostTarget.private(userId)
                    val newRange = range.copy(postTarget = newTarget)
                    controller.repository.saveRange(newRange)
                    changeState(RangeMenuState(rangeId))
                }
                else -> changeGlobalState(ReceiveGroupState(rangeId))
            }
        }
        callback<GoBackButton> {
            changeState(RangeMenuState(rangeId))
        }
    }
}

class SelectIntervalState(private var rangeId: Id<Range>) : MessageState() {
    class IntervalButton(val duration: Duration)
        : TextButton("\uD83D\uDD53 " + TimeUnits.durationToString(duration)) {

        override fun serialize(buffer: ButtonBuffer) = buffer.pushInt(duration.toSeconds().toInt())
    }

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
                        val newRange = range.copy(interval = PeriodTrackInterval(seconds))
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

class ConfirmDeletionState(private var rangeId: Id<Range>) : MessageState() {
    class ConfirmButton(val confirm: Boolean) : TextButton(if (confirm) "\u2705 Да" else "\u274C Нет") {
        override fun serialize(buffer: ButtonBuffer) = buffer.pushBoolean(confirm)
    }

    override suspend fun draw(context: BaseContext) = TextRender("""
        Вы уверены, что хотите удалить ренжик?))))))
    """.trimIndent(), buildInlineKeyboard {
        add(ConfirmButton(true))
        add(ConfirmButton(false))
        newRow()
        add(GoBackButton())
    })

    override val handlerList = buildHandlerList {
        callback<ConfirmButton> {
            if (button.confirm) {
                val range = controller.repository.getRange(rangeId)
                if (range == null) {
                    changeState(NotFoundErrorState())
                } else {
                    controller.stopRange(range)
                    controller.repository.deleteRange(rangeId)
                    changeState(RangeListState())
                }
            } else {
                changeState(RangeMenuState(rangeId))
            }
        }
        callback<GoBackButton> {
            changeState(RangeMenuState(rangeId))
        }
    }
}

class NotFoundErrorState : MessageState() {
    override suspend fun draw(context: BaseContext) =
        TextRender("Что-то пошло не так :(", buildInlineKeyboard {
            row(GoBackButton())
        })

    override val handlerList = buildHandlerList {
        anyCallback { changeState(MainMenuState()) }
    }
}

class NewRangeState : GlobalState() {
    override val handlerList = buildHandlerList {

    }
}

class ReceiveGroupState(val rangeId: Id<Range>) : GlobalState() {
    override val handlerList = buildHandlerList {

    }
}