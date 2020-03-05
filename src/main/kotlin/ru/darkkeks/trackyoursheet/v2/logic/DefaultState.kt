package ru.darkkeks.trackyoursheet.v2.logic

import ru.darkkeks.trackyoursheet.v2.telegram.GlobalState
import ru.darkkeks.trackyoursheet.v2.telegram.buildHandlerList

class DefaultState : GlobalState() {
    override val handlerList = buildHandlerList {
        command("new_range") { changeGlobalState(NewRangeState()) }
        command("list_ranges") { RangeListState().send(this) }
        fallback { MainMenuState().send(this) }
    }
}