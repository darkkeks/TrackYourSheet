package ru.darkkeks.trackyoursheet.v2.telegram

import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.Keyboard
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.trackyoursheet.v2.MainMenuState.*
import ru.darkkeks.trackyoursheet.v2.SheetTrackDao
import ru.darkkeks.trackyoursheet.v2.createLogger
import kotlin.reflect.KClass
import kotlin.reflect.full.cast


abstract class BaseContext(val user: User, val message: Message, val controller: Controller) {

    val userId get() = user.id()

    suspend fun send(text: String,
                     disableWebPagePreview: Boolean = true,
                     replyMarkup: Keyboard? = null,
                     parseMode: ParseMode? = ParseMode.Markdown,
                     disableNotification: Boolean = false): SendResponse {
        val request = SendMessage(message.chat().id(), text)
            .disableWebPagePreview(disableWebPagePreview)
            .parseMode(parseMode)
            .disableNotification(disableNotification)
        if (replyMarkup != null) {
            request.replyMarkup(replyMarkup)
        }
        return controller.bot.execute(request)
    }

}

open class NewMessageContext(message: Message, controller: Controller) : BaseContext(message.from(), message, controller)

class CommandContext(message: Message, controller: Controller) : NewMessageContext(message, controller) {
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

class CallbackButtonContext<T : CallbackButton>(val button: T, val query: CallbackQuery, controller: Controller)
    : BaseContext(query.from(), query.message(), controller) {

    var answered = false

    suspend fun editMessage(text: String? = null,
                            replyMarkup: InlineKeyboardMarkup? = null,
                            parseMode: ParseMode = ParseMode.Markdown) {
        when {
            text != null -> {
                val request = EditMessageText(message.chat().id(), message.messageId(), text)
                    .parseMode(parseMode)
                if (replyMarkup != null) {
                    request.replyMarkup(replyMarkup)
                }
                controller.bot.execute(request)
            }
            replyMarkup != null -> {
                controller.bot.execute(EditMessageReplyMarkup(message.chat(), message.messageId()).replyMarkup(replyMarkup))
            }
        }
    }

    suspend fun answerCallbackQuery(text: String? = null,
                                    showAlert: Boolean = false,
                                    cacheTime: Int? = null) {
        val request = AnswerCallbackQuery(query.id())
            .showAlert(showAlert)
        if (text != null) {
            request.text(text)
        }
        if (cacheTime != null) {
            request.cacheTime(cacheTime)
        }
        controller.bot.execute(request)

        answered = true
    }
}


abstract class State {
    abstract val handlerList: HandlerList

    suspend fun <T : BaseContext> handle(context: T) = handlerList.handle(context)
}

abstract class GlobalState : State()

abstract class MessageState : State() {
    abstract suspend fun draw(context: BaseContext): MessageRender

    suspend fun send(context: BaseContext) {
        when (val render = draw(context)) {
            is ChangeStateRender -> render.state.send(context)
            is TextRender -> {
                val markup = render.keyboard.map { row ->
                    row.map { button ->
                        button.toInlineButton()
                    }.toTypedArray()
                }.toTypedArray()
                context.send(render.text, replyMarkup = InlineKeyboardMarkup(*markup))
            }
        }
    }
}


abstract class MessageRender

class TextRender(val text: String, val keyboard: List<List<CallbackButton>>) : MessageRender()

class ChangeStateRender(val state: MessageState) : MessageRender()



abstract class CallbackButton {
    private val id get() = ButtonRegistry.getByClass(this::class)?.id
        ?: throw IllegalStateException("Button with class ${this::class} is not registered")

    abstract fun getText(): String

    open fun toInlineButton() = InlineKeyboardButton(getText()).also {
        val buffer = ButtonBuffer()
        serialize(buffer)
        it.callbackData(buffer.toString())
    }

    open fun serialize(buffer: ButtonBuffer) = buffer.pushByte(id)
}

abstract class TextButton(private val buttonText: String) : CallbackButton() {
    override fun getText() = buttonText
}

class ButtonRegistryEntry<T : CallbackButton>(val id: Byte,
                                              val klass: KClass<T>,
                                              val buttonFactory: (ButtonBuffer) -> T) {

    fun createContext(buffer: ButtonBuffer, query: CallbackQuery, controller: Controller): CallbackButtonContext<T> {
        return CallbackButtonContext(buttonFactory(buffer), query, controller)
    }
}

enum class ButtonRegistry(val button: ButtonRegistryEntry<*>) {
    CREATE_NEW_RANGE(ButtonRegistryEntry(0x01, CreateNewRangeButton::class) { CreateNewRangeButton() }),
    LIST_RANGES(ButtonRegistryEntry(0x02, ListRangesButton::class) { ListRangesButton() }),
    SETTINGS(ButtonRegistryEntry(0x03, SettingsButton::class) { SettingsButton() }),
    ;

    companion object {
        fun <T : CallbackButton> getByClass(klass: KClass<T>): ButtonRegistryEntry<*>? {
            return values().find { it.button.klass == klass }?.button
        }

        fun getButton(buffer: ButtonBuffer) = values().find {
            it.button.id == buffer.peekByte()
        }?.button?.apply {
            buffer.popByte()
        }
    }
}


class Controller(kodein: Kodein) {

    val bot = CoroutineBot()

    val dao: SheetTrackDao by kodein.instance()

    val scope = CoroutineScope(SupervisorJob())

    suspend fun start() {
        logger.info("Starting bot")
        bot.run().collect { update ->
            println(update)

            scope.launch {
                when {
                    update.message() != null -> {
                        val message = update.message()
                        val userId = message.from().id()

                        val user = dao.getOrCreateUser(userId)

                        val context = when {
                            message.text() != null && CommandContext.isCommand(message.text()) ->
                                CommandContext(message, this@Controller)
                            else ->
                                NewMessageContext(message, this@Controller)
                        }

                        val result = user.state.handle(context)

                        if (result !is HandlerResultSuccess) {
                            context.send("Failed to handle message :(\nContact @darkkeks if problem persists")
                        }
                    }
                    update.callbackQuery() != null -> {
                        val query = update.callbackQuery()
                        val buffer = ButtonBuffer(query.data())

                        val userId = query.from().id()
                        val user = dao.getOrCreateUser(userId)

                        val buttonEntry = ButtonRegistry.getButton(buffer)

                        if (buttonEntry != null) {
                            val context = buttonEntry.createContext(buffer, query, this@Controller)
                            val result = user.state.handle(context)

                            if (result !is HandlerResultSuccess) {
                                context.answerCallbackQuery("Failed to handle button pressed :(")
                            }
                        } else {
                            bot.execute(AnswerCallbackQuery(query.id()).text("Unknown button pressed :("))
                        }
                    }
                }
            }
        }
    }

    suspend fun saveUser() {

    }

    companion object {
        val logger = createLogger<Controller>()
    }
}