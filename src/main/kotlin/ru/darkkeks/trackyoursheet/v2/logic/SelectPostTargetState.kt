package ru.darkkeks.trackyoursheet.v2.logic

import com.pengrad.telegrambot.model.Chat
import org.litote.kmongo.Id
import ru.darkkeks.trackyoursheet.v2.PostTarget
import ru.darkkeks.trackyoursheet.v2.Range
import ru.darkkeks.trackyoursheet.v2.telegram.*

class SelectPostTargetState(private var rangeId: Id<Range>) : MessageState() {
    class PostTargetButton(val target: Chat.Type)
        : TextButton(if (target == Chat.Type.Private) "Постить сюда" else "Постить в группу/канал") {

        override fun serialize(buffer: ButtonBuffer) = buffer.pushByte(target.ordinal.toByte())
    }

    override fun serialize(buffer: ButtonBuffer) = buffer.pushId(rangeId)

    override suspend fun draw(context: BaseContext): MessageRender {
        val range = context.controller.repository.getRange(rangeId)
            ?: return ChangeStateRender(NotFoundErrorState())

        val target = range.postTarget
        val name = when (target.type) {
            Chat.Type.Private -> "сюда"
            Chat.Type.channel -> "в канал ${target.name}"
            Chat.Type.group -> "в группу ${target.name}"
            Chat.Type.supergroup -> "в супергруппу ${target.name}"
        }

        return TextRender("""
            Сейчас сообщения отправляются $name.
        """.trimIndent(), buildInlineKeyboard {
            row(GoBackButton())
            if (target.type != Chat.Type.Private) {
                row(PostTargetButton(Chat.Type.Private))
            }
            row(PostTargetButton(Chat.Type.group))
        })
    }

    override val handlerList = buildHandlerList {
        callback<PostTargetButton> {
            val range = controller.repository.getRange(rangeId)
            when {
                range == null -> changeState(NotFoundErrorState())
                button.target == Chat.Type.Private -> {
                    val newTarget = PostTarget.private(userId)
                    val newRange = range.copy(postTarget = newTarget)
                    controller.repository.saveRange(newRange)
                    changeState(RangeMenuState(rangeId))
                }
                else -> changeGlobalState(ReceiveGroupState(rangeId))
            }
        }
        callback<GoBackButton> {
            changeState(RangeMenuState(rangeId))
        }
    }
}