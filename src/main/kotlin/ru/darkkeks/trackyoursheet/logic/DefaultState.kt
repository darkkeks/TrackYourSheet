package ru.darkkeks.trackyoursheet.logic

import ru.darkkeks.trackyoursheet.telegram.GlobalState
import ru.darkkeks.trackyoursheet.telegram.buildHandlerList

class DefaultState : GlobalState() {
    override val handlerList = buildHandlerList {
        command("new_range") { changeGlobalState(NewRangeState()) }
        command("list_ranges") { RangeListState().send(this) }
        fallback { MainMenuState().send(this) }
    }
}