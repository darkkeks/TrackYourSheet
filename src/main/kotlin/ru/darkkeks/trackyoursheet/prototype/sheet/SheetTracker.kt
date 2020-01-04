package ru.darkkeks.trackyoursheet.prototype.sheet

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.prototype.PeriodTrackInterval
import ru.darkkeks.trackyoursheet.prototype.RangeData
import ru.darkkeks.trackyoursheet.prototype.SheetTrackDao
import ru.darkkeks.trackyoursheet.prototype.TrackJob
import java.time.Duration
import kotlin.system.measureTimeMillis


class SheetTracker(kodein: Kodein) {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val sheetApi: SheetApi by kodein.instance()

    private val trackDao: SheetTrackDao by kodein.instance()

    private val jobs: MutableMap<TrackJob, ReceiveChannel<DataEvent>> = mutableMapOf()

    private val dataCompareService = DataCompareService()

    fun addJob(trackJob: TrackJob): ReceiveChannel<DataEvent> {
        require(!jobs.contains(trackJob)) {
            "Job is already being tracked"
        }

        val channel = scope.produce {
            when (trackJob.interval) {
                is PeriodTrackInterval -> {
                    while (true) {
                        scope.launch {
                            runJob(trackJob).forEach { send(it) }
                        }
                        delay(Duration.ofSeconds(trackJob.interval.periodSeconds.toLong()).toMillis())
                    }
                }
                else -> throw IllegalArgumentException("Unsupported time interval ${trackJob.interval}")
            }
        }

        jobs[trackJob] = channel
        return channel
    }

    fun removeJob(trackJob: TrackJob) {
        jobs.remove(trackJob)?.cancel()
    }

    private suspend fun runJob(job: TrackJob): List<DataEvent> {
        val result = mutableListOf<DataEvent>()
        val time = measureTimeMillis {
            val data = withTimeout(5000) {
                sheetApi.getRanges(job.sheet, listOf("${job.sheet.sheetName}!${job.range}"))
            }

            val oldData = trackDao.getLastData(job._id)
            val newData = RangeData(data, job._id, _id = oldData?._id ?: newId())

            if (oldData == null) {
                result.add(InitialDataLoadEvent(data))
            } else {
                dataCompareService.compare(oldData, newData) { event ->
                    // FIXME Кто нибудь расскажите мне как это делать нормально без листа :D
                    result.add(event)
                }
            }

            trackDao.saveData(newData)
        }

        println("Spent $time millis processing track job ${job._id}")

        return result
    }
}