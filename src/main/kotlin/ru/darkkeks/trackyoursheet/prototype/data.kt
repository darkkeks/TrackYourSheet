package ru.darkkeks.trackyoursheet.prototype

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.api.services.sheets.v4.model.Spreadsheet
import org.bson.types.ObjectId
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.litote.kmongo.newId
import java.time.Instant

data class User(
    val userId: Int,
    val _id: Id<User> = newId()
)

data class PostTarget(
    val chatId: Long,
    val owner: Id<User>,
    val _id: Id<User> = newId()
)

data class TrackJob(
    val sheet: SheetData,
    val range: String, // TODO Should be range, not string?
    val interval: TrackInterval,
    val owner: Id<User>,
    val _id: Id<TrackJob> = newId()
)

data class RangeData(
    val data: Spreadsheet,
    val jobId: Id<TrackJob>,
    val time: Instant = Instant.now(),
    val _id: Id<RangeData> = newId()
)

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class TrackInterval

data class PeriodTrackInterval(val periodSeconds: Int) : TrackInterval() {
    override fun toString() = "$periodSeconds секунд"
}


class SheetTrackDao(kodein: Kodein) {
    val database: CoroutineDatabase by kodein.instance()
    val users = database.getCollection<User>()
    val jobs = database.getCollection<TrackJob>()

    val rangeData = mutableMapOf<Id<TrackJob>, RangeData>()
//    val rangeData = database.getCollection<RangeData>()

    suspend fun getLastData(id: Id<TrackJob>): RangeData? {
        return rangeData[id]
//        return rangeData.find(RangeData::jobId eq id)
//            .sort(descending(RangeData::time))
//            .limit(1)
//            .first()
    }

    suspend fun saveData(data: RangeData) {
        rangeData[data.jobId] = data
//        rangeData.save(data)
    }

    suspend fun getUserJobs(id: Id<User>): List<TrackJob> {
        return jobs.find(TrackJob::owner eq id).toList()
    }

    suspend fun getAllJobs() = jobs.find().toList()

    suspend fun getOrCreateUser(userId: Int): User {
        return getUser(userId) ?: User(userId).also {
            saveUser(it)
        }
    }

    suspend fun getUser(userId: Int): User? {
        return users.findOne(User::userId eq userId)
    }

    suspend fun getUserById(id: Id<User>): User? {
        return users.findOneById(ObjectId(id.toString()))
    }

    suspend fun saveUser(user: User) {
        users.save(user)
    }

    suspend fun saveJob(trackJob: TrackJob) {
        jobs.save(trackJob)
    }
}