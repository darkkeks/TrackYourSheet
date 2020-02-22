package ru.darkkeks.trackyoursheet.v2.telegram

abstract class State {
    abstract val handlerList: HandlerList

    suspend fun <T : BaseContext> handle(context: T) = handlerList.handle(context)
}

abstract class GlobalState : State()

abstract class MessageState : State(), CallbackSerializable {
    abstract suspend fun draw(context: BaseContext): MessageRender

    override fun serialize(buffer: ButtonBuffer) {}

    suspend fun send(context: BaseContext) {
        when (val render = draw(context)) {
            is ChangeStateRender -> render.state.send(context)
            is TextRender -> {
                context.send(render.text, replyMarkup = render.getMarkup(this, context.controller.registry))
            }
        }
    }
}