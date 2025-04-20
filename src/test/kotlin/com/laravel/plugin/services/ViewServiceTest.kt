package com.laravel.plugin.services

import com.laravel.plugin.LaravelTestCase
import java.io.File

class ViewServiceTest : LaravelTestCase() {
    private lateinit var viewService: ViewService

    override fun setUp() {
        super.setUp()
        viewService = ViewService(project)
        
        // Create view files
        createViewFiles()
    }

    override fun getTestDataPath(): String {
        return "src/test/testData/views"
    }

    fun testGetAllViews() {
        val views = viewService.getAllViews()
        assertNotNull("Views list should not be null", views)
        assertTrue("Should contain 'home' view", views.contains("home"))
        assertTrue("Should contain 'users.index' view", views.contains("users.index"))
        assertTrue("Should contain 'layouts.app' view", views.contains("layouts.app"))
    }

    fun testGetViewPath() {
        val path = viewService.getViewPath("home")
        assertNotNull("View path should not be null", path)
    }

    fun testFindViewDeclaration() {
        val homeDeclarations = viewService.findViewDeclaration("home")
        assertNotNull("Home view declarations should not be null", homeDeclarations)
        assertFalse("Home view declarations should not be empty", homeDeclarations.isEmpty())
    }

    fun testIsView() {
        val homeResult = viewService.isView("home")
        assertTrue("Should recognize 'home' view", homeResult)
        assertTrue("Should recognize 'users.index' view", viewService.isView("users.index"))
    }

    fun testIsViewWithNonExistentView() {
        val result = viewService.isView("nonexistent.view")
        assertFalse("Should not recognize non-existent view", result)
    }

    private fun createViewFiles() {
        // Create views directory
        val viewsDir = File(project.basePath, "resources/views")
        if (!viewsDir.exists()) viewsDir.mkdirs()

        // Create home.blade.php
        File(viewsDir, "home.blade.php").writeText("""
            @extends('layouts.app')
            
            @section('content')
                <h1>Welcome to Laravel</h1>
            @endsection
        """.trimIndent())

        // Create layouts directory and app.blade.php
        val layoutsDir = File(viewsDir, "layouts")
        if (!layoutsDir.exists()) layoutsDir.mkdirs()
        
        File(layoutsDir, "app.blade.php").writeText("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>Laravel App</title>
            </head>
            <body>
                <div class="container">
                    @yield('content')
                </div>
            </body>
            </html>
        """.trimIndent())

        // Create users directory and index.blade.php - avoiding Blade variables in test
        val usersDir = File(viewsDir, "users")
        if (!usersDir.exists()) usersDir.mkdirs()
        
        File(usersDir, "index.blade.php").writeText("""
            @extends('layouts.app')
            
            @section('content')
                <h1>Users</h1>
                <ul>
                    <li>User 1</li>
                    <li>User 2</li>
                    <li>User 3</li>
                </ul>
            @endsection
        """.trimIndent())
    }
}