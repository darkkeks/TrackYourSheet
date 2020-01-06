package ru.darkkeks.trackyoursheet.prototype.telegram

import com.pengrad.telegrambot.Callback
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.Keyboard
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.DeleteWebhook
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.BaseResponse
import com.pengrad.telegrambot.response.SendResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.darkkeks.trackyoursheet.prototype.BOT_TOKEN
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CoroutineBot {
    private val bot: TelegramBot = TelegramBot(BOT_TOKEN)

    suspend fun run(): Flow<Update> {
        execute(DeleteWebhook())
        return callbackFlow {
            bot.setUpdatesListener { updates ->
                updates.forEach { update ->
                    offer(update)
                }
                UpdatesListener.CONFIRMED_UPDATES_ALL
            }

            awaitClose()
        }
    }

    suspend fun sendMessage(chatId: Long, text: String,
                      disableWebPagePreview: Boolean = true,
                      replyMarkup: Keyboard? = null,
                      parseMode: ParseMode? = ParseMode.Markdown,
                      disableNotification: Boolean = false): SendResponse {
        val request = SendMessage(chatId, text)
            .disableWebPagePreview(disableWebPagePreview)
            .parseMode(parseMode)
            .disableNotification(disableNotification)
        if (replyMarkup != null) {
            request.replyMarkup(replyMarkup)
        }
        return execute(request)
    }

    suspend fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: T): R {
        val stackTrace = IOException().apply {
            stackTrace = stackTrace.copyOfRange(1, stackTrace.size)
        }

        return suspendCoroutine { continuation ->
            bot.execute(request, object : Callback<T, R> {
                override fun onFailure(request: T, e: IOException?) {
                    stackTrace.initCause(e)
                    continuation.resumeWithException(stackTrace)
                }

                override fun onResponse(request: T, response: R) {
                    if (response.isOk) {
                        continuation.resume(response)
                    } else {
                        val message = "${request.method} failed with error_code " +
                                "${response.errorCode()} ${response.description()}"
                        stackTrace.initCause(TelegramException(message, response))
                        continuation.resumeWithException(stackTrace)
                    }
                }
            })
        }
    }
}
