package ru.darkkeks.trackyoursheet.prototype.states

import com.google.api.services.sheets.v4.model.Sheet
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import ru.darkkeks.trackyoursheet.prototype.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.prototype.PostTarget
import ru.darkkeks.trackyoursheet.prototype.Range
import ru.darkkeks.trackyoursheet.prototype.sheet.CellRange
import ru.darkkeks.trackyoursheet.prototype.sheet.SheetData
import ru.darkkeks.trackyoursheet.prototype.states.menu.RangeMenuState
import ru.darkkeks.trackyoursheet.prototype.telegram.*
import java.time.Duration

class NewRangeState : GlobalUserState() {

    var spreadsheetId: String? = null
    var sheetId: Int? = null
    var sheetTitle: String? = null
    var range: CellRange? = null

    override suspend fun onEnter(context: UserActionContext) {
        context.forceReply("""
            Окей, ща создадим, сейчас мне от тебя нужна ссылка на табличку.

            Можешь сразу дать ссылку на нужный ренж (выделить в гугл табличке -> пкм -> получить ссылку на этот диапазон).
            
            Либо просто ссылку на нужную таблчику, а я помогу выбрать лист и ренж.

            Выглядит она вот так:
            ```https://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-asdfghjklasdfdsaf/edit#gid=13371488228&range=A1:Z22```
        """.trimIndent())
    }

    private suspend fun notALink(context: UserActionContext) = context.forceReply("""
        Это не ссылка :(
        
        Чтобы отменить создание ренжика можно написать /cancel или нажать на кнопку ниже.
    """.trimIndent(), replyMarkup = buildInlineKeyboard {
        row(button("❌ Отмена", context, CancelButton()))
    })

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
                val sheet = sheets.find {
                    it.properties.sheetId.toString() == arguments["gid"]
                }

                if (sheet != null && "range" in arguments) {
                    sheetId = sheet.properties.sheetId
                    sheetTitle = sheet.properties.title
                    val rangeString = arguments.getValue("range")
                    if (CellRange.isRange(rangeString)) {
                        range = CellRange.fromString(rangeString)
                        finish(context)
                        return@handle
                    }
                }
            }

            if (sheetId != null) {
                context.forceReply("""
                    Вижу табличку и лист, осталось указать какой ренж трекать. Напиши вот в таком формате: `A2:BE26`.
                """.trimIndent())
            } else {
                context.reply("""
                    Вижу [табличку](${SheetData(id, 0).sheetUrl}), теперь надо выбрать один из листов.
                """.trimIndent(), replyMarkup = createSheetSelectKeyboard(sheets, context))
            }
        } else if (spreadsheetId == null || sheetId == null) {
            notALink(context)
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

        val sheetData = SheetData(spreadsheetId!!, sheetId!!, sheetTitle!!)
        val trackJob = Range(
            sheetData,
            range!!,
            PeriodTrackInterval(Duration.ofSeconds(30)),
            user._id,
            true,
            PostTarget.private(user.userId)
        )
        dao.saveJob(trackJob)

        context.controller.startJob(trackJob)

        context.reply("""
            Готово, буду трекать ренж [${range!!}](${sheetData.urlTo(range!!)}) на листе `$sheetTitle`.
        """.trimIndent())

        RangeMenuState(trackJob._id).send(context)
        context.changeState(DefaultState())
    }

    override suspend fun handleCommand(context: CommandContext): Boolean {
        when (context.command) {
            "cancel" -> {
                val state = DefaultState()
                context.changeState(state)
                state.initiate(context)
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

                    val sheet = sheets.find {
                        it.properties.sheetId == button.sheetId
                    }

                    if (sheet != null) {
                        sheetId = sheet.properties.sheetId
                        sheetTitle = sheet.properties.title
                        context.forceReply("""
                            Вижу табличку и лист, осталось указать какой ренж трекать. Напиши вот в таком формате: `A2:BE26`.
                        """.trimIndent())
                        context.answerCallbackQuery()
                    } else {
                        context.answerCallbackQuery("Такого листа нет, попробуй еще раз.")
                        context.bot.execute(EditMessageReplyMarkup(context.message.chat().id(), context.message.messageId())
                                                .replyMarkup(createSheetSelectKeyboard(sheets, context)))
                    }
                } else {
                    context.answerCallbackQuery("Сначала дай ссылку на таблицу")
                }
            }
            is CancelButton -> {
                val state = DefaultState()
                context.changeState(state)
                state.initiate(context)
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
    class CancelButton() : GlobalStateButton()
}