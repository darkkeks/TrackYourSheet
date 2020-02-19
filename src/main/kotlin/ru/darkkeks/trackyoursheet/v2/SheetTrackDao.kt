package ru.darkkeks.trackyoursheet.v2

import org.bson.types.ObjectId
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.descending
import org.litote.kmongo.eq
import ru.darkkeks.trackyoursheet.v2.sheet.RangeData

class SheetTrackDao(kodein: Kodein) {
    val database: CoroutineDatabase by kodein.instance()
    val users = database.getCollection<BotUser>()
    val jobs = database.getCollection<Range>()
    val rangeData = database.getCollection<RangeData>()

    suspend fun getLastData(id: Id<Range>): RangeData? {
        return rangeData.find(RangeData::job eq id)
            .sort(descending(RangeData::time))
            .limit(1)
            .first()
    }

    suspend fun saveData(data: RangeData) {
        rangeData.save(data)
    }

    suspend fun getUserJobs(id: Id<BotUser>): List<Range> {
        return jobs.find(Range::owner eq id).toList()
    }

    suspend fun getJob(id: Id<Range>) = jobs.findOneById(ObjectId(id.toString()))

    suspend fun getAllJobs() = jobs.find().toList()

    suspend fun getOrCreateUser(userId: Int): BotUser {
        return getUser(userId) ?: BotUser(userId).also {
            saveUser(it)
        }
    }

    suspend fun getUser(userId: Int): BotUser? {
        return users.findOne(BotUser::userId eq userId)
    }

    suspend fun getUserById(id: Id<BotUser>): BotUser? {
        return users.findOneById(ObjectId(id.toString()))
    }

    suspend fun saveUser(user: BotUser) {
        users.save(user)
    }

    suspend fun saveJob(range: Range) {
        jobs.save(range)
    }

    suspend fun deleteJob(rangeId: Id<Range>) {
        jobs.deleteOneById(ObjectId(rangeId.toString()))
    }
}