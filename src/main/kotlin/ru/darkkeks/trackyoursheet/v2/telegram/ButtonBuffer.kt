package ru.darkkeks.trackyoursheet.v2.telegram

import ru.darkkeks.trackyoursheet.v2.buildList
import kotlin.random.Random

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

    override fun toString(): String {
        return Companion.toString(bytes)
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

fun ButtonBuffer.popInt(): Int {
    val (a, b, c, d) = popBytes(4)
    return (a.toUnsigned() shl 24) or
            (b.toUnsigned() shl 16) or
            (c.toUnsigned() shl 8) or
            d.toUnsigned()
}

fun ButtonBuffer.pushInt(value: Int) {
    val shifts = (0..3).reversed().map { it * 8 }
    shifts.forEach {
        pushByte(((value ushr it) and 0xFF).toByte())
    }
}

//inline fun <reified T> ButtonBuffer.pop(): T = when (T::class) {
//    Byte::class -> popByte() as T
//    Int::class -> popInt() as T
//    else -> throw UnsupportedOperationException("Invalid type ${T::class}")
//}

fun main() {
    val buffer = ButtonBuffer()
    repeat(10) {
        val value = Random.nextInt()
        println("Pushing $value")
        buffer.pushInt(value)
    }
    val s = buffer.toString()
    val b = ButtonBuffer(s)
    repeat(10) {
        println("Received ${b.popInt()}")
    }
}