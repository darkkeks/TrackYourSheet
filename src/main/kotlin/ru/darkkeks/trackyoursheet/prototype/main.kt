package ru.darkkeks.trackyoursheet.prototype

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.newId
import org.litote.kmongo.reactivestreams.KMongo
import ru.darkkeks.trackyoursheet.prototype.sheet.CellTextModifyEvent
import ru.darkkeks.trackyoursheet.prototype.sheet.CredentialsUtil
import ru.darkkeks.trackyoursheet.prototype.sheet.DataEvent
import ru.darkkeks.trackyoursheet.prototype.sheet.SheetApi
import ru.darkkeks.trackyoursheet.prototype.telegram.*

// FIXME env
val BOT_TOKEN: String = System.getenv("BOT_TOKEN") ?: ""


val kodein = Kodein {
    bind<NetHttpTransport>() with singleton { GoogleNetHttpTransport.newTrustedTransport() }
    bind<JsonFactory>() with singleton { JacksonFactory.getDefaultInstance() }
    bind<CredentialsUtil>() with singleton {
        CredentialsUtil(kodein)
    }

    bind<Sheets>() with singleton {
        val credentialsUtil: CredentialsUtil = instance()
        Sheets.Builder(instance(), instance(), credentialsUtil.getCredential())
            .setApplicationName("TrackYourSheet")
            .build()
    }

    bind<SheetApi>() with singleton {
        SheetApi(kodein)
    }

    bind<CoroutineDatabase>() with singleton {
        val credential = MongoCredential.createCredential("root", "admin", "root".toCharArray())
        val settings = MongoClientSettings.builder()
            .credential(credential).build()
        KMongo.createClient(settings).coroutine
            .getDatabase("track_your_sheet")
    }

    bind<SheetTrackDao>() with singleton {
        SheetTrackDao(kodein)
    }
}


typealias ChatId = Long
typealias UserId = Int

typealias StateHolder = Pair<ChatId, UserId>


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
open class CallbackButton(val _id: Id<CallbackButton> = newId()) {
    val stringId
        get() = _id.toString()
}

// TODO
class StatelessButton : CallbackButton()

open class GlobalStateButton : CallbackButton()


class ButtonManager {
    val buttons = mutableMapOf<String, CallbackButton>()

    fun get(id: String) = buttons[id]

    fun put(id: String, button: CallbackButton) {
        buttons[id] = button
    }
}

class Controller(kodein: Kodein) {
    val bot = CoroutineBot()
    val sheetApi: SheetApi by kodein.instance()
    val sheetDao: SheetTrackDao by kodein.instance()

    val buttonManager = ButtonManager()

    private val tracker = SheetTracker(kodein)

    private val userStates = mutableMapOf<StateHolder, GlobalUserState>()

    suspend fun start() {
        preloadJobs()

        bot.run().collect { update ->
            println(update)

            when {
                update.message() != null -> {
                    val message = update.message()
                    val stateHolder = message.chat().id() to message.from().id()
                    val state = userStates.computeIfAbsent(stateHolder) { DefaultState() }

                    if (CommandContext.isCommand(message.text())) {
                        state.handleCommand(CommandContext(this, message))
                    } else {
                        state.handleMessage(NewMessageContext(this, message))
                    }
                }
                update.callbackQuery() != null -> {
                    val callbackQuery = update.callbackQuery()
                    val message = callbackQuery.message()
                    val stateHolder = message.chat().id() to callbackQuery.from().id()

                    val state = userStates.computeIfAbsent(stateHolder) { DefaultState() }

                    when (val button = buttonManager.get(callbackQuery.data())) {
                        is GlobalStateButton -> {
                            val context = CallbackButtonContext(this, callbackQuery, button)
                            state.handleCallback(context)
                            if (!context.answered) {
                                context.answerCallbackQuery()
                            }
                        }
                        is StatelessButton ->
                            println("Warning! Unsupported stateless button received!")
                        else ->
                            println("Warning! Unknown callback query received ${callbackQuery.data()}")
                    }
                }
            }
        }
    }

    fun changeState(user: StateHolder, state: GlobalUserState) {
        userStates[user] = state
    }

    private suspend fun preloadJobs() {
        sheetDao.getAllJobs().forEach { job ->
            addJob(job)
        }
    }

    private suspend fun handleEvent(job: TrackJob, event: DataEvent) {
        println("Job #${job._id} received event $event")

        when (event) {
            is CellTextModifyEvent -> {
                val modifyMessage: (CellTextModifyEvent) -> String = { event ->
                    """
                        В [табличке](${job.sheet.url}) значение изменилось в [клетке ${event.cell}](${job.sheet.urlTo(
                        event.cell
                    )}):
                        Старое:
                        ```
                        ${event.oldText}```
                        Новое:
                        ```
                        ${event.newText}```
                    """.trimIndent()
                }

                val owner = sheetDao.getUserById(job.owner)!!
                bot.execute(SendMessage(
                    owner.userId,
                    modifyMessage(event)
                ).parseMode(ParseMode.Markdown).disableWebPagePreview(true))
            }
        }
    }

    fun addJob(trackJob: TrackJob) {
        GlobalScope.launch {
            // FIXME оно тут надо вообще?
            tracker.addJob(trackJob).consumeEach { event ->
                handleEvent(trackJob, event)
            }
        }
    }
}

suspend fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        println("UNHANDLED EXCEPTION\n$t $e")
        e.printStackTrace()
    }

    val controller = Controller(kodein)
    controller.start()
}