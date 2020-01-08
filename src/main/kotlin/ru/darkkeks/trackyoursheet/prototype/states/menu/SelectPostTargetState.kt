package ru.darkkeks.trackyoursheet.prototype.states.menu

import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.ChatMember
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatAdministrators
import com.pengrad.telegrambot.request.GetChatMember
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.PostTarget
import ru.darkkeks.trackyoursheet.prototype.Range
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class SelectPostTargetState(val rangeId: Id<Range>) : MessageState() {
    override suspend fun draw(context: UserActionContext): MessageRender {
        val range = context.controller.sheetDao.getJob(rangeId)
        if (range == null) {
            return ChangeStateRender(NotFoundErrorState())
        } else {
            val target = range.postTarget
            val name = when (target.type) {
                Chat.Type.Private -> "сюда"
                Chat.Type.channel -> "в канал ${target.name}"
                Chat.Type.group -> "в группу ${target.name}"
                Chat.Type.supergroup -> "в группу ${target.name}"
            }
            return TextRender("""
                Сейчас сообщения отправляются $name.
            """.trimIndent(), buildInlineKeyboard {
                row(goBackButton())
                if (target.type != Chat.Type.Private) {
                    row(postHere())
                }
                row(postToGroup())
            })
        }
    }

    private fun postHere(): InlineKeyboardButton {
        return button("Постить сюда", PostTargetButton(Chat.Type.Private, this))
    }

    private fun postToGroup(): InlineKeyboardButton {
        val button = PostTargetButton(Chat.Type.group, this)
        return button("Постить в группу/канал", button)
    }

    class PostTargetButton(val type: Chat.Type, state: MessageState) : StatefulButton(state)

    override suspend fun handleButton(context: CallbackButtonContext) {
        val range = context.controller.sheetDao.getJob(rangeId)
        if (range == null) {
            changeState(NotFoundErrorState(), context)
        } else if (context.button is PostTargetButton) {
            if (context.button.type == Chat.Type.Private) {
                val newTarget = PostTarget.private(context.userId)
                val newRange = range.copy(postTarget = newTarget)
                context.controller.sheetDao.saveJob(newRange)
                changeState(RangeMenuState(rangeId), context)
            } else {
                lateinit var state: ReceiveGroupState
                hijackGlobalState(context) { parent ->
                    ReceiveGroupState(rangeId, parent).also { state = it }
                }
                state.initiate(context)
            }
        } else if (context.button is GoBackButton) {
            changeState(RangeMenuState(rangeId), context)
        }
    }
}

class ReceiveGroupState(private val rangeId: Id<Range>, parentState: GlobalUserState) : GlobalUserState(parentState) {

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
        """.trimIndent())
    }

    private fun done(context: UserActionContext) = toParentState(context)

    private suspend fun replacePostTarget(context: UserActionContext, target: PostTarget) {
        val range = context.controller.sheetDao.getJob(rangeId)
        if (range == null) {
            NotFoundErrorState().send(context)
            toParentState(context)
        } else {
            val newRange = range.copy(postTarget = target)
            context.controller.sheetDao.saveJob(newRange)
            done(context)
        }
    }

    override suspend fun handleMessage(context: UserActionContext) = handle(context) {
        val chatId = if (context.message.forwardFromChat() != null) {
            context.message.forwardFromChat()
        } else {
            val text = context.message.text()

            // "-1234" => -1234
            // "@username" or "username" => "@username"
            text.toIntOrNull() ?: "@" + text.dropWhile { it == '@' }
        }

        // TODO Тонны ошибок, про которые мне лень пока что думать ;DDDDDDddd
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
        if (context.command == "cancel") {
            context.reply("Ок")
            toParentState(context)
        }
    }
}