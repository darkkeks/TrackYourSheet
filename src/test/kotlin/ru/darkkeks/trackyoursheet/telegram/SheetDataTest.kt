package ru.darkkeks.trackyoursheet.telegram

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.AnnotationSpec
import ru.darkkeks.trackyoursheet.sheet.SheetData

internal class SheetDataTest : AnnotationSpec() {

    private fun runTest(string: String, block: (SheetData.Companion.SheetUrlMatchResult?) -> Unit) {
        val result = SheetData.fromUrl(string)
        block(result)
    }

    private fun runTestIsNull(string: String) = runTest(string) {
        it shouldBe null
    }

    private fun runTestNotNull(string: String, block: (SheetData.Companion.SheetUrlMatchResult) -> Unit) = runTest(string) {
        it shouldNotBe null
        block(it!!)
    }

    @Test
    fun test1() = runTestNotNull("https://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf("gid" to "1697515667")
    }

    @Test
    fun test2() = runTestNotNull("http://docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf("gid" to "1697515667")
    }

    @Test
    fun test3() = runTestNotNull("www.docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf("gid" to "1697515667")
    }

    @Test
    fun test4() = runTestNotNull("docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667&range=123") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf("gid" to "1697515667", "range" to "123")
    }

    @Test
    fun test5() = runTestNotNull("docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf<String, String>()
    }

    @Test
    fun test6() = runTestNotNull("docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf<String, String>()
    }

    @Test
    fun test7() = runTestNotNull("docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8#gid=1697515667") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf("gid" to "1697515667")
    }

    @Test
    fun test8() = runTestNotNull("docs.google.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8") {
        it.id shouldBe "1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8"
        it.arguments shouldBe mapOf<String, String>()
    }

    @Test
    fun test9() = runTestIsNull("https://codepaste.ml")

    @Test
    fun test10() = runTestIsNull("https://docs.shmoogle.com/spreadsheets/d/1yrRO2hTjC13aAP4VYPO_NgyAp-VE9hKnog1EQ0uyBk8/edit#gid=1697515667")

    @Test
    fun test11() = runTestIsNull("https://docs.shmoogle.com#gid=1697515667")

}