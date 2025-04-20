package com.laravel.plugin.completion

import com.laravel.plugin.LaravelTestCase
import com.laravel.plugin.services.TranslationService

class TranslationCompletionTest : LaravelTestCase() {
    override fun getTestDataPath(): String {
        return "src/test/testData/completion"
    }

    override fun setUp() {
        super.setUp()
    }

    fun testTranslationCompletion() {
        myFixture.copyDirectoryToProject("lang", "lang")
        myFixture.configureByFile("TranslationTest.php")

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain 'welcome' translation", completions.any { it.lookupString == "welcome" })
        assertTrue("Should contain 'auth.failed' translation", completions.any { it.lookupString == "auth.failed" })
    }

    fun testTranslationCompletionWithPrefix() {
        myFixture.copyDirectoryToProject("lang", "lang")
        myFixture.configureByFile("TranslationPrefixTest.php")

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain 'auth.failed' translation", completions.any { it.lookupString == "auth.failed" })
    }
}