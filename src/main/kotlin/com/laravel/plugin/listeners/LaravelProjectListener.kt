package com.laravel.plugin.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.laravel.plugin.services.AssetService
import com.laravel.plugin.services.RouteService
import com.laravel.plugin.services.TranslationService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.project.guessProjectDir

/**
 * Listener that initializes Laravel services when a project is opened.
 * This ensures that assets, routes, and translations are scanned and indexed
 * as soon as the project is loaded.
 */
class LaravelProjectListener : ProjectManagerListener, StartupActivity {

    override fun projectOpened(project: Project) {
        refreshServices(project)
    }

    override fun runActivity(project: Project) {
        refreshServices(project)
    }

    private fun refreshServices(project: Project) {
        // Use background thread to avoid UI freezing
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Check if this is a Laravel project
                if (isLaravelProject(project)) {
                    // Refresh asset service
                    project.getService(AssetService::class.java)?.let { assetService ->
                        ApplicationManager.getApplication().runReadAction {
                            // This will scan for all assets in the project
                            assetService.getAllAssets()
                        }
                    }

                    // Refresh translation service
                    project.getService(TranslationService::class.java)?.let { translationService ->
                        ApplicationManager.getApplication().runReadAction {
                            // This will scan for all translations in the project
                            translationService.scanTranslations()
                        }
                    }

                    // Refresh route service
                    project.getService(RouteService::class.java)?.let { routeService ->
                        ApplicationManager.getApplication().runReadAction {
                            // This will scan for all routes and their usages
                            routeService.scanRoutes()
                            routeService.scanForRouteUsages()
                        }
                    }
                }
            } catch (e: Exception) {
                // Log any errors but don't crash the plugin
                e.printStackTrace()
            }
        }
    }
    
    private fun isLaravelProject(project: Project): Boolean {
        // Check for composer.json with Laravel dependency
        val projectDir = project.guessProjectDir()
        val composerJson = projectDir?.findChild("composer.json")
        
        if (composerJson != null && composerJson.exists()) {
            val content = String(composerJson.contentsToByteArray())
            
            // Check for Laravel framework
            if (content.contains("\"laravel/framework\"") || 
                content.contains("\"laravel/laravel\"")) {
                return true
            }
            
            // Check for Laravel-specific namespaces
            if (content.contains("Illuminate\\\\") || 
                content.contains("Laravel\\\\")) {
                return true
            }
        }
        
        // Check for typical Laravel directory structure
        val hasArtisan = projectDir?.findChild("artisan") != null
        val hasApp = projectDir?.findChild("app") != null
        val hasConfig = projectDir?.findChild("config") != null
        val hasRoutes = projectDir?.findChild("routes") != null
        
        // If most of these directories exist, it's likely a Laravel project
        return (hasArtisan && (hasApp || hasConfig || hasRoutes))
    }
}