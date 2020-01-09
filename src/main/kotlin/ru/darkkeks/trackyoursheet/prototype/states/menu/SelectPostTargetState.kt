package ru.darkkeks.trackyoursheet.prototype.states.menu

import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.prototype.PostTarget
import ru.darkkeks.trackyoursheet.prototype.Range
import ru.darkkeks.trackyoursheet.prototype.telegram.*

class SelectPostTargetState(
    @Suppress("MemberVisibilityCanBePrivate")
    val rangeId: Id<Range>
) : MessageState() {

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
