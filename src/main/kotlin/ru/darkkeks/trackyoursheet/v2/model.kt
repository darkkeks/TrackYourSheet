package ru.darkkeks.trackyoursheet.v2

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.pengrad.telegrambot.model.Chat
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.trackyoursheet.v2.sheet.CellRange
import ru.darkkeks.trackyoursheet.v2.sheet.SheetData
import ru.darkkeks.trackyoursheet.v2.telegram.GlobalState
import java.time.Duration

data class UserModel(
    val userId: Int,
    val state: GlobalState = DefaultState(),
    val _id: Id<UserModel> = newId()
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
    val owner: Id<UserModel>,
    val enabled: Boolean,
    val postTarget: PostTarget,
    val _id: Id<Range> = newId()
)


@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
abstract class TrackInterval

data class PeriodTrackInterval(val period: Long) : TrackInterval() {

    constructor(duration: Duration) : this(duration.seconds)

    fun asDuration(): Duration = Duration.ofSeconds(period)

    override fun toString(): String = TimeUnits.durationToString(asDuration())
}
