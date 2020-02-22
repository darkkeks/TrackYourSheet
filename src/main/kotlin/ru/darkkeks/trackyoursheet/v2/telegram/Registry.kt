package ru.darkkeks.trackyoursheet.v2.telegram

import ru.darkkeks.trackyoursheet.v2.MainMenuState
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
        states.register(0x01) { MainMenuState() }

        buttons.register(0x01) { MainMenuState.CreateNewRangeButton() }
        buttons.register(0x02) { MainMenuState.ListRangesButton() }
        buttons.register(0x03) { MainMenuState.SettingsButton() }
    }
}