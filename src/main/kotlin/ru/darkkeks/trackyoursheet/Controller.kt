package ru.darkkeks.trackyoursheet

import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.GetMe
import com.pengrad.telegrambot.response.GetMeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.trackyoursheet.sheet.*
import ru.darkkeks.trackyoursheet.telegram.*


class Controller(kodein: Kodein) {

    val bot = CoroutineBot()
    val sheetApi: SheetApi by kodein.instance()
    val repository: SheetTrackRepository by kodein.instance()
    val registry = Registry()

    private val tracker = SheetTracker(kodein)
    private val scope = CoroutineScope(SupervisorJob())

    lateinit var me: GetMeResponse

    suspend fun start() {
        logger.info("Starting bot")
        me = bot.execute(GetMe())

        preloadJobs()

        bot.run().collect { update ->
            logger.info("{}", update)

            scope.launch {
                when {
                    update.message() != null -> {
                        val message = update.message()
                        val userId = message.from().id()

                        val user = repository.getOrCreateUser(userId)
                        val botUser = BotUser(message.from(), user)

                        val context = when {
                            message.text() != null && CommandContext.isCommand(message.text()) ->
                                CommandContext(botUser, message, this@Controller)
                            else ->
                                NewMessageContext(botUser, message, this@Controller)
                        }

                        val result = user.state.handle(context)

                        if (result !is HandlerResultSuccess) {
                            context.send("Failed to handle message :(")
                        }

                        context.saveUser()
                    }
                    update.callbackQuery() != null -> {
                        val query = update.callbackQuery()
                        val buffer = ButtonBuffer(query.data())

                        val userId = query.from().id()
                        val user = repository.getOrCreateUser(userId)
                        val botUser = BotUser(query.from(), user)

                        val state = registry.states.read(buffer)
                        val button = registry.buttons.read(buffer)

                        if (button == null) {
                            bot.execute(AnswerCallbackQuery(query.id()).text("Unknown button :("))
                            return@launch
                        }

                        val actualState = if (state == null || state is NullState) {
                            user.state
                        } else {
                            state
                        }

                        val context = CallbackButtonContext(button, query, botUser, this@Controller)

                        val result = actualState.handle(context)
                        if (result is HandlerResultSuccess) {
                            if (!context.answered) {
                                context.answerCallbackQuery()
                            }
                        } else {
                            context.answerCallbackQuery("Failed to handle button pressed :(")
                        }

                        context.saveUser()
                    }
                }
            }
        }
    }

    private suspend fun preloadJobs() {
        repository.getAllRanges().forEach { range ->
            if (range.enabled) {
                startRange(range)
            }
        }
    }

    fun startRange(range: Range) {
        scope.launch {
            tracker.addJob(range).consumeEach { event ->
                handleEvent(range, event)
            }
        }
    }

    fun stopRange(range: Range) {
        tracker.removeJob(range)
    }

    private suspend fun handleEvent(job: Range, event: DataEvent) {
        logger.info("Job #${job._id} received event $event")

        val targetChatId = job.postTarget.chatId

        if (event is SheetEvent) {
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

            if (event is DimensionsChangeEvent) {
                val from = "${event.old.first}x${event.old.second}"
                val to = "${event.new.first}x${event.new.second}"
                bot.sendMessage(targetChatId, """
                    Изменились размеры диапазона, лучше посмотреть в [табличку](${job.sheet.url}) глазами что произошло 👀
                    `$from` → `$to`
                """.trimIndent())
            }
        }

    }

    companion object {
        val logger = createLogger<Controller>()
    }
}