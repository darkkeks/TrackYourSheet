package ru.darkkeks.trackyoursheet.v2

import org.bson.types.ObjectId
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.descending
import org.litote.kmongo.eq
import ru.darkkeks.trackyoursheet.v2.sheet.RangeData

class SheetTrackRepository(kodein: Kodein) {
    private val database: CoroutineDatabase by kodein.instance()
    private val users = database.getCollection<UserModel>()
    private val ranges = database.getCollection<Range>()
    private val rangeData = database.getCollection<RangeData>()

    suspend fun getLastData(id: Id<Range>): RangeData? {
        return rangeData.find(RangeData::job eq id)
            .sort(descending(RangeData::time))
            .limit(1)
            .first()
    }

    suspend fun saveData(data: RangeData) {
        rangeData.save(data)
    }

    suspend fun getUserRanges(id: Id<UserModel>): List<Range> {
        return ranges.find(Range::owner eq id).toList()
    }

    suspend fun getRange(id: Id<Range>) = ranges.findOneById(ObjectId(id.toString()))

    suspend fun getAllRanges() = ranges.find().toList()

    suspend fun getOrCreateUser(userId: Int): UserModel {
        return getUser(userId) ?: UserModel(userId).also {
            saveUser(it)
        }
    }

    suspend fun getUser(userId: Int): UserModel? {
        return users.findOne(UserModel::userId eq userId)
    }

    suspend fun getUserById(id: Id<UserModel>): UserModel? {
        return users.findOneById(ObjectId(id.toString()))
    }

    suspend fun saveUser(user: UserModel) {
        users.save(user)
    }

    suspend fun saveRange(range: Range) {
        ranges.save(range)
    }

    suspend fun deleteRange(rangeId: Id<Range>) {
        ranges.deleteOneById(ObjectId(rangeId.toString()))
    }
}