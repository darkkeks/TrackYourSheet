package ru.darkkeks.trackyoursheet.prototype.sheet

import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.Spreadsheet

typealias EventListener = (DataEvent) -> Unit

abstract class DataEvent

abstract class CellEvent(
    val sheet: Sheet,
    val cell: Cell
) : DataEvent()

class AddTextEvent(
    sheet: Sheet,
    cell: Cell,
    val text: String
) : CellEvent(sheet, cell)

class ModifyTextEvent(
    sheet: Sheet,
    cell: Cell,
    val oldText: String,
    val newText: String
) : CellEvent(sheet, cell)

class RemoveTextEvent(
    sheet: Sheet,
    cell: Cell,
    val text: String
) : CellEvent(sheet, cell)

class ModifyNoteEvent(
    sheet: Sheet,
    cell: Cell,
    val oldNote: String,
    val newNote: String
) : CellEvent(sheet, cell)

class AddNoteEvent(
    sheet: Sheet,
    cell: Cell,
    val note: String
) : CellEvent(sheet, cell)

class RemoveNoteEvent(
    sheet: Sheet,
    cell: Cell,
    val note: String
) : CellEvent(sheet, cell)

data class InitialDataLoadEvent(
    val spreadsheet: Spreadsheet
) : DataEvent()