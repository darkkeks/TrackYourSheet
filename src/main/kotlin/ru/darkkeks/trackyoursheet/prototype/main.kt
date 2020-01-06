package ru.darkkeks.trackyoursheet.prototype

import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.util.KMongoConfiguration
import ru.darkkeks.trackyoursheet.prototype.sheet.*
import ru.darkkeks.trackyoursheet.prototype.states.DefaultState
import ru.darkkeks.trackyoursheet.prototype.telegram.*

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


class Controller(kodein: Kodein) {
    val bot = CoroutineBot()
    val sheetApi: SheetApi by kodein.instance()
    val sheetDao: SheetTrackDao by kodein.instance()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val tracker = SheetTracker(kodein)

    private val userStates = mutableMapOf<StateHolder, GlobalUserState>()

    suspend fun start() {
        preloadJobs()

        bot.run().collect { update ->
            when {
                update.message() != null -> {
                    val message = update.message()
                    val context = if (CommandContext.isCommand(message.text())) {
                        CommandContext(this, message)
                    } else {
                        NewMessageContext(this, message)
                    }

                    val state = userStates.computeIfAbsent(context.stateHolder) { DefaultState() }
                    when (context) {
                        is CommandContext -> state.handleCommand(context)
                        else -> state.handleMessage(context)
                    }
                }
                update.callbackQuery() != null -> {
                    val callbackQuery = update.callbackQuery()

                    val button = sheetDao.getButton(callbackQuery.data())

                    if (button == null) {
                        println("Warning! Can't find callback button ${callbackQuery.data()}")
                        bot.execute(AnswerCallbackQuery(callbackQuery.id()).text("Кнопка не найдена"))
                    } else {
                        val context = CallbackButtonContext(this, callbackQuery, button)

                        when (button) {
                            is GlobalStateButton -> {
                                val state = userStates.computeIfAbsent(context.stateHolder) { DefaultState() }
                                state.handleCallback(context)
                            }
                            is StatefulButton -> {
                                button.state.handleButton(context)
                            }
                            else -> {
                                context.answerCallbackQuery("Не получилось обработать кнопку :(")
                            }
                        }

                        if (!context.answered) {
                            context.answerCallbackQuery()
                        }
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
            if (job.enabled) {
                startJob(job)
            }
        }
    }

    fun startJob(trackJob: TrackJob) {
        scope.launch {
            tracker.addJob(trackJob).consumeEach { event ->
                handleEvent(trackJob, event)
            }
        }
    }

    fun stopJob(trackJob: TrackJob) {
        tracker.removeJob(trackJob)
    }

    private suspend fun handleEvent(job: TrackJob, event: DataEvent) {
        println("Job #${job._id} received event $event")
        val owner = sheetDao.getUserById(job.owner)
            ?: throw IllegalStateException("Event for non-existent user ${job.owner}")

        if (event is CellEvent) {
            val cellString = "[клетке ${event.cell + 1}](${job.sheet.urlTo(event.cell)})"
            when (event) {
                is AddTextEvent -> bot.sendMessage(owner.userId.toLong(), """
                    Добавлено значение в $cellString: ```
                    ${event.text}```
                """.trimIndent())
                is ModifyTextEvent -> bot.sendMessage(owner.userId.toLong(), """
                    Изменено значение в $cellString:
                    Старое:```
                    ${event.oldText}```
                    Новое:```
                    ${event.newText}```
                """.trimIndent())
                is RemoveTextEvent -> bot.sendMessage(owner.userId.toLong(), """
                    Удалено значение в $cellString:```
                    ${event.text}```
                """.trimIndent())
                is AddNoteEvent -> bot.sendMessage(owner.userId.toLong(), """
                    Добавлена заметка в $cellString: ```
                    ${event.note}```
                """.trimIndent())
                is ModifyNoteEvent -> bot.sendMessage(owner.userId.toLong(), """
                    Изменена заметка в $cellString:
                    Старая:```
                    ${event.oldNote}```
                    Новая:```
                    ${event.newNote}```
                """.trimIndent())
                is RemoveNoteEvent -> bot.sendMessage(owner.userId.toLong(), """
                    Удалена заметка в $cellString: ```
                    ${event.note}```
                """.trimIndent())
            }
        }
    }
}

suspend fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        println("UNHANDLED EXCEPTION\n$t $e")
        e.printStackTrace()
    }

    val module = SimpleModule()
        .addKeySerializer(Cell::class.java, CellKeySerializer())
        .addKeyDeserializer(Cell::class.java, CellKeyDeserializer())
    KMongoConfiguration.registerBsonModule(module)

    val controller = Controller(kodein)
    controller.start()
}