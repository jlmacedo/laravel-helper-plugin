package com.laravel.plugin.completion

import com.laravel.plugin.LaravelTestCase
import com.laravel.plugin.services.AssetService
import java.io.File

class AssetCompletionTest : LaravelTestCase() {
    override fun getTestDataPath(): String {
        return "src/test/testData/completion"
    }

    override fun setUp() {
        super.setUp()
        // Make sure asset service is loaded
        project.getService(AssetService::class.java)

        // Create test assets
        createTestAssets()
    }

    fun testAssetCompletion() {
        myFixture.configureByText("AssetTest.php", """
            <?php
            
            echo asset('<caret>');
            
        """.trimIndent())

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain CSS asset", completions.any { it.lookupString == "css/app.css" })
        assertTrue("Should contain JS asset", completions.any { it.lookupString == "js/app.js" })
        assertTrue("Should contain image asset", completions.any { it.lookupString == "images/logo.png" })
    }

    fun testAssetCompletionWithPrefix() {
        myFixture.configureByText("AssetPrefixTest.php", """
            <?php
            
            echo asset('css/<caret>');
            
        """.trimIndent())

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain CSS asset", completions.any { it.lookupString == "css/app.css" })
        assertFalse("Should not contain JS asset", completions.any { it.lookupString == "js/app.js" })
    }

    fun testMixAssetCompletion() {
        myFixture.configureByText("MixAssetTest.php", """
            <?php
            
            echo mix('<caret>');
            
        """.trimIndent())

        val completions = myFixture.completeBasic()
        assertNotNull("Completions should not be null", completions)
        assertTrue("Should contain CSS asset", completions.any { it.lookupString == "css/app.css" })
        assertTrue("Should contain JS asset", completions.any { it.lookupString == "js/app.js" })
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