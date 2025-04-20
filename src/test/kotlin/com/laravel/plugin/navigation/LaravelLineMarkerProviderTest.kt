package com.laravel.plugin.navigation

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.laravel.plugin.LaravelTestCase
import java.io.File

class LaravelLineMarkerProviderTest : LaravelTestCase() {
    private lateinit var lineMarkerProvider: LaravelLineMarkerProvider

    override fun setUp() {
        super.setUp()
        lineMarkerProvider = LaravelLineMarkerProvider()
        
        // Set up test files
        createViewFiles()
    }

    fun testNoLineMarkersForEmptyFile() {
        myFixture.configureByText("Empty.blade.php", "")

        val lineMarkers = collectLineMarkers()
        assertFalse("Empty file should not have line markers", lineMarkers.isNotEmpty())
    }

    fun testBladeExtendsSectionHasLineMarker() {
        myFixture.configureByText("Child.blade.php", """
            @extends('layout')
            
            @section('content')
                <p>Child content</p>
            @endsection
        """.trimIndent())

        val lineMarkers = collectLineMarkers()
        assertFalse("Line markers should be found", lineMarkers.isEmpty())
    }

    fun testBladeIncludeHasLineMarker() {
        myFixture.configureByText("Include.blade.php", """
            <div>
                @include('partials.header')
                <main>Content</main>
                @include('partials.footer')
            </div>
        """.trimIndent())

        val lineMarkers = collectLineMarkers()
        assertFalse("Line markers should be found", lineMarkers.isEmpty())
    }

    fun testBladeYieldHasLineMarker() {
        myFixture.configureByText("Layout.blade.php", """
            <html>
            <head>
                <title>@yield('title')</title>
            </head>
            <body>
                <div class="container">
                    @yield('content')
                </div>
            </body>
            </html>
        """.trimIndent())

        val lineMarkers = collectLineMarkers()
        assertFalse("Line markers should be found", lineMarkers.isEmpty())
    }

    private fun collectLineMarkers(): List<LineMarkerInfo<*>> {
        val file = myFixture.file
        
        // Get line marker info directly from provider
        val singleMarker = lineMarkerProvider.getLineMarkerInfo(file)
        
        // Get line markers from collector
        val collectedMarkers = mutableListOf<LineMarkerInfo<*>>()
        lineMarkerProvider.collectSlowLineMarkers(listOf(file), collectedMarkers)
        
        // Combine both results
        val result = mutableListOf<LineMarkerInfo<*>>()
        if (singleMarker != null) {
            result.add(singleMarker)
        }
        result.addAll(collectedMarkers)
        
        return result
    }

    private fun createViewFiles() {
        // Create views directory
        val viewsDir = File(project.basePath, "resources/views")
        if (!viewsDir.exists()) viewsDir.mkdirs()

        // Create layout file
        File(viewsDir, "layout.blade.php").writeText("""
            <html>
            <head>
                <title>@yield('title')</title>
            </head>
            <body>
                <div class="container">
                    @yield('content')
                </div>
            </body>
            </html>
        """.trimIndent())

        // Create partials directory and files
        val partialsDir = File(viewsDir, "partials")
        if (!partialsDir.exists()) partialsDir.mkdirs()
        
        File(partialsDir, "header.blade.php").writeText("""
            <header>
                <h1>Laravel App</h1>
                <nav>Navigation</nav>
            </header>
        """.trimIndent())
        
        File(partialsDir, "footer.blade.php").writeText("""
            <footer>
                <p>&copy; 2023 Laravel</p>
            </footer>
        """.trimIndent())
    }
}