package ru.darkkeks.trackyoursheet.prototype

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.pengrad.telegrambot.model.Chat
import org.bson.types.ObjectId
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.descending
import org.litote.kmongo.eq
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.prototype.sheet.CellRange
import ru.darkkeks.trackyoursheet.prototype.sheet.RangeData
import ru.darkkeks.trackyoursheet.prototype.sheet.SheetData
import ru.darkkeks.trackyoursheet.prototype.states.DefaultState
import ru.darkkeks.trackyoursheet.prototype.telegram.CallbackButton
import ru.darkkeks.trackyoursheet.prototype.telegram.GlobalUserState
import java.time.Duration

data class BotUser(
    val userId: Int,
    val globalState: GlobalUserState = DefaultState(),
    val _id: Id<BotUser> = newId()
)

data class PostTarget(
    val chatId: Long,
    val name: String,
    val type: Chat.Type
) {
    companion object {
        fun private(userId: Int) = PostTarget(userId.toLong(), "сюда", Chat.Type.Private)
    }
}

data class Range(
    val sheet: SheetData,
    val range: CellRange,
    val interval: TrackInterval,
    val owner: Id<BotUser>,
    val enabled: Boolean,
    val postTarget: PostTarget,
    val _id: Id<Range> = newId()
)

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
abstract class TrackInterval

data class PeriodTrackInterval(val period: Long) : TrackInterval() {

    constructor(duration: Duration) : this(duration.seconds)

    fun asDuration() = Duration.ofSeconds(period)

    override fun toString(): String = TimeUnits.durationToString(asDuration())
}

class SheetTrackDao(kodein: Kodein) {
    val database: CoroutineDatabase by kodein.instance()
    val users = database.getCollection<BotUser>()
    val jobs = database.getCollection<Range>()
    val rangeData = database.getCollection<RangeData>()
    val buttons = database.getCollection<CallbackButton>()

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

    suspend fun saveButton(button: CallbackButton) {
        buttons.save(button)
    }

    suspend fun getButton(hexString: String): CallbackButton? {
        return buttons.findOneById(ObjectId(hexString))
    }

    suspend fun deleteJob(rangeId: Id<Range>) {
        jobs.deleteOneById(ObjectId(rangeId.toString()))
    }
}