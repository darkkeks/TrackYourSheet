package ru.darkkeks.trackyoursheet.prototype.states.menu

import com.pengrad.telegrambot.model.ChatMember
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatAdministrators
import com.pengrad.telegrambot.request.GetChatMember
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.PostTarget
import ru.darkkeks.trackyoursheet.prototype.Range
import ru.darkkeks.trackyoursheet.prototype.telegram.CommandContext
import ru.darkkeks.trackyoursheet.prototype.telegram.GlobalUserState
import ru.darkkeks.trackyoursheet.prototype.telegram.UserActionContext
import ru.darkkeks.trackyoursheet.prototype.telegram.handle

class ReceiveGroupState(
    @Suppress("MemberVisibilityCanBePrivate")
    val rangeId: Id<Range>,
    parentState: GlobalUserState? = null) : GlobalUserState(parentState) {

    suspend fun initiate(context: UserActionContext) {
        val escapedUsername = context.controller.me.user().username().replace("_", "\\_")
        context.reply("""
            Ты хочешь выбрать паблик/канал.
            
            В первую очередь убедись, что у тебя есть админка в чатике/канале.
            
            Чтобы выбрать, тебе надо сделать что нибудь из списка ниже:
            - Прислать мне id чата/канала, например `-1001208998514`.
            - Прислать мне username чата/канала, например `IERussia` или `@IERussia`.
            - Переслать мне любое сообщение из чата/канала (только если чат публичный).
            - В чате набрать в поле для сообщения @$escapedUsername и нажать _Выбрать чат для постинга_.
            
            /cancel, если передумал.
        """.trimIndent())
    }

    private fun done(context: UserActionContext) = toParentState(context)

    private suspend fun replacePostTarget(context: UserActionContext, target: PostTarget) {
        val range = context.controller.sheetDao.getJob(rangeId)
        if (range == null) {
            NotFoundErrorState().send(context)
            toParentState(context)
        } else {
            context.controller.stopJob(range)
            val newRange = range.copy(postTarget = target)
            context.controller.sheetDao.saveJob(newRange)
            context.controller.startJob(range)
            done(context)
        }
    }

    override suspend fun handleMessage(context: UserActionContext) = handle(context) {
        if (context.message.forwardDate() != null && context.message.forwardFromChat() == null) {
            context.reply("В пересланном сообщении нету id чата, такое может быть если чат приватный.")
            return@handle
        }

        val chatId = if (context.message.forwardFromChat() != null) {
            context.message.forwardFromChat().id()
        } else {
            val text = context.message.text()

            // "-1234" => -1234
            // "@username" or "username" => "@username"
            text.toIntOrNull() ?: "@" + text.dropWhile { it == '@' }
        }

        selectChatId(chatId, context)
    }

    private suspend fun selectChatId(chatId: Any, context: UserActionContext) {
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
                    context.reply("""Не могу найти этот чат. ${"\uD83D\uDE14"}
                    |`$chatId`""".trimMargin())
                }
                meInChat.chatMember() == null || meInChat.chatMember().status() == ChatMember.Status.left -> {
                    context.reply("Меня нету в чате. \uD83E\uDD28")
                }
                meInChat.chatMember().status() == ChatMember.Status.kicked -> {
                    context.reply("Но меня же кикнули. \uD83E\uDD14")
                }
                meInChat.chatMember().canSendMessages() == false -> {
                    context.reply("Это все конечно замечательно, но у меня нет прав отправлять сообщения в этом чате. \uD83D\uDE12")
                }
                admins.administrators() == null -> {
                    context.reply("Кажется такого не должно было случится, но я почему то не могу проверить, являешься ли ты админом. \uD83E\uDD14")
                }
                admins.administrators().none { it.user().id() == context.userId } -> {
                    context.reply("Ты не админ, геюга!")
                }
                else -> {
                    context.reply("Океюшки, выбираем чатик _${chat.chat().title()}_.")
                    replacePostTarget(context, PostTarget(chat.chat().id(), chat.chat().title(), chat.chat().type()))
                }
            }
        }
    }

    override suspend fun handleCommand(context: CommandContext) = handle(context) {
        when (context.command) {
            "cancel" -> {
                context.reply("Ок")
                toParentState(context)
            }
            "start" -> {
                if (context.arguments.isNotEmpty()) {
                    val chatId = context.arguments.first()
                    selectChatId(chatId, context)
                } else {
                    initiate(context)
                }
            }
            else -> initiate(context)
        }
    }
}