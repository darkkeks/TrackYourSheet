package ru.darkkeks.trackyoursheet.telegram

import org.litote.kmongo.Id
import org.litote.kmongo.id.StringId
import ru.darkkeks.trackyoursheet.buildList

class ButtonBuffer(value: String = "") {

    private val bytes: MutableList<Byte> = fromString(value).reversed().toMutableList()

    val size get() = bytes.size

    fun pushByte(byte: Byte) {
        require(bytes.size < MAX_SIZE)
        bytes.add(byte)
    }

    fun popByte(): Byte {
        require(bytes.size > 0)
        return bytes.removeAt(bytes.lastIndex)
    }

    fun peekByte(): Byte {
        require(bytes.size > 0)
        return bytes.last()
    }

    fun popBytes(count: Int): List<Byte> {
        require(count <= bytes.size)
        return buildList {
            repeat(count) {
                add(popByte())
            }
        }
    }

    fun peekBytes(count: Int): List<Byte> {
        require(count <= bytes.size)
        return bytes.subList(bytes.size - count, bytes.size).reversed()
    }

    fun serialize(): String {
        return Companion.toString(bytes)
    }

    override fun toString(): String {
        return bytes.joinToString(prefix = "[", postfix = "]")
    }

    companion object {
        const val DATA_MAX_SIZE = 64
        const val MAX_SIZE = DATA_MAX_SIZE * 7 / 8

        private fun fromString(string: String) = buildList<Byte> {
            var current: Long = 0
            var count = 0
            string.forEach { char ->
                val code = char.toLong()
                current = current or (code shl count)
                count += 7

                while (count >= 8) {
                    val byte = (current and 0xFF).toByte()
                    add(byte)
                    current = current shr 8
                    count -= 8
                }
            }

            require(current == 0L) {
                "Leftover bits"
            }
        }

        private fun toString(bytes: List<Byte>) = buildString {
            var current: Long = 0
            var count = 0

            fun addAll(minBitCount: Int = 7) {
                while (count >= minBitCount) {
                    append((current and 0x7F).toChar())
                    current = current shr 7
                    count -= 7
                }
            }

            bytes.forEach { byte ->
                current = current or (byte.toUnsigned().toLong() shl count)
                count += 8

                addAll(7)
            }

            addAll(1)
        }
    }
}

fun Byte.toUnsigned() = if (this >= 0) this.toInt() else 256 + this.toInt()


fun ButtonBuffer.peekInt(): Int {
    val (a, b, c, d) = peekBytes(4)
    return (a.toUnsigned() shl 24) or
            (b.toUnsigned() shl 16) or
            (c.toUnsigned() shl 8) or
            d.toUnsigned()
}

fun ButtonBuffer.popInt(): Int {
    val result = peekInt()
    repeat(4) { popByte() }
    return result
}

fun ButtonBuffer.pushInt(value: Int) {
    val shifts = (0..3).reversed().map { it * 8 }
    shifts.forEach {
        pushByte(((value ushr it) and 0xFF).toByte())
    }
}


fun ButtonBuffer.peekBoolean(): Boolean = peekByte() != 0.toByte()
fun ButtonBuffer.popBoolean(): Boolean = popByte() != 0.toByte()
fun ButtonBuffer.pushBoolean(value: Boolean) = pushByte(if(value) 1 else 0)


fun ButtonBuffer.peekString(): String {
    val length = peekByte().toInt()
    val result = peekBytes(length)
    return String(result.toByteArray())
}

fun ButtonBuffer.popString(): String {
    val length = popByte().toInt()
    val result = popBytes(length)
    return String(result.toByteArray())
}

fun ButtonBuffer.pushString(value: String) {
    val bytes = value.toByteArray()
    pushByte(bytes.size.toByte())
    bytes.forEach { pushByte(it) }
}


fun <T> ButtonBuffer.peekId() = StringId<T>(peekString())
fun <T> ButtonBuffer.popId() = StringId<T>(popString())
fun <T> ButtonBuffer.pushId(value: Id<T>) = pushString(value.toString())