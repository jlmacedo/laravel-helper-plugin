package com.laravel.plugin.services

import com.laravel.plugin.LaravelTestCase
import java.io.File

class TranslationServiceTest : LaravelTestCase() {
    private lateinit var translationService: TranslationService

    override fun setUp() {
        super.setUp()
        translationService = TranslationService(project)
        
        // Create translation files
        createTranslationFiles()
    }

    override fun getTestDataPath(): String {
        return "src/test/testData/translations"
    }

    fun testGetAllTranslations() {
        val translations = translationService.getAllTranslations()
        assertNotNull("Translations list should not be null", translations)
        assertTrue("Should contain 'welcome' translation", translations.contains("welcome"))
        assertTrue("Should contain 'auth.failed' translation", translations.contains("auth.failed"))
    }

    fun testGetTranslationPath() {
        val value = translationService.getTranslationValue("welcome")
        assertEquals("Welcome to Laravel", value)
    }

    fun testFindTranslationDeclaration() {
        val welcomeDeclarations = translationService.findTranslationDeclaration(project, "welcome")
        assertNotNull("Welcome translation declarations should not be null", welcomeDeclarations)
        assertFalse("Welcome translation declarations should not be empty", welcomeDeclarations.isEmpty())
    }

    fun testIsTranslation() {
        assertTrue("Should recognize 'welcome' translation", translationService.isTranslation("welcome"))
        assertTrue("Should recognize 'auth.failed' translation", translationService.isTranslation("auth.failed"))
    }

    fun testGetTranslationValue() {
        val welcome = translationService.getTranslationValue("welcome")
        val authFailed = translationService.getTranslationValue("auth.failed")
        
        assertEquals("Welcome to Laravel", welcome)
        assertEquals("These credentials do not match our records.", authFailed)
    }

    private fun createTranslationFiles() {
        // Create lang directory
        val langDir = File(project.basePath, "lang")
        if (!langDir.exists()) langDir.mkdirs()

        // Create en directory and files
        val enDir = File(langDir, "en")
        if (!enDir.exists()) enDir.mkdirs()
        
        // Create welcome.php file with translation
        File(enDir, "welcome.php").writeText("""
            <?php
            
            return [
                'welcome' => 'Welcome to Laravel',
            ];
        """.trimIndent())
        
        // Create auth.php file with translations
        File(enDir, "auth.php").writeText("""
            <?php
            
            return [
                'failed' => 'These credentials do not match our records.',
                'password' => 'The provided password is incorrect.',
                'throttle' => 'Too many login attempts. Please try again in :seconds seconds.',
            ];
        """.trimIndent())
    }
}