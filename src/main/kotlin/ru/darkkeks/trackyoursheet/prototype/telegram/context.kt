package ru.darkkeks.trackyoursheet.prototype.telegram

import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.ForceReply
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.Keyboard
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse
import ru.darkkeks.trackyoursheet.prototype.Controller

open class UserActionContext(val controller: Controller, val message: Message, val user: User) {
    val userId: Int
        get() = user.id()

    val bot get() = controller.bot

    suspend fun reply(text: String,
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

    suspend fun forceReply(text: String,
                           disableWebPagePreview: Boolean = false,
                           replyMarkup: Keyboard? = null,
                           parseMode: ParseMode? = ParseMode.Markdown,
                           disableNotification: Boolean = false
    ): SendResponse {
        val chat = message.chat()
        val request = SendMessage(chat.id(), text)
            .disableWebPagePreview(disableWebPagePreview)
            .parseMode(parseMode)
            .disableNotification(disableNotification)

        if (replyMarkup != null) {
            request.replyMarkup(replyMarkup)
        }

        if (!isPrivate() && message.from().id() == userId) {
            request.replyToMessageId(message.messageId())
            if (replyMarkup == null) {
                request.replyMarkup(ForceReply(true))
            }
        }

        return controller.bot.execute(request)
    }

    private fun isPrivate() = message.chat().type() == Chat.Type.Private

    fun changeState(state: GlobalUserState) {
        controller.changeState(userId, state)
    }
}

open class NewMessageContext(controller: Controller, message: Message) :
        UserActionContext(controller, message, message.from())

class CommandContext(controller: Controller, message: Message) : NewMessageContext(controller, message) {
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

class CallbackButtonContext(controller: Controller,
                            val button: CallbackButton,
                            private val callbackQuery: CallbackQuery) :
        UserActionContext(controller, callbackQuery.message(), callbackQuery.from()) {

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
                bot.execute(request)
            }
            replyMarkup != null -> {
                bot.execute(EditMessageReplyMarkup(message.chat(), message.messageId()).replyMarkup(replyMarkup))
            }
        }
    }

    suspend fun answerCallbackQuery(text: String? = null,
                                    showAlert: Boolean = false,
                                    cacheTime: Int? = null) {
        val request = AnswerCallbackQuery(callbackQuery.id())
            .showAlert(showAlert)
        if (text != null) {
            request.text(text)
        }
        if (cacheTime != null) {
            request.cacheTime(cacheTime)
        }
        bot.execute(request)

        answered = true
    }
}