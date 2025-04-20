package com.laravel.plugin.services
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.table.JBTable
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import java.awt.Point
import java.util.concurrent.ConcurrentHashMap
import javax.swing.table.AbstractTableModel

@com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)
class TranslationService(private val project: Project) {
    private val translationCache = ConcurrentHashMap<String, TranslationInfo>()
    init {
        // Scan translations when service is initialized
        scanTranslations()
    }

    data class TranslationInfo(
        val key: String,
        val locale: String,
        val file: VirtualFile,
        val value: String,
        val element: PsiElement
    ) {
        companion object {
            // Empty companion object to allow access to the class via TranslationService.TranslationInfo
        }
    }

    fun getAllTranslations(): List<String> {
        // Always scan translations to ensure we have the latest data
        if (translationCache.isEmpty()) {
            scanTranslations()
        }
        return translationCache.keys.toList().sorted()
    }

    fun getTranslationValue(translationKey: String): String {
        if (translationCache.isEmpty()) {
            scanTranslations()
        }
        return translationCache[translationKey]?.value ?: translationKey
    }

    fun getTranslationLocale(translationKey: String): String {
        if (translationCache.isEmpty()) {
            scanTranslations()
        }
        return translationCache[translationKey]?.locale ?: "en"
    }

    fun isTranslation(text: String): Boolean {
        if (translationCache.isEmpty()) {
            scanTranslations()
        }
        return translationCache.containsKey(text)
    }

    fun findTranslationDeclaration(project: Project, translationKey: String): List<PsiElement> {
        return findTranslationDeclarations(project, translationKey)
    }

    fun scanTranslations() {
        translationCache.clear()

        // Get the project root directory
        val projectDir = project.guessProjectDir() ?: return

        // Look for the lang directory at different locations
        val langDirs = mutableListOf<VirtualFile>()

        // resources/lang (Laravel 5+)
        projectDir.findChild("resources")?.findChild("lang")?.let {
            if (it.exists() && it.isValid) {
                langDirs.add(it)
            }
        }

        // lang (Laravel 4.x)
        projectDir.findChild("lang")?.let {
            if (it.exists() && it.isValid) {
                langDirs.add(it)
            }
        }

        // app/lang (Very old Laravel)
        projectDir.findChild("app")?.findChild("lang")?.let {
            if (it.exists() && it.isValid) {
                langDirs.add(it)
            }
        }

        // vendor language files (Laravel 6+)
        projectDir.findChild("vendor")?.findChild("laravel")?.findChild("lang")?.let {
            if (it.exists() && it.isValid) {
                langDirs.add(it)
            }
        }

        // Check for Laravel 6+ module translations
        projectDir.findChild("Modules")?.let { modulesDir ->
            if (modulesDir.exists() && modulesDir.isValid) {
                modulesDir.children.forEach { moduleDir ->
                    if (moduleDir.isDirectory) {
                        moduleDir.findChild("Resources")?.findChild("lang")?.let {
                            if (it.exists() && it.isValid) {
                                langDirs.add(it)
                            }
                        }
                    }
                }
            }
        }

        // Lang publisher (Laravel Packages)
        projectDir.findChild("lang")?.findChild("vendor")?.let { vendorDir ->
            if (vendorDir.exists() && vendorDir.isValid) {
                vendorDir.children.forEach { packageDir ->
                    if (packageDir.isDirectory) {
                        langDirs.add(packageDir)
                    }
                }
            }
        }

        // Process all PHP files in lang directories and their subdirectories
        if (langDirs.isEmpty()) {
            // If no lang directories found, try to find translation files anywhere
            val scope = GlobalSearchScope.projectScope(project)
            FilenameIndex.getAllFilesByExt(project, "php", scope)
                .filter { it.path.contains("/lang/") || it.path.contains("\\lang\\") }
                .forEach { file ->
                    val locale = file.parent.name
                    val prefix = file.nameWithoutExtension
                    processTranslationFile(file, locale, prefix)
                }

            // Also look for JSON translations (Laravel 5.4+)
            FilenameIndex.getAllFilesByExt(project, "json", scope)
                .filter { (it.path.contains("/lang/") || it.path.contains("\\lang\\")) || it.path.contains("/translations/") }
                .forEach { file ->
                    val locale = file.nameWithoutExtension
                    processJsonTranslationFile(file, locale)
                }
        } else {
            // Process files from the found lang directories
            langDirs.forEach { langDir ->
                // Process locale directories
                langDir.children
                    .filter { it.isDirectory }
                    .forEach { localeDir ->
                        val locale = localeDir.name

                        // Process PHP files in locale directory
                        localeDir.children
                            .filter { it.extension == "php" }
                            .forEach { file ->
                                val prefix = file.nameWithoutExtension
                                processTranslationFile(file, locale, prefix)
                            }
                    }

                // Process JSON files directly in lang directory (for Laravel 5.4+)
                langDir.children
                    .filter { it.extension == "json" }
                    .forEach { file ->
                        val locale = file.nameWithoutExtension
                        processJsonTranslationFile(file, locale)
                    }

                // Process PHP files directly in lang directory (for some packages)
                langDir.children
                    .filter { it.extension == "php" }
                    .forEach { file ->
                        val prefix = file.nameWithoutExtension
                        processTranslationFile(file, "en", prefix) // Default to "en" when locale is not in path
                    }
            }
        }
    }

    private fun processJsonTranslationFile(file: VirtualFile, locale: String) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return

        try {
            // Simple parsing of JSON file content to find translation keys
            val content = psiFile.text
            val jsonKeyValuePattern = """"([^"]+)"\s*:\s*"([^"]+)"""".toRegex()

            jsonKeyValuePattern.findAll(content).forEach { match ->
                val key = match.groupValues[1]
                val value = match.groupValues[2]

                translationCache[key] = TranslationInfo(
                    key = key,
                    locale = locale,
                    file = file,
                    value = value,
                    element = psiFile
                )
            }
        } catch (e: Exception) {
            // Handle any parsing exceptions gracefully
        }
    }

    private fun processTranslationFile(file: VirtualFile, locale: String, prefix: String) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return

        try {
            psiFile.accept(object : PhpElementVisitor() {
                override fun visitPhpReturn(returnStatement: PhpReturn) {
                    super.visitPhpReturn(returnStatement)
                    val array = returnStatement.firstPsiChild as? ArrayCreationExpression ?: return
                    processArray(array, locale, prefix, file)
                }
            })
        } catch (e: Exception) {
            // Handle any exceptions gracefully
        }
    }

    class TranslationTableModel(
        private val translations: Map<String, TranslationInfo>
    ) : AbstractTableModel() {
        private val data = translations.values.toList()
        private val columns = arrayOf("Locale", "Value", "File")

        override fun getRowCount(): Int = data.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(row: Int, col: Int): Any {
            val info = data[row]
            return when (col) {
                0 -> info.locale
                1 -> info.value
                2 -> info.file.name
                else -> "" // Add an else branch for exhaustive when expression
            }
        }
    }

    fun showTranslationsPopup(
        project: Project,
        element: PsiElement,
        translations: Map<String, TranslationInfo>
    ) {
        val translationKey = (element as? StringLiteralExpression)?.contents ?: return
        val model = TranslationTableModel(translations)
        val table = JBTable(model).apply {
            setShowColumns(true)
            columnModel.apply {
                getColumn(0).preferredWidth = 50   // Locale column
                getColumn(1).preferredWidth = 200  // Value column
                getColumn(2).preferredWidth = 200  // File column
            }
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(table)
            .setTitle("Translations for '$translationKey'")
            .setItemChosenCallback(object : Runnable {
                override fun run() {
                    val selectedRow = table.selectedRow
                    if (selectedRow >= 0) {
                        val translationInfo = translations.values.toList()[selectedRow]
                        val fileEditorManager = FileEditorManager.getInstance(project)
                        val descriptor = OpenFileDescriptor(project, translationInfo.file, translationInfo.element.textOffset)
                        fileEditorManager.openTextEditor(descriptor, true)
                    }
                }
            })
            .createPopup()
            .show(RelativePoint(Point(0, 0)))
    }

    private fun processArray(
        array: ArrayCreationExpression,
        locale: String,
        prefix: String,
        file: VirtualFile,
        path: String = ""
    ) {
        array.children.forEach { child ->
            if (child is ArrayHashElement) {
                val key = child.key as? StringLiteralExpression ?: return@forEach
                val value = child.value

                val fullPath = if (path.isEmpty()) key.contents else "$path.${key.contents}"
                val translationKey = if (prefix.isEmpty()) fullPath else "$prefix.$fullPath"

                when (value) {
                    is StringLiteralExpression -> {
                        translationCache[translationKey] = TranslationInfo(
                            key = translationKey,
                            locale = locale,
                            file = file,
                            value = value.contents,
                            element = key
                        )
                    }
                    is ArrayCreationExpression -> {
                        processArray(value, locale, prefix, file, fullPath)
                    }
                    else -> {
                        // Handle any other possible value types
                        translationCache[translationKey] = TranslationInfo(
                            key = translationKey,
                            locale = locale,
                            file = file,
                            value = value?.text ?: "[complex value]",
                            element = key
                        )
                    }
                }
            }
        }
    }

    fun findTranslationDeclarations(project: Project, translationKey: String): List<PsiElement> {
        // Get the service for this project
        val service = project.getService(TranslationService::class.java)

        // Make sure the translations are scanned
        if (service.translationCache.isEmpty()) {
            service.scanTranslations()
        }

        // First check if we have this translation in the cache
        service.translationCache[translationKey]?.element?.let { element ->
            return listOf(element)
        }

        // If not in cache, try to find it in the project files
        // Split the translation key to get the namespace and actual key
        val parts = translationKey.split(".")
        val namespace = if (parts.size > 1) parts[0] else ""
        val key = if (parts.size > 1) parts.drop(1).joinToString(".") else translationKey

        val elements = mutableListOf<PsiElement>()

        // Get the project root directory
        val projectDir = project.guessProjectDir() ?: return emptyList()

        // Look for translations in all possible language directories
        val langDirs = mutableListOf<Pair<VirtualFile, String>>()

        // Standard Laravel 5+ path
        projectDir.findChild("resources")?.findChild("lang")?.let {
            if (it.exists()) langDirs.add(Pair(it, ""))
        }

        // Laravel 4.x path
        projectDir.findChild("lang")?.let {
            if (it.exists()) langDirs.add(Pair(it, ""))
        }

        // Old Laravel path
        projectDir.findChild("app")?.findChild("lang")?.let {
            if (it.exists()) langDirs.add(Pair(it, ""))
        }

        // Laravel 6+ vendor path
        projectDir.findChild("vendor")?.findChild("laravel")?.findChild("lang")?.let {
            if (it.exists()) langDirs.add(Pair(it, "vendor."))
        }

        // Laravel 6+ modules
        projectDir.findChild("Modules")?.children?.forEach { moduleDir ->
            if (moduleDir.isDirectory) {
                moduleDir.findChild("Resources")?.findChild("lang")?.let {
                    if (it.exists()) langDirs.add(Pair(it, "modules.${moduleDir.name}."))
                }
            }
        }

        // Check all language directories for the translation
        for ((langDir, prefix) in langDirs) {
            // Process all locale directories
            langDir.children.forEach { localeDir ->
                if (localeDir.isDirectory) {
                    // Check for translation file
                    val translationFile = if (namespace.isNotEmpty()) {
                        localeDir.findChild("$namespace.php")
                    } else {
                        // Try to locate by pattern if no namespace
                        localeDir.children.firstOrNull { it.nameWithoutExtension == namespace && it.extension == "php" }
                    }

                    if (translationFile != null) {
                        val psiFile = PsiManager.getInstance(project).findFile(translationFile)
                        psiFile?.accept(object : PsiRecursiveElementVisitor() {
                            override fun visitElement(element: PsiElement) {
                                super.visitElement(element)
                                if (element is StringLiteralExpression && element.contents == key) {
                                    // Verify it's actually a key in the translation array
                                    if (element.parent is ArrayHashElement && (element.parent as ArrayHashElement).key == element) {
                                        elements.add(element)
                                    }
                                }
                            }
                        })
                    }
                } else if (localeDir.extension == "json") {
                    // Process JSON translation files
                    val locale = localeDir.nameWithoutExtension
                    val psiFile = PsiManager.getInstance(project).findFile(localeDir)

                    if (psiFile != null) {
                        val content = psiFile.text
                        val jsonKeyPattern = """"${translationKey.replace(".", "\\.")}"\s*:""".toRegex()

                        if (jsonKeyPattern.containsMatchIn(content)) {
                            elements.add(psiFile)
                        }
                    }
                }
            }
        }

        // If no elements found but we have a cached entry, add that as a fallback
        if (elements.isEmpty()) {
            service.translationCache[translationKey]?.element?.let {
                elements.add(it)
            }
        }

        return elements
    }
}