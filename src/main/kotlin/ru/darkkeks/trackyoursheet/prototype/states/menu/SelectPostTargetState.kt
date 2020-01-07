package ru.darkkeks.trackyoursheet.prototype.states.menu

import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatAdministrators
import com.pengrad.telegrambot.request.GetChatMember
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
                row(postToChannel(target.type))
                row(postToGroup(target.type))
            })
        }
    }

    private fun postHere() = button("Постить сюда", PostTargetButton(Chat.Type.Private, this))

    private fun postToChannel(currentType: Chat.Type): InlineKeyboardButton {
        val button = PostTargetButton(Chat.Type.channel, this)
        val other = currentType == Chat.Type.channel
        return button("Постить в${if (other) " другой " else " "}канал", button)
    }

    private fun postToGroup(currentType: Chat.Type): InlineKeyboardButton {
        val button = PostTargetButton(Chat.Type.group, this)
        val other = currentType == Chat.Type.group || currentType == Chat.Type.supergroup
        return button("Постить в${if (other) " другую " else " "}группу", button)
    }

    class PostTargetButton(val type: Chat.Type, state: MessageState) : StatefulButton(state)

    override suspend fun handleButton(context: CallbackButtonContext) {
        val range = context.controller.sheetDao.getJob(rangeId)
        if (range == null) {
            changeState(NotFoundErrorState(), context)
        } else if (context.button is PostTargetButton) {
            when (context.button.type) {
                Chat.Type.Private -> {
                    val newTarget = PostTarget.private(context.userId)
                    val newRange = range.copy(postTarget = newTarget)
                    context.controller.sheetDao.saveJob(newRange)
                    changeState(RangeMenuState(rangeId), context)
                }
                Chat.Type.group, Chat.Type.supergroup, Chat.Type.channel -> {
                    lateinit var state: ReceiveGroupState
                    hijackGlobalState(context) { parent ->
                        ReceiveGroupState(rangeId, parent).also { state = it }
                    }
                    state.initiate(context)
                }
            }
        }
    }
}

class ReceiveGroupState(private val rangeId: Id<Range>, parentState: GlobalUserState) : GlobalUserState(parentState) {

    suspend fun initiate(context: UserActionContext) {
        context.reply("""
            Ты хочешь выбрать паблик/канал.
            
            В первую очередь убедись, что у тебя есть админка в чатике/канале.
            
            Чтобы выбрать, тебе надо сделать что нибудь из списка ниже:
            - Прислать мне id чата/канала, например `-1001208998514`.
            - Прислать мне username чата/канала, например `IERussia` или `@IERussia`.
            - Переслать мне любое сообщение из чата/канала.
            - В чате набрать в поле для сообщения @${context.controller.me.user().username()} и нажать _Выбрать чат для постинга_.
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
            context.message.text().dropWhile { it == '@' }
        }

        // TODO Тонны ошибок, про которые мне лень пока что думать ;DDDDDDddd
        val myId = context.controller.me.user()
        val chat = context.controller.bot.execute(GetChat(chatId))
        val meInChat = context.controller.bot.execute(GetChatMember(chatId, myId.id()))
        val admins = context.controller.bot.execute(GetChatAdministrators(chatId))
        when {
            admins.administrators().none { it.user().id() == context.userId } -> {
                context.reply("Ты не админ, геюга")
            }
            meInChat.chatMember() == null -> {
                context.reply("Меня нету в чате \uD83E\uDD28")
            }
            meInChat.chatMember().canSendMessages() == false -> {
                context.reply("Это все конечно замечательно, но у меня нет прав отправлять сообщения в этом чате \uD83D\uDE12\n\nПопробуй еще раз.")
            }
            else -> {
                context.reply("Океюшки, выбираем чатик _${chat.chat().title()}_.")
                replacePostTarget(context, PostTarget(chat.chat().id(), chat.chat().title(), chat.chat().type()))
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