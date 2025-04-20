package com.laravel.plugin.completion

import com.laravel.plugin.LaravelTestCase
import com.laravel.plugin.services.ViewService

class ViewCompletionTest : LaravelTestCase() {
    override fun getTestDataPath(): String {
        return "src/test/testData/completion"
    }

    override fun setUp() {
        super.setUp()
    }

    fun testViewCompletion() {
        myFixture.copyDirectoryToProject("views", "resources/views")
        myFixture.configureByFile("ViewTest.php")

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain 'home' view", completions.any { it.lookupString == "home" })
        assertTrue("Should contain 'users.index' view", completions.any { it.lookupString == "users.index" })
    }

    fun testViewCompletionWithPrefix() {
        myFixture.copyDirectoryToProject("views", "resources/views")
        myFixture.configureByFile("ViewPrefixTest.php")

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain 'users.index' view", completions.any { it.lookupString == "users.index" })
    }
}