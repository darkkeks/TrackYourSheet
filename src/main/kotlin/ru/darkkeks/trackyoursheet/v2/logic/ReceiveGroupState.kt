package ru.darkkeks.trackyoursheet.v2.logic

import com.pengrad.telegrambot.model.ChatMember
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatAdministrators
import com.pengrad.telegrambot.request.GetChatMember
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.v2.*
import ru.darkkeks.trackyoursheet.v2.telegram.*

class ReceiveGroupState(val rangeId: Id<Range>) : GlobalState() {
    private val helpMessage = """
        Ты хочешь выбрать паблик/канал.

        В первую очередь убедись, что у тебя есть админка в чатике/канале.

        Чтобы выбрать, тебе надо сделать что нибудь из списка ниже:
        - Прислать мне id чата/канала, например `-1001208998514`.
        - Прислать мне username чата/канала, например `IERussia` или `@IERussia`.
        - Переслать мне любое сообщение из чата/канала (только если чат публичный).

        /cancel, если передумал.
    """.trimIndent()

    override val handlerList = buildHandlerList {
        text {
            if (message.forwardDate() != null && message.forwardFromChat() == null) {
                send("В пересланном сообщении нету id чата, такое может быть если чат приватный.")
                return@text
            }

            val chatId: Any = if (message.forwardFromChat() != null) {
                message.forwardFromChat().id()
            } else {
                val text: String = message.text()

                // "-1234" => -1234
                // "@username" or "username" => "@username"
                text.toLongOrNull() ?: "@" + text.dropWhile { it == '@' }
            }

            selectChatId(chatId, this)
        }

        command("cancel") {
            send("Ok")
            done(this)
        }

        command("start") {
            if (arguments.isNotEmpty()) {
                val chatId = arguments.first()
                selectChatId(chatId, this)
            } else {
                send(helpMessage)
            }
        }

        fallback {  // onEnter too
            send(helpMessage)
        }
    }

    private suspend fun selectChatId(chatId: Any, context: BaseContext) {
        val me = context.controller.me.user()

        coroutineScope {
            val chatDeferred = async {
                context.controller.bot.executeUnsafe(GetChat(chatId))
            }
            val meInChatDeferred = async {
                context.controller.bot.executeUnsafe(GetChatMember(chatId, me.id()))
            }
            val adminsDeferred = async {
                context.controller.bot.executeUnsafe(GetChatAdministrators(chatId))
            }

            val chat = chatDeferred.await()
            val meInChat = meInChatDeferred.await()
            val admins = adminsDeferred.await()

            when {
                chat.chat() == null -> {
                    context.send("Не могу найти этот чат. ${"\uD83D\uDE14"}\n`$chatId`")
                }

                meInChat.chatMember() == null || meInChat.chatMember().status() == ChatMember.Status.left -> {
                    context.send("Меня нету в чате. \uD83E\uDD28")
                }

                meInChat.chatMember().status() == ChatMember.Status.kicked -> {
                    context.send("Но меня же кикнули. \uD83E\uDD14")
                }

                meInChat.chatMember().canSendMessages() == false -> {
                    context.send("Это все конечно замечательно, но у меня нет прав отправлять сообщения в этом чате. \uD83D\uDE12")
                }

                admins.administrators() == null -> {
                    context.send("Кажется такого не должно было случится, но я почему то не могу проверить, являешься ли ты админом. \uD83E\uDD14")
                }

                admins.administrators().none { it.user().id() == context.userId } -> {
                    context.send("Ты не админ \uD83D\uDE21")
                }

                else -> {
                    context.send("Океюшки, выбираем чатик _${chat.chat().title()}_.")
                    replacePostTarget(PostTarget(chat.chat().id(), chat.chat().title(), chat.chat().type()), context)
                    done(context)
                }
            }
        }
    }

    private suspend fun replacePostTarget(target: PostTarget, context: BaseContext) {
        val range = context.controller.repository.getRange(rangeId)
        if (range == null) {
            NotFoundErrorState().send(context)
        } else {
            context.controller.stopRange(range)
            val newRange = range.copy(postTarget = target)
            context.controller.repository.saveRange(newRange)
            context.controller.startRange(range)
        }
    }

    private suspend fun done(context: BaseContext) {
        context.changeGlobalState(DefaultState())
    }
}