package com.laravel.plugin.navigation

import com.intellij.psi.PsiFile
import com.laravel.plugin.LaravelTestCase
import com.laravel.plugin.services.AssetService
import java.io.File

class AssetGotoDeclarationHandlerTest : LaravelTestCase() {
    private lateinit var handler: AssetGotoDeclarationHandler

    override fun setUp() {
        super.setUp()
        handler = AssetGotoDeclarationHandler()
        
        // Create test assets
        createTestAssets()
    }

    override fun getTestDataPath(): String {
        return "src/test/testData/navigation"
    }

    fun testAssetGotoDeclaration() {
        myFixture.configureByText("AssetTest.php", """
            <?php
            
            echo asset('css/app.css');
            
        """.trimIndent())

        val element = myFixture.elementAtCaret
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        
        assertNotNull("Declaration targets should not be null", targets)
        if (targets != null) {
            assertTrue("Should target CSS file", targets.any { it is PsiFile && it.name == "app.css" })
        }
    }

    fun testAssetGotoDeclarationWithMix() {
        myFixture.configureByText("MixAssetTest.php", """
            <?php
            
            echo mix('js/app.js');
            
        """.trimIndent())

        val element = myFixture.elementAtCaret
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        
        assertNotNull("Declaration targets should not be null", targets)
        if (targets != null) {
            assertTrue("Should target JS file", targets.any { it is PsiFile && it.name == "app.js" })
        }
    }

    fun testAssetGotoDeclarationWithImage() {
        myFixture.configureByText("ImageAssetTest.php", """
            <?php
            
            echo asset('images/logo.png');
            
        """.trimIndent())

        val element = myFixture.elementAtCaret
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        
        assertNotNull("Declaration targets should not be null", targets)
        if (targets != null) {
            assertTrue("Should target image file", targets.any { it is PsiFile && it.name == "logo.png" })
        }
    }

    private fun createTestAssets() {
        // Create public directory
        val publicDir = File(project.basePath, "public")
        if (!publicDir.exists()) publicDir.mkdirs()

        // Create CSS directory and file
        val cssDir = File(publicDir, "css")
        if (!cssDir.exists()) cssDir.mkdirs()
        File(cssDir, "app.css").writeText("/* Test CSS */")

        // Create JS directory and file
        val jsDir = File(publicDir, "js")
        if (!jsDir.exists()) jsDir.mkdirs()
        File(jsDir, "app.js").writeText("// Test JS")

        // Create images directory and file
        val imagesDir = File(publicDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        File(imagesDir, "logo.png").writeText("PNG")
    }
}