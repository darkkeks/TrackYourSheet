package ru.darkkeks.trackyoursheet.prototype

import ru.darkkeks.trackyoursheet.prototype.telegram.CommandContext
import ru.darkkeks.trackyoursheet.prototype.telegram.GlobalUserState
import ru.darkkeks.trackyoursheet.prototype.telegram.UserActionContext
import ru.darkkeks.trackyoursheet.prototype.telegram.handle

class DefaultState : GlobalUserState() {

    override suspend fun handleMessage(context: UserActionContext) = handle(context) {
        startMessage(context)
    }

    override suspend fun handleCommand(context: CommandContext): Boolean {
        when (context.command) {
            "start" -> startMessage(context)
            "help" -> startMessage(context)
            "new_range" -> {
                val state = NewRangeState()
                context.changeState(state)
                state.initiate(context)
            }
            "list_ranges" -> {
                val dao = context.controller.sheetDao
                val user = dao.getOrCreateUser(context.userId)

                val ranges = dao.getUserJobs(user._id)

                val rangeList = if (ranges.isNotEmpty()) {
                    ranges.joinToString("\n") {
                        "[${it.range}](${it.sheet.urlTo(it.range)}) с интервалом ${it.interval}"
                    }
                } else {
                    "Тут пусто ;("
                }

                context.reply("Ваши ренжики:\n$rangeList")
            }
            else -> return false
        }
        return true
    }

    suspend fun startMessage(context: UserActionContext) = context.reply("""
        Привет, я умею наблюдать за гугл-табличками. ${"\uD83E\uDD13"}
        
        Давай будем называть _ренжем_ (_ренж_, _ренжик_, в _ренже_, _ренжику_, от англ. _range_) диапазон не некотором листе в гугл таблице. 
        Я не смог придумать название лучше, если есть идеи -- всегда можно написать @darkkeks.
        
        Сюда еще хелпы надо, напишите @lodthe чтобы добавил))0)).
    """.trimIndent())
}