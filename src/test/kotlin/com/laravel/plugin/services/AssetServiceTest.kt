package com.laravel.plugin.services

import com.laravel.plugin.LaravelTestCase
import java.io.File

class AssetServiceTest : LaravelTestCase() {
    private lateinit var assetService: AssetService

    override fun setUp() {
        super.setUp()
        assetService = AssetService(project)
        
        // Create test asset files in the project
        createTestAssets()
    }

    override fun getTestDataPath(): String {
        return "src/test/testData/assets"
    }

    fun testGetAllAssets() {
        val assets = assetService.getAllAssets()
        assertNotNull("Assets list should not be null", assets)
        assertTrue("Should contain CSS asset", assets.contains("css/app.css"))
        assertTrue("Should contain JS asset", assets.contains("js/app.js"))
        assertTrue("Should contain image asset", assets.contains("images/logo.png"))
    }

    fun testGetAssetPath() {
        assertEquals("css/app.css", assetService.getAssetPath("css/app.css"))
        assertEquals("js/app.js", assetService.getAssetPath("js/app.js"))
        assertEquals("images/logo.png", assetService.getAssetPath("images/logo.png"))
    }

    fun testGetAssetType() {
        assertEquals(AssetService.AssetType.CSS, assetService.getAssetType("css/app.css"))
        assertEquals(AssetService.AssetType.JS, assetService.getAssetType("js/app.js"))
        assertEquals(AssetService.AssetType.IMAGE, assetService.getAssetType("images/logo.png"))
    }

    fun testIsAsset() {
        assertTrue("Should recognize CSS asset", assetService.isAsset("css/app.css"))
        assertTrue("Should recognize JS asset", assetService.isAsset("js/app.js"))
        assertTrue("Should recognize image asset", assetService.isAsset("images/logo.png"))
        assertFalse("Should not recognize non-existing asset", assetService.isAsset("not/exists.txt"))
    }

    fun testFindAssetDeclaration() {
        val cssDeclarations = assetService.findAssetDeclaration("css/app.css")
        assertNotNull("CSS asset declarations should not be null", cssDeclarations)
        assertFalse("CSS asset declarations should not be empty", cssDeclarations.isEmpty())

        val jsDeclarations = assetService.findAssetDeclaration("js/app.js")
        assertNotNull("JS asset declarations should not be null", jsDeclarations)
        assertFalse("JS asset declarations should not be empty", jsDeclarations.isEmpty())

        val imageDeclarations = assetService.findAssetDeclaration("images/logo.png")
        assertNotNull("Image asset declarations should not be null", imageDeclarations)
        assertFalse("Image asset declarations should not be empty", imageDeclarations.isEmpty())
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