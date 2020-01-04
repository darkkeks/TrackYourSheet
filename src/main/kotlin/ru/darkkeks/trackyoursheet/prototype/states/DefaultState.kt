package ru.darkkeks.trackyoursheet.prototype.states

import ru.darkkeks.trackyoursheet.prototype.telegram.*

class DefaultState : GlobalUserState() {

    override suspend fun handleMessage(context: UserActionContext) = handle(context) {
        startMessage(context)
    }

    override suspend fun handleCommand(context: CommandContext): Boolean {
        when (context.command) {
            "start" -> startMessage(context)
            "help" -> startMessage(context)
            "list_ranges" -> RangeListState().send(context)
            "new_range" -> {
                val state = NewRangeState()
                context.changeState(state)
                state.initiate(context)
            }
            else -> return false
        }
        return true
    }

    suspend fun startMessage(context: UserActionContext) = MainMenuState().send(context)

}