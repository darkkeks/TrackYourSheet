package ru.darkkeks.trackyoursheet.prototype.sheet

import com.google.api.services.sheets.v4.model.GridData
import com.google.api.services.sheets.v4.model.Sheet
import ru.darkkeks.trackyoursheet.prototype.RangeData
import kotlin.math.max

class DataCompareService {

    fun compare(old: RangeData, new: RangeData, block: EventListener) {
        // TODO I don't actually know how this all is useful, because there is no batching yet

        val oldSheets = toSheetMap(old)
        val newSheets = toSheetMap(new)
        oldSheets.keys.intersect(newSheets.keys).forEach { sheetId ->
            val oldSheet = oldSheets.getValue(sheetId)
            val newSheet = newSheets.getValue(sheetId)

            val oldData = toGridMap(oldSheet.data)
            val newData = toGridMap(newSheet.data)

            oldData.keys.intersect(newData.keys).forEach { gridId ->
                compareGrids(newSheet, oldData.getValue(gridId), newData.getValue(gridId)) { event ->
                    block(event)
                }
            }
        }
    }

    private fun compareGrids(sheet: Sheet, old: GridData, new: GridData, block: EventListener) {
        require(old.rowData.size == new.rowData.size)

        val startCell = Cell(new.startRow, new.startColumn)
        for (i in 0 until new.rowData.size) {
            val oRow = old.rowData[i].getValues()
            val nRow = new.rowData[i].getValues()

            val columns = max(oRow.size, nRow.size)
            for (j in 0 until columns) {
                if (j < oRow.size && j < nRow.size) {
                    if (oRow[j].formattedValue != nRow[j].formattedValue) {
                        block(
                            CellTextModifyEvent(
                                sheet,
                                startCell + Cell(i, j),
                                oRow[j].formattedValue ?: "",
                                nRow[j].formattedValue ?: ""
                            )
                        )
                    }
                } else {
                    // TODO
                    assert(false) { "Empty cells ???" }
                }
            }
        }
    }

    private fun toGridMap(grids: List<GridData>) = grids.associateBy { it.startRow to it.startColumn }

    private fun toSheetMap(data: RangeData) = data.data.sheets.associateBy { it.properties.sheetId }
}