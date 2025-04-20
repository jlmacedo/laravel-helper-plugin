package com.laravel.plugin.completion

import com.laravel.plugin.LaravelTestCase
import com.laravel.plugin.services.RouteService

class RouteCompletionTest : LaravelTestCase() {
    override fun getTestDataPath(): String {
        return "src/test/testData/completion"
    }

    override fun setUp() {
        super.setUp()
        // Make sure route service is loaded
        project.getService(RouteService::class.java)
    }

    fun testRouteCompletion() {
        myFixture.copyFileToProject("routes/web.php", "routes/web.php")
        myFixture.configureByFile("RouteTest.php")

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain 'home' route", completions.any { it.lookupString == "home" })
        assertTrue("Should contain 'users.index' route", completions.any { it.lookupString == "users.index" })
    }

    fun testRouteCompletionWithPrefix() {
        myFixture.copyFileToProject("routes/web.php", "routes/web.php")
        myFixture.configureByFile("RoutePrefixTest.php")

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain 'users.index' route", completions.any { it.lookupString == "users.index" })
        assertTrue("Should contain 'users.show' route", completions.any { it.lookupString == "users.show" })
    }
}