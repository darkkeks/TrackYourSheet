package ru.darkkeks.trackyoursheet.prototype.states

import com.google.api.services.sheets.v4.model.Sheet
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import ru.darkkeks.trackyoursheet.prototype.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import ru.darkkeks.trackyoursheet.prototype.sheet.CellRange
import ru.darkkeks.trackyoursheet.prototype.sheet.SheetData
import ru.darkkeks.trackyoursheet.prototype.telegram.*
import java.time.Duration

class NewRangeState : GlobalUserState(DefaultState()) {

    var spreadsheetId: String? = null
    var sheet: Sheet? = null
    var range: CellRange? = null

    suspend fun initiate(context: UserActionContext) {
        context.forceReply("""
            Окей, ща создадим, сейчас мне от тебя нужна ссылка на табличку.

            Можешь сразу дать ссылку на нужный ренж (выделить в гугл табличке -> пкм -> получить ссылку на этот диапазон).
            
            Либо просто ссылку на нужную таблчику/лист.

            Выглядит она вот так:
            ```https://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-asdfghjklasdfdsaf/edit#gid=13371488228&range=A1:Z22```
        """.trimIndent())
    }

    private suspend fun notALink(context: UserActionContext) = context.forceReply("""
        Это не ссылка :(
        
        Чтобы отменить создание ренжика можно написать /cancel или нажать на кнопку ниже.
    """.trimIndent())

    override suspend fun handleMessage(context: UserActionContext) = handle(context) {
        val text = context.message.text()

        if (text == null) {
            notALink(context)
            return@handle
        }

        val match = SheetData.fromUrl(text)
        if (match != null) {
            val (id, arguments) = match
            spreadsheetId = id

            val sheets = context.controller.sheetApi.getSheets(id)

            if ("gid" in arguments) {
                sheet = sheets.find {
                    it.properties.sheetId.toString() == arguments["gid"]
                }

                if (sheet != null && "range" in arguments) {
                    val rangeString = arguments.getValue("range")
                    if (CellRange.isRange(rangeString)) {
                        range = CellRange.fromString(rangeString)
                        finish(context)
                        return@handle
                    }
                }
            }

            if (sheet != null) {
                context.forceReply("""
                    Вижу табличку и лист, осталось указать какой ренж трекать. Напиши вот в таком формате: `A2:BE26`.
                """.trimIndent())
            } else {
                context.reply("""
                    Вижу [табличку](${SheetData(id, 0).sheetUrl}), теперь надо выбрать один из листов.
                """.trimIndent(), replyMarkup = createSheetSelectKeyboard(sheets, context))
            }
        } else if (spreadsheetId == null || sheet == null) {
            notALink(context)
            return@handle
        } else if (CellRange.isRange(text)) {
            range = CellRange.fromString(text)
            finish(context)
        } else {
            context.forceReply("""
                Это не ренж, попробуй еще раз.
            """.trimIndent())
        }
    }

    private suspend fun finish(context: UserActionContext) {
        val dao = context.controller.sheetDao
        val user = dao.getOrCreateUser(context.userId)

        val sheetData = SheetData(spreadsheetId!!, sheet!!.properties.sheetId, sheet!!.properties.title)
        val trackJob = TrackJob(
            sheetData,
            range!!,
            PeriodTrackInterval(Duration.ofSeconds(10)),
            user._id,
            true
        )
        dao.saveJob(trackJob)

        context.controller.startJob(trackJob)

        toParentState(context)

        context.reply("""
            Готово, буду трекать ренж [${range!!}](${sheetData.urlTo(range!!)}) на листе `${sheet!!.properties.title}`.
        """.trimIndent())
    }

    override suspend fun handleCommand(context: CommandContext): Boolean {
        when (context.command) {
            "cancel" -> {
                val state = DefaultState()
                state.startMessage(context)
                context.changeState(state)
            }
            else -> return false
        }
        return true
    }

    override suspend fun handleCallback(context: CallbackButtonContext) {
        when (val button = context.button) {
            is SheetSelectButton -> {
                val finalSpreadsheetId = spreadsheetId
                if (finalSpreadsheetId != null) {
                    val sheets = context.controller.sheetApi.getSheets(finalSpreadsheetId)

                    sheet = sheets.find {
                        it.properties.sheetId == button.sheetId
                    }

                    if (sheet != null) {
                        context.forceReply("""
                            Вижу табличку и лист, осталось указать какой ренж трекать. Напиши вот в таком формате: `A2:BE26`.
                        """.trimIndent())
                        context.answerCallbackQuery()
                    } else {
                        context.answerCallbackQuery("Такого листа нет, попробуй еще раз.")
                        context.bot.execute(EditMessageReplyMarkup(context.message.chat().id(),
                                                                   context.message.messageId())
                                                .replyMarkup(createSheetSelectKeyboard(sheets, context)))
                    }
                } else {
                    context.answerCallbackQuery("Сначала дай ссылку на таблицу")
                }
            }
            else -> Unit
        }
    }

    private suspend fun createSheetSelectKeyboard(sheets: List<Sheet>, context: UserActionContext) = buildInlineKeyboard {
        sheets.forEach {
            row(button(it.properties.title, context, SheetSelectButton(it.properties.sheetId)))
        }
    }

    class SheetSelectButton(val sheetId: Int) : GlobalStateButton()
}