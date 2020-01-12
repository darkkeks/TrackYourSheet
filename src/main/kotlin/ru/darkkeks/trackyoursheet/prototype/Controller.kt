package ru.darkkeks.trackyoursheet.prototype

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
import org.kodein.di.generic.instance
import ru.darkkeks.trackyoursheet.prototype.sheet.*
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class Controller(kodein: Kodein) {
    val bot = CoroutineBot()
    val sheetApi: SheetApi by kodein.instance()
    val sheetDao: SheetTrackDao by kodein.instance()

    private val userStates = mutableMapOf<Int, BotUser>()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val tracker = SheetTracker(kodein)

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
                                    val url = "tg://resolve?domain=${me.user().username()}&start=${message.chat().id()}"
                                    add(InlineKeyboardButton("\u2699️Настроить").url(url))
                                })
                            }
                        } else {
                            val userId = message.from().id()
                            val user = sheetDao.getOrCreateUser(message.from().id())
                            userStates[userId] = user

                            val state = user.globalState

                            val context = if (CommandContext.isCommand(message.text())) {
                                CommandContext(this@Controller, message)
                            } else {
                                NewMessageContext(this@Controller, message)
                            }

                            when (context) {
                                is CommandContext -> {
                                    if (!state.handleCommand(context)) {
                                        context.reply("Неизвестная команда \uD83D\uDE15")
                                    }
                                }
                                else -> {
                                    if (!state.handleMessage(context)) {
                                        context.reply("Не получилось обработать сообщение \u2639")
                                    }
                                }
                            }

                            saveUser(userId)
                        }
                    }
                    update.callbackQuery() != null -> {
                        val callbackQuery = update.callbackQuery()

                        val userId = callbackQuery.from().id()
                        val button = sheetDao.getButton(callbackQuery.data())
                        val user = sheetDao.getOrCreateUser(userId)
                        userStates[userId] = user

                        if (button == null) {
                            logger.warn("Can't find callback button ${callbackQuery.data()}")
                            bot.execute(AnswerCallbackQuery(callbackQuery.id()).text("Кнопка не найдена"))
                        } else {
                            val context = CallbackButtonContext(this@Controller, button, callbackQuery)

                            when (button) {
                                is GlobalStateButton -> {
                                    val state = user.globalState
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

                            saveUser(userId)
                        }
                    }
                }
            }
        }
    }

    fun getUser(userId: Int): BotUser? {
        return userStates[userId]
    }

    suspend fun changeState(context: UserActionContext, state: GlobalUserState) {
        val user = userStates[context.userId] ?: return
        userStates[context.userId] = user.copy(globalState = state)
        state.onEnter(context)
    }

    private suspend fun saveUser(userId: Int) {
        sheetDao.saveUser(userStates[userId] ?: return)
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