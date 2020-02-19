package ru.darkkeks.trackyoursheet.v2.sheet

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.v2.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.v2.Range
import ru.darkkeks.trackyoursheet.v2.SheetTrackDao
import ru.darkkeks.trackyoursheet.v2.buildList


class SheetTracker(kodein: Kodein) {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val sheetApi: SheetApi by kodein.instance()

    private val trackDao: SheetTrackDao by kodein.instance()

    private val jobs: MutableMap<Id<Range>, ReceiveChannel<DataEvent>> = mutableMapOf()

    private val dataCompareService = DataCompareService()

    fun addJob(range: Range): ReceiveChannel<DataEvent> {
        require(!jobs.contains(range._id)) {
            "Job is already being tracked"
        }

        val channel = scope.produce {
            when (range.interval) {
                is PeriodTrackInterval -> {
                    while (true) {
                        scope.launch {
                            runJob(range).forEach { send(it) }
                        }
                        delay(range.interval.asDuration().toMillis())
                    }
                }
                else -> throw IllegalArgumentException("Unsupported time interval ${range.interval}")
            }
        }

        jobs[range._id] = channel
        return channel
    }

    fun removeJob(range: Range) {
        jobs.remove(range._id)?.cancel()
    }

    private suspend fun runJob(job: Range) = buildList<DataEvent> {
        val data = withTimeout(5000) {
            sheetApi.getRanges(job.sheet, listOf("${job.sheet.sheetName}!${job.range}"))
        }
        val sheet = data.sheets.find {
            it.properties.sheetId == job.sheet.sheetId
        } ?: throw IllegalStateException("No sheet with id ${job.sheet.id} in response")
        val grid = sheet.data[0]

        val oldData = trackDao.getLastData(job._id)
        val newData = RangeData(grid, job._id, _id = oldData?._id ?: newId())

        if (oldData == null) {
            add(InitialDataLoadEvent(data))
        } else {
            dataCompareService.compare(sheet, oldData, newData) { add(it) }
        }

        trackDao.saveData(newData)
    }
}