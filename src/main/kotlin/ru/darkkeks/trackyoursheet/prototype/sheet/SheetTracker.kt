package ru.darkkeks.trackyoursheet.prototype.sheet

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.prototype.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.prototype.SheetTrackDao
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import kotlin.IllegalStateException


class SheetTracker(kodein: Kodein) {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val sheetApi: SheetApi by kodein.instance()

    private val trackDao: SheetTrackDao by kodein.instance()

    private val jobs: MutableMap<Id<TrackJob>, ReceiveChannel<DataEvent>> = mutableMapOf()

    private val dataCompareService = DataCompareService()

    fun addJob(trackJob: TrackJob): ReceiveChannel<DataEvent> {
        require(!jobs.contains(trackJob._id)) {
            "Job is already being tracked"
        }

        val channel = scope.produce {
            when (trackJob.interval) {
                is PeriodTrackInterval -> {
                    while (true) {
                        scope.launch {
                            runJob(trackJob).forEach { send(it) }
                        }
                        delay(trackJob.interval.asDuration().toMillis())
                    }
                }
                else -> throw IllegalArgumentException("Unsupported time interval ${trackJob.interval}")
            }
        }

        jobs[trackJob._id] = channel
        return channel
    }

    fun removeJob(trackJob: TrackJob) {
        jobs.remove(trackJob._id)?.cancel()
    }

    private suspend fun runJob(job: TrackJob) = buildList<DataEvent> {
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

class ListBuilder<T> {
    private val result = mutableListOf<T>()

    fun add(vararg values: T) {
        values.forEach {
            result.add(it)
        }
    }

    fun build() = result.toList()
}

inline fun <T> buildList(block: ListBuilder<T>.() -> Unit): List<T> {
    val builder = ListBuilder<T>()
    builder.block()
    return builder.build()
}