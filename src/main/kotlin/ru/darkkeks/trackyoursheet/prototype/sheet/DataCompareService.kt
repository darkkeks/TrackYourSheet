package ru.darkkeks.trackyoursheet.prototype.sheet

import com.google.api.services.sheets.v4.model.Sheet
import kotlin.math.max

class DataCompareService {
    fun compare(sheet: Sheet, old: RangeData, new: RangeData, block: EventListener) {
        require(old.start == new.start)
        require(old.data.size == new.data.size)

        for (i in new.data.indices) {
            val oRow = old.data[i]
            val nRow = new.data[i]

            val columns = max(oRow.size, nRow.size)
            for (j in 0 until columns) {
                if (oRow[j] != nRow[j]) {
                    block(CellTextModifyEvent(sheet, new.start + Cell(i, j), oRow[j], nRow[j]))
                }
            }
        }
    }
}