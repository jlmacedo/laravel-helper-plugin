package com.laravel.plugin.services

import com.laravel.plugin.LaravelTestCase
import java.io.File

class RouteServiceTest : LaravelTestCase() {
    private lateinit var routeService: RouteService

    override fun setUp() {
        super.setUp()
        routeService = RouteService(project)
        
        // Create routes file
        createRoutesFile()
    }

    override fun getTestDataPath(): String {
        return "src/test/testData/routes"
    }

    fun testGetAllRoutes() {
        val routes = routeService.getAllRoutes()
        assertNotNull("Routes list should not be null", routes)
        assertTrue("Should contain 'home' route", routes.contains("home"))
        assertTrue("Should contain 'users.index' route", routes.contains("users.index"))
    }

    fun testGetRoutePaths() {
        assertEquals("GET", routeService.getRouteMethod("home"))
        assertEquals("GET", routeService.getRouteMethod("users.index"))
    }

    fun testFindRouteDeclaration() {
        val homeDeclarations = routeService.findRouteDeclaration("home")
        assertNotNull("Home route declarations should not be null", homeDeclarations)
        assertFalse("Home route declarations should not be empty", homeDeclarations.isEmpty())
    }

    private fun createRoutesFile() {
        // Create routes directory
        val routesDir = File(project.basePath, "routes")
        if (!routesDir.exists()) routesDir.mkdirs()

        // Create web.php file with route definitions
        File(routesDir, "web.php").writeText("""
            <?php
            
            Route::get('/', function () {
                return view('welcome');
            })->name('home');
            
            Route::get('/users', [UserController::class, 'index'])->name('users.index');
            Route::get('/users/{id}', [UserController::class, 'show'])->name('users.show');
        """.trimIndent())
    }
}