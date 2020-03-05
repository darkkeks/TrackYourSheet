package ru.darkkeks.trackyoursheet.sheet

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
                    block(when {
                        oRow[j].isEmpty() -> AddTextEvent(sheet, new.start + Cell(i, j), nRow[j])
                        nRow[j].isEmpty() -> RemoveTextEvent(sheet, new.start + Cell(i, j), oRow[j])
                        else -> ModifyTextEvent(sheet, new.start + Cell(i, j), oRow[j], nRow[j])
                    })
                }
            }
        }

        old.notes.keys.intersect(new.notes.keys).forEach {
            if (old.notes[it] != new.notes[it]) {
                block(ModifyNoteEvent(sheet, new.start + it, old.notes.getValue(it), new.notes.getValue(it)))
            }
        }

        old.notes.keys.minus(new.notes.keys).forEach {
            block(RemoveNoteEvent(sheet, new.start + it, old.notes.getValue(it)))
        }

        new.notes.keys.minus(old.notes.keys).forEach {
            block(AddNoteEvent(sheet, new.start + it, new.notes.getValue(it)))
        }
    }
}