package ru.darkkeks.trackyoursheet.v2

import com.google.api.services.sheets.v4.model.Sheet
import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.ChatMember
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatAdministrators
import com.pengrad.telegrambot.request.GetChatMember
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.v2.sheet.CellRange
import ru.darkkeks.trackyoursheet.v2.sheet.SheetData
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

    class SheetSelectButton(val sheetId: Int, title: String) : TextButton(title) {
        override fun serialize(buffer: ButtonBuffer) = buffer.pushInt(sheetId)
    }

    class CancelButton : TextButton("\u274C Отмена")

    var spreadsheetId: String? = null
    var sheetId: Int? = null
    var sheetTitle: String? = null
    var range: CellRange? = null

    override val handlerList = buildHandlerList {
        onEnter {
            forceReply("""
                Окей, ща создадим, сейчас мне от тебя нужна ссылка на табличку.

                Можешь сразу дать ссылку на нужный ренж (выделить в гугл табличке -> пкм -> получить ссылку на этот диапазон).

                Либо просто ссылку на нужную таблчику, а я помогу выбрать лист и ренж.

                Выглядит она вот так:
                ```https://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-asdfghjklasdfdsaf/edit#gid=13371488228&range=A1:Z22```
            """.trimIndent(), replyMarkup = buildInlineKeyboard {
                row(CancelButton())
            }.getMarkup(NullState(), controller.registry))
        }

        command("cancel") {
            changeGlobalState(DefaultState())
        }

        callback<SheetSelectButton> {
            val finalSpreadsheetId = spreadsheetId
            if (finalSpreadsheetId != null) {
                val sheets = controller.sheetApi.getSheets(finalSpreadsheetId)

                val sheet = sheets.find {
                    it.properties.sheetId == button.sheetId
                }

                if (sheet != null) {
                    sheetId = sheet.properties.sheetId
                    sheetTitle = sheet.properties.title
                    forceReply("""
                            Вижу табличку и лист, осталось указать какой ренж трекать. Напиши вот в таком формате: `A2:BE26`.
                        """.trimIndent())
                } else {
                    answerCallbackQuery("Такого листа нет, попробуй еще раз.")
                    controller.bot.execute(EditMessageReplyMarkup(message.chat().id(), message.messageId())
                                            .replyMarkup(createSheetSelectKeyboard(sheets, this)))
                }
            } else {
                answerCallbackQuery("Сначала дай ссылку на таблицу")
            }
        }

        callback<CancelButton> {
            changeGlobalState(DefaultState())
        }

        text {
            val text: String? = message.text()

            if (text == null) {
                notALink(this)
                return@text
            }

            val match = SheetData.fromUrl(text)
            if (match != null) {
                val (id, arguments) = match
                spreadsheetId = id

                val sheets = controller.sheetApi.getSheets(id)

                if ("gid" in arguments) {
                    val sheet = sheets.find {
                        it.properties.sheetId.toString() == arguments["gid"]
                    }

                    if (sheet != null && "range" in arguments) {
                        sheetId = sheet.properties.sheetId
                        sheetTitle = sheet.properties.title
                        val rangeString = arguments.getValue("range")
                        if (CellRange.isRange(rangeString)) {
                            range = CellRange.fromString(rangeString)
                            finish(this)
                            return@text
                        }
                    }
                }

                if (sheetId != null) {
                    forceReply("""
                        Вижу табличку и лист, осталось указать какой ренж трекать. Напиши вот в таком формате: `A2:BE26`.
                    """.trimIndent())
                } else {
                    send("""
                        Вижу [табличку](${SheetData(id, 0).sheetUrl}), теперь надо выбрать один из листов.
                    """.trimIndent(), replyMarkup = createSheetSelectKeyboard(sheets, this))
                }
            } else if (spreadsheetId == null || sheetId == null) {
                notALink(this)
            } else if (CellRange.isRange(text)) {
                range = CellRange.fromString(text)
                finish(this)
            } else {
                forceReply("""
                    Это не ренж, попробуй еще раз.
                """.trimIndent())
            }
        }
    }

    private suspend fun finish(context: BaseContext) {
        val dao = context.controller.repository
        val user = dao.getOrCreateUser(context.userId)

        val sheetData = SheetData(spreadsheetId!!, sheetId!!, sheetTitle!!)
        val rangeJob = Range(
            sheetData,
            range!!,
            PeriodTrackInterval(Duration.ofSeconds(30)),
            user._id,
            true,
            PostTarget.private(user.userId)
        )
        dao.saveRange(rangeJob)

        context.controller.startRange(rangeJob)

        context.send("""
            Готово, буду трекать ренж [${this.range!!}](${sheetData.urlTo(this.range!!)}) на листе `$sheetTitle`.
        """.trimIndent())

        RangeMenuState(rangeJob._id).send(context)
        context.changeGlobalState(DefaultState())
    }

    private fun createSheetSelectKeyboard(sheets: List<Sheet>, context: BaseContext) = buildInlineKeyboard {
        sheets.forEach {
            row(SheetSelectButton(it.properties.sheetId, it.properties.title))
        }
    }.getMarkup(NullState(), context.controller.registry)

    private suspend fun notALink(context: BaseContext) = context.forceReply("""
        Это не ссылка :(

        Чтобы отменить создание ренжика можно написать /cancel или нажать на кнопку ниже.
    """.trimIndent(), replyMarkup = buildInlineKeyboard {
        row(CancelButton())
    }.getMarkup(NullState(), context.controller.registry))
}

class ReceiveGroupState(val rangeId: Id<Range>) : GlobalState() {
    private val helpMessage = """
        Ты хочешь выбрать паблик/канал.

        В первую очередь убедись, что у тебя есть админка в чатике/канале.

        Чтобы выбрать, тебе надо сделать что нибудь из списка ниже:
        - Прислать мне id чата/канала, например `-1001208998514`.
        - Прислать мне username чата/канала, например `IERussia` или `@IERussia`.
        - Переслать мне любое сообщение из чата/канала (только если чат публичный).

        /cancel, если передумал.
    """.trimIndent()

    override val handlerList = buildHandlerList {
        text {
            if (message.forwardDate() != null && message.forwardFromChat() == null) {
                send("В пересланном сообщении нету id чата, такое может быть если чат приватный.")
                return@text
            }

            val chatId: Any = if (message.forwardFromChat() != null) {
                message.forwardFromChat().id()
            } else {
                val text: String = message.text()

                // "-1234" => -1234
                // "@username" or "username" => "@username"
                text.toIntOrNull() ?: "@" + text.dropWhile { it == '@' }
            }

            selectChatId(chatId, this)
        }

        command("cancel") {
            send("Ok")
            done(this)
        }

        command("start") {
            if (arguments.isNotEmpty()) {
                val chatId = arguments.first()
                selectChatId(chatId, this)
            } else {
                send(helpMessage)
            }
        }

        fallback {  // onEnter too
            send(helpMessage)
        }
    }

    private suspend fun selectChatId(chatId: Any, context: BaseContext) {
        val me = context.controller.me.user()

        coroutineScope {
            val chatDeferred = async {
                context.controller.bot.executeUnsafe(GetChat(chatId))
            }
            val meInChatDeferred = async {
                context.controller.bot.executeUnsafe(GetChatMember(chatId, me.id()))
            }
            val adminsDeferred = async {
                context.controller.bot.executeUnsafe(GetChatAdministrators(chatId))
            }

            val chat = chatDeferred.await()
            val meInChat = meInChatDeferred.await()
            val admins = adminsDeferred.await()

            when {
                chat.chat() == null -> {
                    context.send("""Не могу найти этот чат. ${"\uD83D\uDE14"}
                    |`$chatId`""".trimMargin())
                }
                meInChat.chatMember() == null || meInChat.chatMember().status() == ChatMember.Status.left -> {
                    context.send("Меня нету в чате. \uD83E\uDD28")
                }
                meInChat.chatMember().status() == ChatMember.Status.kicked -> {
                    context.send("Но меня же кикнули. \uD83E\uDD14")
                }
                meInChat.chatMember().canSendMessages() == false -> {
                    context.send("Это все конечно замечательно, но у меня нет прав отправлять сообщения в этом чате. \uD83D\uDE12")
                }
                admins.administrators() == null -> {
                    context.send("Кажется такого не должно было случится, но я почему то не могу проверить, являешься ли ты админом. \uD83E\uDD14")
                }
                admins.administrators().none { it.user().id() == context.userId } -> {
                    context.send("Ты не админ, геюга!")
                }
                else -> {
                    context.send("Океюшки, выбираем чатик _${chat.chat().title()}_.")
                    replacePostTarget(PostTarget(chat.chat().id(), chat.chat().title(), chat.chat().type()), context)
                    done(context)
                }
            }
        }
    }

    private suspend fun replacePostTarget(target: PostTarget, context: BaseContext) {
        val range = context.controller.repository.getRange(rangeId)
        if (range == null) {
            NotFoundErrorState().send(context)
        } else {
            context.controller.stopRange(range)
            val newRange = range.copy(postTarget = target)
            context.controller.repository.saveRange(newRange)
            context.controller.startRange(range)
        }
    }

    private suspend fun done(context: BaseContext) {
        context.changeGlobalState(DefaultState())
    }
}