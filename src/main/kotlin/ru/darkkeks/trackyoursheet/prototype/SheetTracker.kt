package ru.darkkeks.trackyoursheet.prototype

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.newId
import kotlin.system.measureTimeMillis

// FIXME Не нравится мне эта реализация CoroutineScope, что то тут не так :thinking:
//  Нужно оно чтобы делать ProducerChannel, так как produce -- функция у CoroutineScope
class SheetTracker(kodein: Kodein) : CoroutineScope by GlobalScope {

    val sheetApi: SheetApi by kodein.instance()

    val trackDao = SheetTrackDao(kodein)

    val jobs = mutableMapOf<TrackJob, ReceiveChannel<DataEvent>>()

    val dataCompareService = DataCompareService()

    fun addJob(trackJob: TrackJob): ReceiveChannel<DataEvent> {
        require(!jobs.contains(trackJob)) {
            "Job is already being tracked"
        }

        val channel = produce {
            when (trackJob.interval) {
                is PeriodTrackInterval -> {
                    // FIXME Хочется нормальный шедулер, а не задержку
                    //  Между обновлениями будет не periodSeconds секунд. Но даже если сделать какое нибудь вычисление
                    //  когда выполнять в следующий раз, поползут проблемы если runJob будет выполняться дольше чем
                    //  надо. Я просто уверен, что есть замена в стандартной библиотеке (как минимум прикрутить корутины
                    //  к java.util.Timer), но я не придумал как лучше :(
                    while (true) {
                        runJob(trackJob).forEach { send(it) }
                        delay(1000L * trackJob.interval.periodSeconds)
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
                sheetApi.getRanges(job.sheet, listOf(job.range))
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