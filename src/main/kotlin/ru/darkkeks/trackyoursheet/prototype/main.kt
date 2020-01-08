package ru.darkkeks.trackyoursheet.prototype

import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.GetMe
import com.pengrad.telegrambot.response.GetMeResponse
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
import org.slf4j.LoggerFactory
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

    lateinit var me: GetMeResponse

    suspend fun start() {
        preloadJobs()

        me = bot.execute(GetMe())

        bot.run().collect { update ->
            println(update)

            scope.launch {
                when {
                    update.message() != null -> {
                        val message = update.message()

                        if (message.chat().type() != Chat.Type.Private) {
                            if (message.newChatMembers() == null ||
                                message.newChatMembers().any { it.id() == me.user().id() }) {

                                NewMessageContext(this@Controller, message).reply("""
                                    В группу я могу только срать обновлениями. 
                                    
                                    Администраторы группы могут добавить ренжей с информированием сюда в личке. ${"\uD83D\uDE0F"}
                                """.trimIndent(), replyMarkup = buildInlineKeyboard {
                                    add(InlineKeyboardButton("\u2699️Настроить")
                                            .url("tg://resolve?domain=${me.user().username()}&start=${message.chat().id()}"))
                                })
                            }
                        } else {
                            val context = if (CommandContext.isCommand(message.text())) {
                                CommandContext(this@Controller, message)
                            } else {
                                NewMessageContext(this@Controller, message)
                            }

                            val state = userStates.computeIfAbsent(context.stateHolder) { DefaultState() }
                            when (context) {
                                is CommandContext -> {
                                    if (context.command == "start" && context.arguments.isNotEmpty()) {
                                        context.reply("Кажется ты хочешь настроить чатик ${context.arguments.first()}, но фича еще не готова))))")
                                    } else {
                                        state.handleCommand(context)
                                    }
                                }
                                else -> state.handleMessage(context)
                            }
                        }
                    }
                    update.callbackQuery() != null -> {
                        val callbackQuery = update.callbackQuery()

                        val button = sheetDao.getButton(callbackQuery.data())

                        if (button == null) {
                            logger.warn("Can't find callback button ${callbackQuery.data()}")
                            bot.execute(AnswerCallbackQuery(callbackQuery.id()).text("Кнопка не найдена"))
                        } else {
                            val context = CallbackButtonContext(this@Controller, callbackQuery, button)

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
    }

    fun getUserState(user: StateHolder) = userStates[user]

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

    fun startJob(range: Range) {
        scope.launch {
            tracker.addJob(range).consumeEach { event ->
                handleEvent(range, event)
            }
        }
    }

    fun stopJob(range: Range) {
        tracker.removeJob(range)
    }

    private suspend fun handleEvent(job: Range, event: DataEvent) {
        logger.info("Job #${job._id} received event $event")

        val targetChatId = job.postTarget.chatId

        if (event is CellEvent) {
            val cellString = "[клетке ${event.cell + 1}](${job.sheet.urlTo(event.cell + 1)})"
            when (event) {
                is AddTextEvent -> bot.sendMessage(targetChatId, """
                    Добавлено значение в $cellString: ```
                    ${event.text}```
                """.trimIndent())
                is ModifyTextEvent -> bot.sendMessage(targetChatId, """
                    Изменено значение в $cellString:
                    Старое:```
                    ${event.oldText}```
                    Новое:```
                    ${event.newText}```
                """.trimIndent())
                is RemoveTextEvent -> bot.sendMessage(targetChatId, """
                    Удалено значение в $cellString:```
                    ${event.text}```
                """.trimIndent())
                is AddNoteEvent -> bot.sendMessage(targetChatId, """
                    Добавлена заметка в $cellString: ```
                    ${event.note}```
                """.trimIndent())
                is ModifyNoteEvent -> bot.sendMessage(targetChatId, """
                    Изменена заметка в $cellString:
                    Старая:```
                    ${event.oldNote}```
                    Новая:```
                    ${event.newNote}```
                """.trimIndent())
                is RemoveNoteEvent -> bot.sendMessage(targetChatId, """
                    Удалена заметка в $cellString: ```
                    ${event.note}```
                """.trimIndent())
            }
        }
    }

    companion object {
        val logger = createLogger<Controller>()
    }
}

suspend fun main() {
    val logger = LoggerFactory.getLogger("Main")
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        logger.error("Unhandled exception on thread $t", e)
    }

    val module = SimpleModule()
        .addKeySerializer(Cell::class.java, CellKeySerializer())
        .addKeyDeserializer(Cell::class.java, CellKeyDeserializer())
    KMongoConfiguration.registerBsonModule(module)

    val controller = Controller(kodein)
    controller.start()
}