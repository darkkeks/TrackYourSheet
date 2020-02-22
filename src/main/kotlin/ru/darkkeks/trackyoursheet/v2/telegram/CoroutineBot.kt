package ru.darkkeks.trackyoursheet.v2.telegram

import com.pengrad.telegrambot.Callback
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.DeleteWebhook
import com.pengrad.telegrambot.response.BaseResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.darkkeks.trackyoursheet.v2.BOT_TOKEN
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

    /**
     * Does not perform error checks, allows for a better code depending on certain errors
     */
    suspend fun <T : BaseRequest<T, R>, R : BaseResponse> executeUnsafe(request: T): R {
        val stackTrace = getStackTrace()

        return suspendCoroutine { continuation ->
            bot.execute(request, object : Callback<T, R> {
                override fun onFailure(request: T, e: IOException?) {
                    stackTrace.initCause(e)
                    continuation.resumeWithException(stackTrace)
                }

                override fun onResponse(request: T, response: R) {
                    continuation.resume(response)
                }
            })
        }
    }

    suspend fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: T): R {
        val stackTrace = getStackTrace()
        val result = executeUnsafe(request)

        if (!result.isOk) {
            val message = "${request.method} failed with error_code ${result.errorCode()}: ${result.description()}"
            throw stackTrace.initCause(TelegramException(message, result))
        }

        return result
    }

    private fun getStackTrace() = IOException().apply {
        stackTrace = stackTrace.copyOfRange(2, stackTrace.size)
    }
}
