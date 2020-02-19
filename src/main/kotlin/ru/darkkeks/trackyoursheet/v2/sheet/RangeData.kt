package ru.darkkeks.trackyoursheet.v2.sheet

import com.fasterxml.jackson.annotation.JsonCreator
import com.google.api.services.sheets.v4.model.GridData
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.v2.Range
import java.time.Instant

class RangeData {

    val start: Cell
    val data: List<List<String>>
    val notes: Map<Cell, String>
    val job: Id<Range>
    val time: Instant
    val _id: Id<RangeData>

    @JsonCreator
    constructor(start: Cell,
                data: List<List<String>>,
                notes: Map<Cell, String>,
                job: Id<Range>,
                time: Instant = Instant.now(),
                _id: Id<RangeData> = newId()) {
        this.start = start
        this.data = data
        this.notes = notes
        this.job = job
        this.time = time
        this._id = _id
    }

    constructor(grid: GridData,
                job: Id<Range>,
                time: Instant = Instant.now(),
                _id: Id<RangeData> = newId()) {
        start = Cell(grid.startRow ?: 0, grid.startColumn ?: 0)
        this.job = job
        this.time = time
        this._id = _id

        val mutableNotes = mutableMapOf<Cell, String>()
        data = List(grid.rowData.size) { i ->
            val row = grid.rowData[i].getValues()
            List(row.size) { j ->
                val cell = row[j]
                if (cell.note != null) {
                    mutableNotes[Cell(i, j)] = cell.note
                }
                cell.formattedValue ?: ""
            }
        }
        notes = mutableNotes
    }
}