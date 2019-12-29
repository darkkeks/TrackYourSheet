import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.pengrad.telegrambot.Callback
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.BaseResponse
import com.pengrad.telegrambot.response.SendResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


// FIXME env
val BOT_TOKEN: String = System.getenv("BOT_TOKEN") ?: throw IllegalStateException()

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class ButtonInfo

data class ButtonData(
    val state: State,
    val button: ButtonInfo,
    val _id: Id<ButtonInfo> = newId()
)

class ButtonDao(kodein: Kodein) {
    private val database: CoroutineDatabase by kodein.instance()
    private val buttons = database.getCollection<ButtonData>()

    suspend fun saveButtons(buttonList: List<ButtonData>) {
        buttons.bulkWrite(buttonList.map {
            insertOne(it)
        })
    }

    suspend fun saveButton(button: ButtonData) {
        buttons.insertOne(button)
    }

    suspend fun getButton(id: ObjectId): ButtonData? {
        return buttons.findOneById(id)
    }
}

data class User(
    val userId: Int,
    val jobs: List<Id<TrackJob>>,
    val _id: Id<User> = newId()
)

data class PostTarget(
    val chatId: Long,
    val owner: Id<User>,
    val _id: Id<User> = newId()
)

data class SheetData(val id: String) {
    val url get() = "https://docs.google.com/spreadsheets/d/${id}"
}

data class TrackJob(
    val sheet: SheetData,
    val range: String,
    val interval: TrackInterval,
    val _id: Id<TrackJob> = newId()
)

abstract class TrackInterval

class PeriodTrackInterval(val periodSeconds: Int) : TrackInterval()

class UserDao(kodein: Kodein) {
    private val database: CoroutineDatabase by kodein.instance()
    private val users = database.getCollection<User>()
    private val trackJobs = database.getCollection<TrackJob>()

    suspend fun getUser(userId: Int) = users.findOne(User::userId eq userId)

    suspend fun getJob(job: Id<TrackJob>): TrackJob? {
        return trackJobs.findOneById(ObjectId(job.toString()))
    }

    suspend fun getUserJobs(user: User): List<TrackJob> {
        return trackJobs.find(TrackJob::_id `in` user.jobs).toList()
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class State(val chatId: Long, var messageId: Int = -1) {

    @JsonIgnore
    abstract fun getText(): String

    @JsonIgnore
    abstract fun getKeyboard(): InlineKeyboardMarkup?

    @JsonIgnore
    private val buttonsToPersist = mutableListOf<ButtonData>()

    fun render(): MenuRender {
        buttonsToPersist.clear()
        return MenuRender(getText(), getKeyboard())
    }

    suspend fun send(render: MenuRender, bot: TrackBot): SendResponse {
        return bot.execute(SendMessage(chatId, render.text).replyMarkup(render.keyboard))
    }

    suspend fun redraw(render: MenuRender, bot: TrackBot) {
        println("$chatId, $messageId, ${render.text}")
        bot.execute(EditMessageText(chatId, messageId, render.text)
            .replyMarkup(render.keyboard))
    }

    suspend fun persist(buttonDao: ButtonDao) {
        buttonDao.saveButtons(buttonsToPersist)
    }

    fun button(text: String, info: ButtonInfo): InlineKeyboardButton {
        val data = ButtonData(this, info)
        buttonsToPersist.add(data)
        return InlineKeyboardButton(text).callbackData(data._id.toString())
    }

    abstract suspend fun handleButton(info: ButtonInfo, callbackQuery: CallbackQuery, bot: TrackBot)
}

data class MenuRender(val text: String, val keyboard: InlineKeyboardMarkup?)

open class TextButtonInfo(val text: String) : ButtonInfo()

class Menu(chatId: Long, messageId: Int) : State(chatId, messageId) {
    class NewRangeButton : TextButtonInfo("*️⃣ Нам нужно больше ренжиков")
    class RangeListButton : TextButtonInfo("\uD83D\uDCDD Ренжики мои любимые")
    class SettingsButton : TextButtonInfo("⚙️Настроечкикикики")

    override fun getText(): String = """
        Лучший ботик подписывайтесь на инсту
    """.trimIndent()

    override fun getKeyboard() =
        InlineKeyboardMarkup(
            *listOf(
                RangeListButton(),
                SettingsButton()
            ).map {
                arrayOf(button(it.text, it))
            }.toTypedArray()
        )

    override suspend fun handleButton(
        info: ButtonInfo,
        callbackQuery: CallbackQuery,
        bot: TrackBot
    ) {
        when (info) {
            is NewRangeButton -> {
                bot.execute(
                    AnswerCallbackQuery(callbackQuery.id())
                        .text("Ех даааа, ща бы жопу сосать")
                )
            }
            is RangeListButton -> {
                val state = createRangeList(bot)
                state.redraw(state.render(), bot)
                bot.execute(AnswerCallbackQuery(callbackQuery.id()))
            }
            is SettingsButton -> {
                bot.execute(
                    AnswerCallbackQuery(callbackQuery.id())
                        .text("Жопу пососи, а?")
                )
            }
        }
    }
}

class GoBackButton : ButtonInfo() {
    val text: String = "◀️ Назад"
}

suspend fun State.createRangeList(bot: TrackBot): RangeList {
    val user = bot.userDao.getUser(chatId.toInt()) ?: User(chatId.toInt(), listOf()) // FIXME LOL
    val jobs = bot.userDao.getUserJobs(user)
    return RangeList(jobs, chatId, messageId)
}

class RangeList(
    @JsonIgnore val jobs: List<TrackJob>,
    chatId: Long, messageId: Int
) : State(chatId, messageId) {

    class JobButton(val job: Id<TrackJob>) : ButtonInfo()

    override fun getText() = """
        Вот тут списочек ренжиков дададада:
        ${if (jobs.isEmpty()) "Тут пусто :((((" else ""}
    """.trimIndent()

    override fun getKeyboard() = InlineKeyboardMarkup(
        *jobs.map {
            arrayOf(button(it.range, JobButton(it._id)))
        }.toTypedArray()
    )

    override suspend fun handleButton(info: ButtonInfo, callbackQuery: CallbackQuery, bot: TrackBot) {
        when (info) {
            is JobButton -> {
                val job = bot.userDao.getJob(info.job)
                if (job == null) {
                    bot.execute(AnswerCallbackQuery(callbackQuery.id()).text("Ренжик пропал куда то"))
                } else {
                    val trackJobView = TrackJobView(job, chatId, messageId)
                    trackJobView.redraw(trackJobView.render(), bot)
                    bot.execute(AnswerCallbackQuery(callbackQuery.id()))
                }
            }
        }
    }
}

class TrackJobView(private val job: TrackJob, chatId: Long, messageId: Int) : State(chatId, messageId) {
    override fun getText() = """
        Инфа о ренжике:
        Ренж: ${job.range}
        Интервальчик: ${job.interval}
        Табличка: ${job.sheet.url}
    """.trimIndent()

    override fun getKeyboard() = InlineKeyboardMarkup(
        arrayOf(GoBackButton().let { button(it.text, it) })
    )

    override suspend fun handleButton(info: ButtonInfo, callbackQuery: CallbackQuery, bot: TrackBot) {
        when (info) {
            is GoBackButton -> {
                val state = createRangeList(bot)
                state.redraw(state.render(), bot)
                bot.execute(AnswerCallbackQuery(callbackQuery.id()))
            }
        }
    }
}

class TrackBot(kodein: Kodein) {
    private val bot: TelegramBot = TelegramBot(BOT_TOKEN)
    private val buttonDao = ButtonDao(kodein)
    val userDao = UserDao(kodein)

    suspend fun start() {
        val flow = callbackFlow {
            bot.setUpdatesListener { updates ->
                updates.forEach { update ->
                    offer(update)
                }
                UpdatesListener.CONFIRMED_UPDATES_ALL
            }

            awaitClose()
        }

        flow.collect { update ->
            coroutineScope {
                launch {
                    println(update)
                    handleUpdate(update)
                }
            }
        }
    }

    private suspend fun handleUpdate(update: Update) {
        when {
            update.message() != null -> {
                val state = Menu(update.message().chat().id(), -1)
                val render = state.render()
                val response = state.send(render, this)
                state.messageId = response.message().messageId()
                state.persist(buttonDao)
            }
            update.callbackQuery() != null -> {
                val id = update.callbackQuery().data()
                val button = buttonDao.getButton(ObjectId(id)) ?: return
                button.state.handleButton(button.button, update.callbackQuery(), this)
            }
            else -> Unit
        }
    }

    suspend fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: T): R {
        return suspendCoroutine { continuation ->
            bot.execute<T, R>(request, object : Callback<T, R> {
                override fun onFailure(request: T, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(request: T, response: R) {
                    if (response.isOk) {
                        continuation.resume(response)
                    } else {
                        val message = "${request.method} failed with error_code " +
                                "${response.errorCode()} ${response.description()}"
                        continuation.resumeWithException(TelegramException(message, response))
                    }
                }
            })
        }
    }
}


fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        println("UNHANDLED EXCEPTION YOU DUMB FUCK\n$t $e")
        e.printStackTrace()
    }


    runBlocking {
        TrackBot(kodein).start()
    }
}
