package ru.darkkeks.trackyoursheet.v2.sheet

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.Spreadsheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import kotlin.math.max
import kotlin.math.min

// TODO Error handling
class SheetApi(kodein: Kodein) {
    private val sheets: Sheets by kodein.instance()

    suspend fun getSheet(sheet: SheetData): Spreadsheet {
        return withContext(Dispatchers.IO) {
            sheets.spreadsheets().get(sheet.id)
                .setIncludeGridData(false)
                .execute()
        }
    }

    suspend fun getRanges(sheet: SheetData, ranges: List<String> = listOf()): Spreadsheet {
        return withContext(Dispatchers.IO) {
            sheets.spreadsheets().get(sheet.id)
                .setRanges(ranges)
                .setIncludeGridData(true)
                .execute()
        }
    }

    suspend fun getSheets(sheetId: String): List<Sheet> {
        return withContext(Dispatchers.IO) {
            sheets.spreadsheets().get(sheetId)
                .setIncludeGridData(false)
                .execute()
                .sheets
        }
    }
}

data class SheetData(val id: String, val sheetId: Int, val sheetName: String = "") {

    val url
        @JsonIgnore
        get() = "https://docs.google.com/spreadsheets/d/${id}"

    val sheetUrl
        @JsonIgnore
        get() = "${url}#gid=${sheetId}"

    fun urlTo(cell: Cell) = "${sheetUrl}&range=$cell"
    fun urlTo(value: String) = "${sheetUrl}&range=$value"
    fun urlTo(range: CellRange) = "${sheetUrl}&range=$range"

    companion object {
        /**
         * Matches google docs urls in this forms:
         *  - https://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667
         *  - http://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667
         *  - www.docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667
         *  - docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667&range=123
         *  - docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#
         *  - docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit
         *  - docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8#gid=1697515667
         *  - docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8
         *
         * First group in match is spreadsheet id. Second one (if present) is arguments without #, may be blank.
         */
        private val URL_PATTERN =
            """(?:https?://)?(?:www\.)?docs\.google\.com/spreadsheets/d/([a-zA-Z\d-_]+)(?:/edit)?(?:\?[^#]*)?(?:#(.+))?""".toRegex()

        data class SheetUrlMatchResult(val id: String, val arguments: Map<String, String> = emptyMap())

        /**
         * Extracts spreadsheet id and url arguments
         * @return Pair of spreadsheet id and map of arguments, or null if couldn't match
         */
        fun fromUrl(url: String): SheetUrlMatchResult? {
            val match = URL_PATTERN.matchEntire(url) ?: return null
            val id = match.groupValues[1]
            val argumentString = match.groups[2]?.value

            if (argumentString?.isEmpty() != false) {
                return SheetUrlMatchResult(id)
            }

            val arguments = argumentString.split("&").map {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    parts[0] to ""
                }
            }.toMap()

            return SheetUrlMatchResult(id, arguments)
        }
    }
}

data class Cell(val row: Int, val column: Int) {

    operator fun plus(shift: Pair<Int, Int>) =
        Cell(row + shift.first, column + shift.second)

    operator fun plus(shift: Cell) = this + (shift.row to shift.column)

    operator fun plus(shift: Int) = this + (shift to shift)

    override fun toString() = "${indexToSheetString(column)}${row}"

    companion object {
        fun indexToSheetString(index: Int): String {
            var value = index
            var result = ""
            while (value > 0) {
                val digit = value % 26
                if (digit == 0) {
                    result += 'Z'
                    value -= 26
                } else {
                    result += 'A' + digit - 1
                }
                value /= 26
            }
            return result.reversed()
        }

        fun sheetStringToIndex(value: String): Int {
            var result = 0
            for (char in value) {
                result *= 26
                result += char - 'A' + 1
            }
            return result
        }


        val CELL_PATTERN = """([A-Z]+)(\d+)""".toRegex()

        fun fromString(value: String): Cell {
            val match = CELL_PATTERN.find(value)
            require(match != null)

            val (column, row) = match.groupValues.subList(1, 3)
            return Cell(row.toInt(), sheetStringToIndex(column))
        }
    }
}

class CellKeySerializer : JsonSerializer<Cell>() {
    override fun serialize(value: Cell, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeFieldName("${value.row},${value.column}")
    }
}

class CellKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): Any {
        val (r, c) = key.split(",")
        return Cell(r.toInt(), c.toInt())
    }
}

class CellRange(from: Cell, to: Cell) {
    val from = Cell(min(from.row, to.row), min(from.column, to.column))
    val to = Cell(max(from.row, to.row), max(from.column, to.column))

    override fun toString() = "$from:$to"

    companion object {
        val RANGE_PATTERN = """${Cell.CELL_PATTERN}:${Cell.CELL_PATTERN}""".toRegex()

        fun isRange(value: String) = value matches Cell.CELL_PATTERN || value matches RANGE_PATTERN

        fun fromString(value: String): CellRange {
            require(isRange(value))

            return if (value matches RANGE_PATTERN) {
                val (a, b) = value.split(":")
                CellRange(Cell.fromString(a), Cell.fromString(b))
            } else {
                val cell = Cell.fromString(value)
                CellRange(cell, cell)
            }
        }
    }
}