package ru.darkkeks.trackyoursheet.prototype

import ru.darkkeks.trackyoursheet.prototype.telegram.*

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

                context.reply("Ваши ренжики:\n\n$rangeList")
            }
            else -> return false
        }
        return true
    }

    suspend fun startMessage(context: UserActionContext) = MainMenuState().send(context)

}