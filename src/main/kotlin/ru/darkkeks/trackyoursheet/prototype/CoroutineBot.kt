package ru.darkkeks.trackyoursheet.prototype

import com.pengrad.telegrambot.Callback
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.response.BaseResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CoroutineBot {
    private val bot: TelegramBot = TelegramBot(BOT_TOKEN)

    suspend fun run(): Flow<Update> = callbackFlow {
        bot.setUpdatesListener { updates ->
            updates.forEach { update ->
                offer(update)
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }

        awaitClose()
    }

    suspend fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: T): R {
        return suspendCoroutine { continuation ->
            bot.execute<T, R>(request, object : Callback<T, R> {
                override fun onFailure(request: T, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(request: T, response: R) {
                    if (response.isOk) {
                        continuation.resume(response)
                    } else {
                        val message = "${request.method} failed with error_code " +
                                "${response.errorCode()} ${response.description()}"
                        continuation.resumeWithException(TelegramException(message, response))
                    }
                }
            })
        }
    }
}

fun User.mention(text: String) = """[${text}](tg://user?id=${id()})"""
