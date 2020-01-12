package ru.darkkeks.trackyoursheet.prototype.states

import ru.darkkeks.trackyoursheet.prototype.states.menu.MainMenuState
import ru.darkkeks.trackyoursheet.prototype.states.menu.RangeListState
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class DefaultState : GlobalUserState() {

    override suspend fun handleMessage(context: UserActionContext) = handle(context) {
        initiate(context)
    }

    override suspend fun handleCommand(context: CommandContext): Boolean {
        when (context.command) {
            "start" -> initiate(context)
            "help" -> initiate(context)
            "list_ranges" -> RangeListState().send(context)
            "new_range" -> {
                val state = NewRangeState()
                context.changeState(state)
            }
            else -> return false
        }
        return true
    }

    suspend fun initiate(context: UserActionContext) = MainMenuState().send(context)

}