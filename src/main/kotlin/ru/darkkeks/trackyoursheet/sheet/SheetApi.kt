package ru.darkkeks.trackyoursheet.sheet

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
import java.net.URI
import java.net.URISyntaxException
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
        private val DOMAIN_PATTERN = """(www\.)?docs\.google\.com""".toRegex()
        private val PATH_PATTERN = """/spreadsheets/d/([a-zA-Z\d-_]+)/?.*?""".toRegex()

        data class SheetUrlMatchResult(val id: String, val arguments: Map<String, String> = emptyMap())

        /**
         * Extracts spreadsheet id and url arguments
         * @return Pair of spreadsheet id and map of arguments, or null if couldn't match
         */
        fun fromUrl(string: String): SheetUrlMatchResult? {
            try {
                var processed = string
                if (!string.startsWith("http")) processed = "https://$processed"

                val url = URI(processed)
                if (!DOMAIN_PATTERN.matches(url.host)) return null
                val path = PATH_PATTERN.matchEntire(url.path) ?: return null
                val id = path.groupValues[1]

                val hash: String? = url.fragment
                val parts = if (hash == null || hash.isEmpty()) listOf() else hash.split("&")
                val args = parts.map {
                    val x = it.split('=', limit = 2)
                    if (x.size == 2) x[0] to x[1] else x[0] to ""
                }.toMap()

                return SheetUrlMatchResult(id, args)
            } catch (e: URISyntaxException) {
                return null
            }
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

        const val NOT_SET = -1

        val ROW_PATTERN = """[1-9]\d*""".toRegex()
        val COLUMN_PATTERN = """[A-Z]+""".toRegex()

        val CELL_PATTERN = """($COLUMN_PATTERN)($ROW_PATTERN)""".toRegex()

        fun fromString(value: String): Cell? {
            return when {
                value matches CELL_PATTERN -> {
                    val (col, row) = CELL_PATTERN.find(value)!!.groupValues.drop(1)
                    Cell(row.toInt(), sheetStringToIndex(col))
                }
                value matches ROW_PATTERN -> {
                    Cell(value.toInt(), NOT_SET)
                }
                value matches COLUMN_PATTERN -> {
                    Cell(NOT_SET, sheetStringToIndex(value))
                }
                else -> null
            }
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
        private val RANGE_PATTERN = listOf(
            "${Cell.CELL_PATTERN}:${Cell.CELL_PATTERN}",
            "${Cell.ROW_PATTERN}:${Cell.ROW_PATTERN}",
            "${Cell.COLUMN_PATTERN}:${Cell.COLUMN_PATTERN}",
            "${Cell.CELL_PATTERN}",
            "${Cell.ROW_PATTERN}",
            "${Cell.COLUMN_PATTERN}"
        ).joinToString("|").toRegex()

        fun isRange(value: String) = value matches RANGE_PATTERN

        fun fromString(value: String): CellRange {
            require(isRange(value))

            return if (value.contains(":")) {
                val (left, right) = value.split(":")
                val from = Cell.fromString(left)
                val to = Cell.fromString(right)

                require(from != null && to != null)
                CellRange(from, to)
            } else {
                val cell = Cell.fromString(value)
                require(cell != null)
                CellRange(cell, cell)
            }
        }
    }
}