package ru.darkkeks.trackyoursheet.v2.telegram

import com.pengrad.telegrambot.model.Chat
import ru.darkkeks.trackyoursheet.v2.*
import java.time.Duration
import kotlin.reflect.KClass

interface CallbackSerializable {
    fun serialize(buffer: ButtonBuffer)
}

class SerializableRegistry<U : CallbackSerializable> {

    private val entries: MutableList<Entry> = mutableListOf()

    fun read(buffer: ButtonBuffer): U? {
        val id = buffer.peekByte()
        val result = entries.find { it.id == id } ?: return null
        buffer.popByte()
        return result.factory(buffer)
    }

    fun write(value: U, buffer: ButtonBuffer) {
        val entry = entries.find { it.klass == value::class }
            ?: throw IllegalArgumentException("Button type ${value::class} is not registered")
        buffer.pushByte(entry.id)
        value.serialize(buffer)
    }

    fun <T : U> register(id: Byte, klass: KClass<T>, factory: (ButtonBuffer) -> T) {
        entries.add(Entry(id, klass, factory))
    }

    inline fun <reified T : U> register(id: Byte, noinline factory: (ButtonBuffer) -> T) {
        register(id, T::class, factory)
    }

    private inner class Entry(val id: Byte, val klass: KClass<*>, val factory: (ButtonBuffer) -> U)
}

class Registry {

    val states = SerializableRegistry<MessageState>()
    val buttons = SerializableRegistry<CallbackButton>()

    init {

        states.register(0x00) { NullState() }
        buttons.register(0x00) { NewRangeState.SheetSelectButton(it.popInt(), "") }
        buttons.register(0x01) { NewRangeState.CancelButton() }

        buttons.register(0x7F) { GoBackButton() }

        states.register(0x01) { MainMenuState() }
        buttons.register(0x10) { MainMenuState.CreateNewRangeButton() }
        buttons.register(0x11) { MainMenuState.ListRangesButton() }
        buttons.register(0x12) { MainMenuState.SettingsButton() }

        states.register(0x02) { RangeListState() }
        buttons.register(0x20) { RangeListState.RangeButton(it.popId(), "") }

        states.register(0x03) { RangeMenuState(it.popId()) }
        buttons.register(0x30) { RangeMenuState.PostTargetButton("") }
        buttons.register(0x31) { RangeMenuState.DeleteButton() }
        buttons.register(0x32) { RangeMenuState.IntervalButton(PeriodTrackInterval(0)) }
        buttons.register(0x33) { RangeMenuState.ToggleEnabledButton(it.popBoolean()) }

        states.register(0x04) { SelectPostTargetState(it.popId()) }
        buttons.register(0x40) { SelectPostTargetState.PostTargetButton(Chat.Type.values()[it.popByte().toInt()]) }

        states.register(0x05) { SelectIntervalState(it.popId()) }
        buttons.register(0x50) { SelectIntervalState.IntervalButton(Duration.ofSeconds(it.popInt().toLong())) }

        states.register(0x06) { ConfirmDeletionState(it.popId()) }
        buttons.register(0x60) { ConfirmDeletionState.ConfirmButton(it.popBoolean()) }

        states.register(0x07) { NotFoundErrorState() }
    }
}