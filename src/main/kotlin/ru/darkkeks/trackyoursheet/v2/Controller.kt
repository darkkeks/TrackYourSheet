package ru.darkkeks.trackyoursheet.v2

import com.pengrad.telegrambot.request.AnswerCallbackQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.trackyoursheet.v2.telegram.*


class Controller(kodein: Kodein) {

    val bot = CoroutineBot()

    val dao: SheetTrackDao by kodein.instance()

    val registry = Registry()

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
                    }
                    update.callbackQuery() != null -> {
                        val query = update.callbackQuery()
                        val buffer = ButtonBuffer(query.data())

                        val userId = query.from().id()
                        val user = dao.getOrCreateUser(userId)
                        val botUser = BotUser(query.from(), user)

                        val state = registry.states.read(buffer)
                        val button = registry.buttons.read(buffer)

                        if (button == null) {
                            bot.execute(AnswerCallbackQuery(query.id()).text("Unknown button :("))
                            return@launch
                        }

                        val context = CallbackButtonContext(button, query, botUser, this@Controller)

                        val result = (state ?: user.state).handle(context)
                        if (result is HandlerResultSuccess) {
                            if (!context.answered) {
                                context.answerCallbackQuery()
                            }
                        } else {
                            context.answerCallbackQuery("Failed to handle button pressed :(")
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