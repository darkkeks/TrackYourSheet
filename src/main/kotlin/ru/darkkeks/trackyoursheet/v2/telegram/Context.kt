package ru.darkkeks.trackyoursheet.v2.telegram

import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.Keyboard
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse
import ru.darkkeks.trackyoursheet.v2.Controller
import ru.darkkeks.trackyoursheet.v2.UserModel


data class BotUser(val telegramUser: User, val model: UserModel)

abstract class BaseContext(val user: BotUser, val message: Message, val controller: Controller) {

    val userId: Int get() = user.telegramUser.id()

    suspend fun changeGlobalState(state: GlobalState) {
        val newUser = user.copy(model = user.model.copy(state = state))
        controller.dao.saveUser(newUser.model)
        state.handle(EnterStateContext(newUser, message, controller))
    }

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

class EnterStateContext(user: BotUser, message: Message, controller: Controller) : BaseContext(user, message, controller)

open class NewMessageContext(user: BotUser, message: Message, controller: Controller) : BaseContext(user, message, controller)

class CommandContext(user: BotUser, message: Message, controller: Controller) : NewMessageContext(user, message, controller) {
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

class CallbackButtonContext<T : CallbackButton>(val button: T,
                                                val query: CallbackQuery,
                                                user: BotUser,
                                                controller: Controller)
    : BaseContext(user, query.message(), controller) {

    var answered = false

    suspend fun changeState(state: MessageState) {
        when (val render = state.draw(this)) {
            is ChangeStateRender -> changeState(render.state)
            is TextRender -> {
                editMessage(render.text, replyMarkup = render.getMarkup(state, controller.registry))
            }
        }
    }

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