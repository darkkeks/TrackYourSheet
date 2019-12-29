package ru.darkkeks.trackyoursheet.prototype

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.Sheet
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.request.*
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse
import kotlinx.coroutines.GlobalScope
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

// FIXME env
val BOT_TOKEN: String = System.getenv("BOT_TOKEN") ?: throw IllegalStateException()


val kodein = Kodein {
    bind<NetHttpTransport>() with singleton { GoogleNetHttpTransport.newTrustedTransport() }
    bind<JsonFactory>() with singleton { JacksonFactory.getDefaultInstance() }
    bind<CredentialsUtil>() with singleton { CredentialsUtil(kodein) }

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

open class NewMessageContext(val controller: Controller, val message: Message) {
    val userId: Int get() = message.from().id()

    suspend fun reply(text: String,
                      disableWebPagePreview: Boolean = true,
                      replyMarkup: Keyboard? = null,
                      parseMode: ParseMode? = ParseMode.Markdown,
                      disableNotification: Boolean = false): SendResponse {
        return controller.bot.execute(SendMessage(message.chat().id(), text)
                                          .disableWebPagePreview(disableWebPagePreview)
                                          .replyMarkup(replyMarkup)
                                          .parseMode(parseMode)
                                          .disableNotification(disableNotification))
    }

    suspend fun forceReply(text: String,
        disableWebPagePreview: Boolean = false,
        replyMarkup: Keyboard? = null,
        parseMode: ParseMode? = ParseMode.Markdown,
        disableNotification: Boolean = false
    ): SendResponse {
        val chat = message.chat()
        val request = SendMessage(chat.id(), text)
            .disableWebPagePreview(disableWebPagePreview)
            .replyMarkup(replyMarkup)
            .parseMode(parseMode)
            .disableNotification(disableNotification)

        if (!isPrivate()) {
            request.replyToMessageId(message.messageId())
            if (replyMarkup == null) {
                request.replyMarkup(ForceReply(true))
            }
        }

        return controller.bot.execute(request)
    }

    fun isPrivate() = message.chat().type() == Chat.Type.Private

    fun changeState(state: GlobalUserState) {
        controller.changeState(message.chat().id() to message.from().id(), state)
    }
}

class CommandContext(controller: Controller, message: Message) :
        NewMessageContext(controller, message) {
    val command: String
    val arguments: List<String>

    init {
        require(isCommand(message.text()))

        val text = message.text().substring(1)
        val parts = text.split(" ")
        command = parts[0]
        arguments = parts.subList(1, parts.size)
    }

    companion object {
        fun isCommand(text: String?) = text != null && text.trim().let { trimmed ->
            trimmed.startsWith("/") && !trimmed[1].isWhitespace()
        }
    }
}

inline fun <T> GlobalUserState.handle(context: T, block: (T) -> Unit): Boolean {
    block(context)
    return true
}

abstract class GlobalUserState(val parentState: GlobalUserState? = null) {
    open suspend fun handleMessage(context: NewMessageContext): Boolean = false

    open suspend fun handleCommand(context: CommandContext): Boolean = false
}

class DefaultState : GlobalUserState() {
    override suspend fun handleMessage(context: NewMessageContext) = handle(context) {
        startMessage(context)
    }

    override suspend fun handleCommand(context: CommandContext): Boolean {
        when (context.command) {
            "start" -> startMessage(context)
            "help" -> startMessage(context)
            "new_range" -> {
                val state = NewRangeState()
                context.changeState(state)
                state.initiate(context)
            }
            "list_ranges" -> {
                val dao = context.controller.sheetDao
                val user = dao.getOrCreateUser(context.userId)

                val ranges = dao.getUserJobs(user._id)

                val rangeList = if (ranges.isNotEmpty()) {
                    ranges.joinToString("\n") {
                        """[${it.range}](${it.sheet.urlTo(it.range)}) с интервалом ${it.interval}"""
                    }
                } else {
                    "Тут пусто ;("
                }

                context.reply("""
                    Ваши ренжики:
                    $rangeList
                """.trimIndent())
            }
            else -> return false
        }
        return true
    }

    suspend fun startMessage(context: NewMessageContext) = context.reply("""
        Привет, я умею наблюдать за гугл-табличками. ${"\uD83E\uDD13"}
        
        Давай будем называть _ренжем_ (_ренж_, _ренжик_, в _ренже_, _ренжику_, от англ. _range_) диапазон не некотором листе в гугл таблице. 
        Я не смог придумать название лучше, если есть идеи -- всегда можно написать @darkkeks.
        
        Сюда еще хелпы надо, напишите @lodthe чтобы добавил))0)).
    """.trimIndent())
}

class NewRangeState : GlobalUserState(DefaultState()) {

    var spreadsheetId: String? = null
    var sheet: Sheet? = null
    var range: CellRange? = null

    suspend fun initiate(context: NewMessageContext) {
        context.forceReply("""
            Окей, ща создадим, сейчас мне от тебя нужна ссылка на табличку.

            Можешь сразу дать ссылку на нужный ренж (выделить в гугл табличке -> пкм -> получить ссылку на этот диапазон).
            
            Либо просто ссылку на нужную таблчику/лист.

            Выглядит она вот так:
            ```https://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-asdfghjklasdfdsaf/edit#gid=13371488228&range=A1:Z22```
        """.trimIndent())
    }

    suspend fun errorMessage(context: NewMessageContext) = context.forceReply("""
        Это не ссылка :(
        
        Чтобы отменить создание ренжика можно написать /cancel или нажать на кнопку ниже.
    """.trimIndent())

    override suspend fun handleMessage(context: NewMessageContext) = handle(context) {
        val text = context.message.text()

        if (text == null) {
            errorMessage(context)
            return@handle
        }

        val match = SheetData.fromUrl(text)
        if (match == null) {
            errorMessage(context)
            return@handle
        }

        val (id, arguments) = match
        spreadsheetId = id

        val sheets = context.controller.sheetApi.getSheets(id)

        if ("gid" in arguments) {
            sheet = sheets.find {
                it.properties.sheetId.toString() == arguments["gid"]
            }
        }

        if (sheet != null && "range" in arguments) {
            val rangeString = arguments.getValue("range")
            if (CellRange.isRange(rangeString)) {
                range = CellRange.fromString(rangeString)
            }
        }

        val finalSheet = sheet
        when {
            finalSheet == null -> {
                val keyboard = InlineKeyboardMarkup(*sheets.map {
                    arrayOf(InlineKeyboardButton(it.properties.title ?: "Без названия")
                                .callbackData("Тут коллбек))"))
                }.toTypedArray())

                context.reply("""
                    Вижу [табличку](${SheetData(id, -1).url}), теперь надо выбрать один из листов. 
                    Либо можешь ответить мне ренжем с листом, вида `Лист 1!A1:Z13`.
                """.trimIndent(), replyMarkup = keyboard)
            }
            range == null -> {
                context.forceReply("""
                    Вижу табличку и лист, осталось указать какой ренж трекать. Напиши вот в таком формате: `A2:BE26`.
                """.trimIndent())
            }
            else -> {
                val dao = context.controller.sheetDao

                val user = dao.getOrCreateUser(context.userId)

                val trackJob = TrackJob(
                    SheetData(id, finalSheet.properties.sheetId),
                    "${finalSheet.properties.title}!$range",
                    PeriodTrackInterval(10),
                    user._id
                )
                dao.saveJob(trackJob)

                context.controller.addJob(trackJob)

                context.changeState(DefaultState())

                context.reply("""
                    Готово, буду трекать ренж [$range](${sheet}) на листе `${finalSheet.properties.title}`.
                """.trimIndent())
            }
        }
    }

    override suspend fun handleCommand(context: CommandContext): Boolean {
        when (context.command) {
            "cancel" -> {
                val state = DefaultState()
                state.startMessage(context)
                context.changeState(state)
            }
            else -> return false
        }
        return true
    }
}


class Controller(kodein: Kodein) {
    val bot = CoroutineBot()
    val sheetApi: SheetApi by kodein.instance()
    val sheetDao: SheetTrackDao by kodein.instance()

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
            }
        }
    }

    fun changeState(user: StateHolder, state: GlobalUserState) {
        userStates[user] = state
    }

    private suspend fun preloadJobs() {
        sheetDao.getAllJobs().forEach { job ->
            tracker.addJob(job).consumeEach { event ->
                handleEvent(job, event)
            }
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
        GlobalScope.launch { // FIXME оно тут надо вообще?
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