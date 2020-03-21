package ru.darkkeks.trackyoursheet.sheet

import com.google.api.services.sheets.v4.model.Sheet

class DataCompareService {
    fun compare(sheet: Sheet, old: RangeData, new: RangeData, block: EventListener) {
        require(old.start == new.start)

        val oDim = old.dimensions
        val nDim = new.dimensions

        if (oDim != nDim) {
            block(DimensionsChangeEvent(sheet, oDim, nDim))
        }

        for (i in new.data.indices.intersect(old.data.indices)) {
            val oRow = old.data[i]
            val nRow = new.data[i]

            for (j in oRow.indices.intersect(nRow.indices)) {
                if (oRow[j] != nRow[j]) {
                    val event = when {
                        oRow[j].isEmpty() -> AddTextEvent(sheet, new.start + Cell(i, j), nRow[j])
                        nRow[j].isEmpty() -> RemoveTextEvent(sheet, new.start + Cell(i, j), oRow[j])
                        else -> ModifyTextEvent(sheet, new.start + Cell(i, j), oRow[j], nRow[j])
                    }
                    block(event)
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