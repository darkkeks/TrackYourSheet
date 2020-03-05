package ru.darkkeks.trackyoursheet.telegram

import io.kotlintest.shouldBe
import io.kotlintest.specs.AnnotationSpec
import kotlin.random.Random

internal class ButtonBufferTest : AnnotationSpec() {

    @Test
    fun canGetBytesBack() {
        for (value in 0..127) {
            val byte: Byte = value.toByte()
            val buffer = ButtonBuffer()
            buffer.pushByte(byte)
            val result = passThroughString(buffer)
            result.popByte() shouldBe byte
        }
    }

    @Test
    fun canGetNegativeBytesBack() {
        for (value in -128..-1) {
            val byte: Byte = value.toByte()
            val buffer = ButtonBuffer()
            buffer.pushByte(byte)
            val result = passThroughString(buffer)
            result.popByte() shouldBe byte
        }
    }

    @Test
    fun size() {
        for (amount in 0..10) {
            val buffer = ButtonBuffer()

            repeat(amount) {
                buffer.pushByte(Random.nextBytes(1)[0])
            }

            buffer.size shouldBe amount
            passThroughString(buffer).size shouldBe amount
        }
    }

    @Test
    fun canGetMultipleBytesBackSimple() {
        val buffer = ButtonBuffer()
        buffer.pushByte(2)
        buffer.pushByte(3)
        val result = passThroughString(buffer)
        result.popByte() shouldBe 2.toByte()
        result.popByte() shouldBe 3.toByte()
    }

    @Test
    fun canGetMultipleBytesBack() {
        for (amount in 2..10) {
            val bytes = Random.nextBytes(amount).toList()

            val buffer = ButtonBuffer()
            bytes.forEach { buffer.pushByte(it) }
            val result = passThroughString(buffer)
            val resultBytes = List(amount) {
                result.popByte()
            }

            resultBytes shouldBe bytes
        }
    }

    @Test
    fun pushIntPopIntSimple() {
        val buffer = ButtonBuffer()
        buffer.pushInt(123123123)
        val result = passThroughString(buffer)
        result.popInt() shouldBe 123123123
    }

    @Test
    fun pushIntPopIntNegativeSimple() {
        val buffer = ButtonBuffer()
        buffer.pushInt(-123123123)
        val result = passThroughString(buffer)
        result.popInt() shouldBe -123123123
    }

    @Test
    fun pushStringPopStringSimple() {
        val buffer = ButtonBuffer()
        val value = "Simple test string"
        buffer.pushString(value)
        val result = passThroughString(buffer)
        result.popString() shouldBe value
    }

    @Test
    fun pushStringPopStringSpecial() {
        val buffer = ButtonBuffer()
        val value = "Сложная \uD83D\uDE48 строка \uD83D\uDE0E"
        buffer.pushString(value)
        val result = passThroughString(buffer)
        result.popString() shouldBe value
    }

    private fun passThroughString(buffer: ButtonBuffer): ButtonBuffer {
        return ButtonBuffer(buffer.serialize())
    }

}