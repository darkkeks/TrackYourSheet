package ru.darkkeks.trackyoursheet.logic

import com.google.api.services.sheets.v4.model.Sheet
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import ru.darkkeks.trackyoursheet.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.PostTarget
import ru.darkkeks.trackyoursheet.Range
import ru.darkkeks.trackyoursheet.sheet.CellRange
import ru.darkkeks.trackyoursheet.sheet.SheetData
import ru.darkkeks.trackyoursheet.telegram.*
import java.time.Duration

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
                Сейчас я помогу тебе создать ренж, для этого мне нужна ссылка на табличку.
                
                Можешь скинуть мне просто ссылку на табличку, тогда я помогу выбрать лист и ренж на нём.
                
                Либо ссылка может сразу указывать на нужный ренж (что получить такую, кликни в табличке правой кнопкой по выделенной области и выбрери _Получить ссылку на этот диапазон_)

                Выглядят ссылки вот так:
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
                        
                        Еще можно создавать ренжи, которые трекают промежуток колонок или сток, например '3:4' трекает третий и четвертый ряд в таблице.
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
                        Вижу табличку и лист, осталось указать за каким ренжем наблюдать. Напиши вот в таком формате: `A2:BE26`.
                        
                        Еще можно создавать ренжи, которые трекают промежуток колонок или сток, например '3:4' трекает третий и четвертый ряд в таблице.
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
                    Это не ренж, попробуй еще раз. Правильный ренж должен быть похож на такой `A2:B26`, либо на `A:C` или `2:5`, если надо выбрать промежуток строк или столбцов.
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
        context.changeGlobalState(DefaultState(), noInit = true)
    }

    private fun createSheetSelectKeyboard(sheets: List<Sheet>, context: BaseContext) = buildInlineKeyboard {
        sheets.forEach {
            row(SheetSelectButton(it.properties.sheetId, it.properties.title))
        }
    }.getMarkup(NullState(), context.controller.registry)

    private suspend fun notALink(context: BaseContext) = context.forceReply("""
        Твоё сообщение не похоже на правильную ссылку, она должна выглядеть примерно вот так:
        
        ```https://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-asdfghjklasdfdsaf/edit#gid=13371488228&range=A1:Z22```

        Чтобы отменить создание ренжика можно написать /cancel или нажать на кнопку ниже.
    """.trimIndent(), replyMarkup = buildInlineKeyboard {
        row(NewRangeState.CancelButton())
    }.getMarkup(NullState(), context.controller.registry))
}