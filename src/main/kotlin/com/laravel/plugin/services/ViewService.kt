package com.laravel.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ViewService(private val project: Project) {
    private val viewCache = ConcurrentHashMap<String, ViewInfo>()

    data class ViewInfo(
        val name: String,
        val path: String,
        val extension: String
    )
    
    init {
        // Scan views when service is initialized
        scanViews()
    }

    fun getAllViews(): List<String> {
        if (viewCache.isEmpty()) {
            scanViews()
        }
        return viewCache.keys.toList()
    }

    fun getViewPath(viewName: String): String {
        if (viewCache.isEmpty()) {
            scanViews()
        }
        return viewCache[viewName]?.path ?: ""
    }

    fun isView(text: String): Boolean {
        if (viewCache.isEmpty()) {
            scanViews()
        }
        return viewCache.containsKey(text)
    }

    fun findViewDeclaration(viewName: String): List<PsiElement> {
        val results = mutableListOf<PsiElement>()
        
        if (viewCache.isEmpty()) {
            scanViews()
        }

        // Try multiple format variations of the view name
        val viewNameVariations = mutableListOf(
            viewName,                               // Original format
            viewName.replace('/', '.'),            // Convert slashes to dots
            viewName.replace('.', '/'),            // Convert dots to slashes
            viewName.replace(".blade.php", ""),     // Remove .blade.php if present
            viewName.replace(".php", "")            // Remove .php if present
        ).distinct()

        // Try each variation against the cache
        for (variation in viewNameVariations) {
            val viewInfo = viewCache[variation]
            if (viewInfo != null) {
                // Check standard Laravel resources/views directory
                var viewFile = project.guessProjectDir()
                    ?.findChild("resources")
                    ?.findChild("views")
                    ?.findFileByRelativePath(viewInfo.path)
                
                // If not found, try Laravel 6+ vendor view locations
                if (viewFile == null) {
                    viewFile = project.guessProjectDir()
                        ?.findChild("vendor")
                        ?.findFileByRelativePath("views/${viewInfo.path}")
                }
                
                // Also check modules if using a modular structure
                if (viewFile == null) {
                    val modulesDir = project.guessProjectDir()?.findChild("Modules")
                    if (modulesDir != null && modulesDir.exists()) {
                        for (module in modulesDir.children) {
                            if (module.isDirectory) {
                                val moduleViewFile = module.findChild("Resources")
                                    ?.findChild("views")
                                    ?.findFileByRelativePath(viewInfo.path)
                                
                                if (moduleViewFile != null) {
                                    viewFile = moduleViewFile
                                    break
                                }
                            }
                        }
                    }
                }
                
                // Check legacy Laravel 6 views location as well
                if (viewFile == null) {
                    viewFile = project.guessProjectDir()
                        ?.findChild("app")
                        ?.findChild("views")
                        ?.findFileByRelativePath(viewInfo.path)
                }

                viewFile?.let {
                    val psiFile = PsiManager.getInstance(project).findFile(it)
                    psiFile?.let { file ->
                        results.add(file)
                    }
                }
            }
        }

        // If still not found, try direct path variations
        if (results.isEmpty()) {
            val pathVariations = listOf(
                viewName.replace('.', '/') + ".blade.php",
                viewName.replace('.', '/') + ".php",
                viewName + ".blade.php",
                viewName + ".php"
            )

            // Try standard location
            for (path in pathVariations) {
                var viewFile = project.guessProjectDir()
                    ?.findChild("resources")
                    ?.findChild("views")
                    ?.findFileByRelativePath(path)
                
                // Try vendor and modules
                if (viewFile == null) {
                    viewFile = project.guessProjectDir()
                        ?.findChild("vendor")
                        ?.findFileByRelativePath("views/$path")
                    
                    if (viewFile == null) {
                        val modulesDir = project.guessProjectDir()?.findChild("Modules")
                        if (modulesDir != null && modulesDir.exists()) {
                            for (module in modulesDir.children) {
                                if (module.isDirectory) {
                                    val moduleViewFile = module.findChild("Resources")
                                        ?.findChild("views")
                                        ?.findFileByRelativePath(path)
                                    
                                    if (moduleViewFile != null) {
                                        viewFile = moduleViewFile
                                        break
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check legacy location 
                    if (viewFile == null) {
                        viewFile = project.guessProjectDir()
                            ?.findChild("app")
                            ?.findChild("views")
                            ?.findFileByRelativePath(path)
                    }
                }

                viewFile?.let {
                    val psiFile = PsiManager.getInstance(project).findFile(it)
                    psiFile?.let { file ->
                        results.add(file)
                    }
                }
            }
        }

        return results
    }

    fun scanViews() {
        // Clear the cache before scanning to ensure fresh data
        viewCache.clear()
        
        // Get standard Laravel views directory
        val viewsDir = project.guessProjectDir()
            ?.findChild("resources")
            ?.findChild("views")

        if (viewsDir != null && viewsDir.exists() && viewsDir.isValid) {
            scanDirectory(viewsDir, "")
        }
        
        // Check for legacy Laravel 6 views location
        val legacyViewsDir = project.guessProjectDir()
            ?.findChild("app")
            ?.findChild("views")
            
        if (legacyViewsDir != null && legacyViewsDir.exists() && legacyViewsDir.isValid) {
            scanDirectory(legacyViewsDir, "")
        }
        
        // Check for vendor views
        val vendorViewsDir = project.guessProjectDir()
            ?.findChild("vendor")
            ?.findChild("views")
            
        if (vendorViewsDir != null && vendorViewsDir.exists() && vendorViewsDir.isValid) {
            scanDirectory(vendorViewsDir, "vendor")
        }
        
        // Check for module views in modular Laravel projects
        val modulesDir = project.guessProjectDir()?.findChild("Modules")
        if (modulesDir != null && modulesDir.exists() && modulesDir.isValid) {
            for (module in modulesDir.children) {
                if (module.isDirectory) {
                    val moduleViewsDir = module.findChild("Resources")?.findChild("views")
                    if (moduleViewsDir != null && moduleViewsDir.exists() && moduleViewsDir.isValid) {
                        scanDirectory(moduleViewsDir, "modules.${module.name}")
                    }
                }
            }
        }
    }

    private fun scanDirectory(directory: VirtualFile, prefix: String) {
        try {
            if (!directory.isValid || !directory.exists()) {
                return
            }
            
            directory.children.forEach { child ->
                when {
                    child.isDirectory -> {
                        val newPrefix = if (prefix.isEmpty())
                            child.name
                        else
                            "$prefix.${child.name}"
                        scanDirectory(child, newPrefix)
                    }
                    child.extension == "php" || child.name.endsWith(".blade.php") -> {
                        // Normalize name to handle both .blade.php and .php extensions
                        val nameWithoutExtension = when {
                            child.name.endsWith(".blade.php") -> child.name.removeSuffix(".blade.php")
                            else -> child.nameWithoutExtension
                        }

                        val viewName = if (prefix.isEmpty())
                            nameWithoutExtension
                        else
                            "$prefix.$nameWithoutExtension"

                        val relativePath = if (prefix.isEmpty())
                            nameWithoutExtension + "." + child.extension
                        else
                            prefix.replace('.', '/') + "/$nameWithoutExtension." + child.extension

                        // Store multiple variations of the path to increase matching success
                        viewCache[viewName] = ViewInfo(
                            name = viewName,
                            path = relativePath,
                            extension = child.extension ?: "php"
                        )

                        // Also store the path with slashes instead of dots for alternative lookups
                        val slashViewName = viewName.replace('.', '/')
                        if (slashViewName != viewName) {
                            viewCache[slashViewName] = viewCache[viewName]!!
                        }

                        // Also store with extension for direct matches
                        viewCache["$viewName.${child.extension}"] = viewCache[viewName]!!
                    }
                }
            }
        } catch (e: Exception) {
            // Handle potential exceptions gracefully
        }
    }
}