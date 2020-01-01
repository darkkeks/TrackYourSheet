package ru.darkkeks.trackyoursheet.prototype.sheet

import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.Spreadsheet

typealias EventListener = (DataEvent) -> Unit

abstract class DataEvent

class CellTextModifyEvent(
    val sheet: Sheet,
    val cell: Cell,
    val oldText: String,
    val newText: String
) : DataEvent()

class InitialDataLoadEvent(
    val spreadsheet: Spreadsheet
) : DataEvent()
