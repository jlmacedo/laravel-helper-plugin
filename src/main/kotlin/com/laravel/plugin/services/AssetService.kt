package com.laravel.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AssetService(private val project: Project) {
    private val assetCache = ConcurrentHashMap<String, AssetInfo>()

    data class AssetInfo(
        val name: String,
        val path: String,
        val extension: String,
        val type: AssetType
    )

    enum class AssetType {
        CSS, JS, IMAGE, FONT, OTHER
    }

    init {
        // Scan assets when service is initialized
        scanAssets()
    }
    
    fun getAllAssets(): List<String> {
        // If cache is empty, scan again to ensure we have the latest data
        if (assetCache.isEmpty()) {
            scanAssets()
        }
        return assetCache.keys.toList()
    }

    fun getAssetPath(assetName: String): String {
        // Ensure cache is populated
        if (assetCache.isEmpty()) {
            scanAssets()
        }
        return assetCache[assetName]?.path ?: ""
    }

    fun getAssetType(assetName: String): AssetType {
        // Ensure cache is populated
        if (assetCache.isEmpty()) {
            scanAssets()
        }
        return assetCache[assetName]?.type ?: AssetType.OTHER
    }

    fun isAsset(text: String): Boolean {
        // Ensure cache is populated
        if (assetCache.isEmpty()) {
            scanAssets()
        }
        return assetCache.containsKey(text)
    }

    fun findAssetDeclaration(assetName: String): List<PsiElement> {
        val results = mutableListOf<PsiElement>()
        
        // Ensure cache is populated
        if (assetCache.isEmpty()) {
            scanAssets()
        }
        
        val assetInfo = assetCache[assetName] ?: return results

        // Look in public directory first
        val publicAssetFile = project.guessProjectDir()
            ?.findChild("public")
            ?.findFileByRelativePath(assetInfo.path)

        // Then look in resources directory - check for both 'assets' and 'js/css' folders
        val resourcesAssetFile = project.guessProjectDir()
            ?.findChild("resources")
            ?.findChild("assets")
            ?.findFileByRelativePath(assetInfo.path)
            
        // Also check for resources/js and resources/css for newer Laravel versions
        val resourcesJsFile = project.guessProjectDir()
            ?.findChild("resources")
            ?.findChild("js")
            ?.findFileByRelativePath(assetInfo.path.removePrefix("js/"))
            
        val resourcesCssFile = project.guessProjectDir()
            ?.findChild("resources")
            ?.findChild("css")
            ?.findFileByRelativePath(assetInfo.path.removePrefix("css/"))
            
        // Check in resources directory directly (for newer Laravel versions)
        val resourcesFile = project.guessProjectDir()
            ?.findChild("resources")
            ?.findFileByRelativePath(assetInfo.path)

        // Use the first file found, checking in order of most common locations
        val assetFile = publicAssetFile ?: resourcesAssetFile ?: resourcesJsFile ?: resourcesCssFile ?: resourcesFile
        assetFile?.let {
            val psiFile = PsiManager.getInstance(project).findFile(it)
            psiFile?.let { file ->
                results.add(file)
            }
        }

        return results
    }

    private fun scanAssets() {
        // Clear the cache before scanning to ensure fresh data
        assetCache.clear()
        
        // Get the project root directory
        val projectDir = project.guessProjectDir() ?: return
        
        // Scan public directory (for compiled/published assets)
        val publicDir = projectDir.findChild("public")
        if (publicDir != null && publicDir.exists() && publicDir.isValid) {
            scanDirectory(publicDir, "", true)
        }

        // Get the resources directory
        val resourcesDir = projectDir.findChild("resources")
        if (resourcesDir != null && resourcesDir.exists() && resourcesDir.isValid) {
            // Scan resources/assets directory (for Laravel Mix)
            val assetsDir = resourcesDir.findChild("assets")
            if (assetsDir != null && assetsDir.exists() && assetsDir.isValid) {
                scanDirectory(assetsDir, "", false)
            }
            
            // Scan resources/js directory (for Vite/newer Laravel)
            val jsDir = resourcesDir.findChild("js")
            if (jsDir != null && jsDir.exists() && jsDir.isValid) {
                scanDirectory(jsDir, "js", false)
            }
            
            // Scan resources/css directory (for Vite/newer Laravel)
            val cssDir = resourcesDir.findChild("css")
            if (cssDir != null && cssDir.exists() && cssDir.isValid) {
                scanDirectory(cssDir, "css", false)
            }
            
            // Scan resources/sass directory (for older Laravel)
            val sassDir = resourcesDir.findChild("sass")
            if (sassDir != null && sassDir.exists() && sassDir.isValid) {
                scanDirectory(sassDir, "sass", false)
            }
            
            // Scan resources/scss directory (for newer Laravel)
            val scssDir = resourcesDir.findChild("scss")
            if (scssDir != null && scssDir.exists() && scssDir.isValid) {
                scanDirectory(scssDir, "scss", false)
            }
        }
        
        // Support Laravel 6+ build directories
        val nodeModulesDir = projectDir.findChild("node_modules")
        if (nodeModulesDir != null && nodeModulesDir.exists() && nodeModulesDir.isValid) {
            // Check for common frontend libraries that Laravel uses
            val buildDirs = listOf(
                "bootstrap", "jquery", "vue", "react", "alpinejs", "tailwindcss", "livewire"
            )
            
            buildDirs.forEach { dirName ->
                val libDir = nodeModulesDir.findChild(dirName)
                if (libDir != null && libDir.exists() && libDir.isValid) {
                    val distDir = libDir.findChild("dist")
                    if (distDir != null && distDir.exists() && distDir.isValid) {
                        scanDirectory(distDir, "node_modules/$dirName/dist", false)
                    }
                }
            }
        }
    }

    private fun scanDirectory(directory: VirtualFile, prefix: String, isPublic: Boolean) {
        try {
            if (!directory.isValid || !directory.exists()) {
                return
            }
            
            directory.children.forEach { child ->
                if (child.isDirectory) {
                    val newPrefix = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                    scanDirectory(child, newPrefix, isPublic)
                } else {
                    val extension = child.extension ?: ""
                    val type = determineAssetType(extension)

                    // Skip non-asset files
                    if (type == AssetType.OTHER && !isAssetExtension(extension)) {
                        return@forEach
                    }

                    val assetPath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                    
                    // Generate the asset name based on location
                    val assetName = when {
                        isPublic -> assetPath
                        prefix.startsWith("js") || directory.name == "js" -> assetPath
                        prefix.startsWith("css") || directory.name == "css" -> assetPath
                        else -> "assets/$assetPath"
                    }

                    assetCache[assetName] = AssetInfo(
                        name = assetName,
                        path = assetPath,
                        extension = extension,
                        type = type
                    )
                }
            }
        } catch (e: Exception) {
            // Handle potential exceptions gracefully
        }
    }

    private fun determineAssetType(extension: String): AssetType {
        return when (extension.lowercase()) {
            "css", "scss", "sass", "less" -> AssetType.CSS
            "js", "ts", "jsx", "tsx" -> AssetType.JS
            "jpg", "jpeg", "png", "gif", "svg", "webp", "ico" -> AssetType.IMAGE
            "ttf", "woff", "woff2", "eot" -> AssetType.FONT
            else -> AssetType.OTHER
        }
    }

    private fun isAssetExtension(extension: String): Boolean {
        return extension.lowercase() in listOf(
            "css", "scss", "sass", "less",
            "js", "ts", "jsx", "tsx",
            "jpg", "jpeg", "png", "gif", "svg", "webp", "ico",
            "ttf", "woff", "woff2", "eot",
            "pdf", "json"
        )
    }
}