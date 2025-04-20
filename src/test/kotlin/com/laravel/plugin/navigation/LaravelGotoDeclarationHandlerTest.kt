package com.laravel.plugin.navigation

import com.laravel.plugin.LaravelTestCase

class LaravelGotoDeclarationHandlerTest : LaravelTestCase() {
    private lateinit var handler: LaravelGotoDeclarationHandler

    override fun setUp() {
        super.setUp()
        handler = LaravelGotoDeclarationHandler()
    }

    fun testRouteGotoDeclaration() {
        myFixture.copyFileToProject("routes/web.php", "routes/web.php")
        myFixture.configureByFile("RouteGotoTest.php")

        val element = myFixture.elementAtCaret
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        
        assertNotNull("Declaration targets should not be null", targets)
        if (targets != null) {
            assertEquals("Should go to controller method", "UserController.php", targets[0].containingFile.name)
        }
    }

    fun testViewGotoDeclaration() {
        myFixture.copyDirectoryToProject("views", "resources/views")
        myFixture.configureByFile("ViewGotoTest.php")

        val element = myFixture.elementAtCaret
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        
        assertNotNull("Declaration targets should not be null", targets)
        if (targets != null) {
            assertEquals("Should go to view file", "index.blade.php", targets[0].containingFile.name)
        }
    }

    fun testTranslationGotoDeclaration() {
        myFixture.copyDirectoryToProject("lang", "lang")
        myFixture.configureByFile("TranslationGotoTest.php")

        val element = myFixture.elementAtCaret
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        
        assertNotNull("Declaration targets should not be null", targets)
        if (targets != null) {
            assertEquals("Should go to translation file", "auth.php", targets[0].containingFile.name)
        }
    }

    override fun getTestDataPath(): String {
        return "src/test/testData/navigation"
    }
}