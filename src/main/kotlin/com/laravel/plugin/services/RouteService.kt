package com.laravel.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.php.lang.psi.elements.*
import org.apache.commons.collections.Closure
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class RouteService(private val project: Project) {
    private val routeCache = ConcurrentHashMap<String, RouteInfo>()
    private val routePathCache = ConcurrentHashMap<String, RouteInfo>()
    private val routeUsages = ConcurrentHashMap<String, MutableList<RouteUsage>>()
    private val routeDomains = ConcurrentHashMap<String, RouteDomainInfo>()
    
    init {
        // Initialize route scanning when the service is created
        scanRoutes()
        scanForRouteUsages()
    }

    data class RouteInfo(
        val name: String?,
        val method: String,
        val path: String,
        val controller: String?,
        val middleware: List<String>,
        val element: PsiElement,
        val parameters: List<RouteParameter> = emptyList(),
        val groupPrefix: String = "",
        val domain: String? = null
    )

    data class RouteParameter(
        val name: String,
        val type: String?,
        val optional: Boolean,
        val pattern: String?
    )

    data class RouteUsage(
        val file: VirtualFile,
        val lineNumber: Int,
        val element: PsiElement,
        val usageType: UsageType
    )

    data class RouteDomainInfo(
        val domain: String,
        val element: PsiElement,
        val parameters: List<DomainParameter>,
        val parentGroup: PsiElement?,
        val middleware: List<String>,
        val isActive: Boolean = true
    )

    data class DomainParameter(
        val name: String,
        val optional: Boolean = false,
        val pattern: String? = null,
        val defaultValue: String? = null
    )

    enum class UsageType {
        ROUTE_NAME,
        ROUTE_PATH,
        ROUTE_ACTION,
        URL_PATH,
    }

    private class RouteGroupContext {
        var prefix: String = ""
        var domain: String? = null
        var namespace: String? = null
        var namePrefix: String = ""
        var middleware: MutableList<String> = mutableListOf()

        fun copy(): RouteGroupContext {
            return RouteGroupContext().apply {
                prefix = this@RouteGroupContext.prefix
                domain = this@RouteGroupContext.domain
                namespace = this@RouteGroupContext.namespace
                namePrefix = this@RouteGroupContext.namePrefix
                middleware = this@RouteGroupContext.middleware.toMutableList()
            }
        }
    }

    fun getAllRoutes(): List<String> {
        // Always scan routes to ensure we have the latest data when the project is loaded
        scanRoutes() 
        scanForRouteUsages()
        return (routeCache.keys + routePathCache.values.map { it.path }).distinct()
    }

    fun getRouteMethod(routeIdentifier: String): String {
        // Ensure cache is populated
        if (routeCache.isEmpty()) {
            scanRoutes()
        }
        return routeCache[routeIdentifier]?.method
            ?: routePathCache[routeIdentifier]?.method
            ?: "GET"
    }

    fun getRoutePath(routeIdentifier: String): String {
        // Ensure cache is populated
        if (routeCache.isEmpty()) {
            scanRoutes()
        }
        return routeCache[routeIdentifier]?.path
            ?: routePathCache[routeIdentifier]?.path
            ?: routeIdentifier
    }

    fun getRouteUsages(routeIdentifier: String): List<RouteUsage> {
        // Ensure cache is populated
        if (routeUsages.isEmpty()) {
            scanForRouteUsages()
        }
        
        val usages = mutableListOf<RouteUsage>()
        usages.addAll(routeUsages[routeIdentifier] ?: emptyList())

        routePathCache[routeIdentifier]?.let { routeInfo ->
            usages.addAll(routeUsages[routeInfo.path] ?: emptyList())
        }
        
        routeCache[routeIdentifier]?.let { routeInfo ->
            if (routeInfo.name != null && routeInfo.name != routeIdentifier) {
                usages.addAll(routeUsages[routeInfo.name] ?: emptyList())
            }
            usages.addAll(routeUsages[routeInfo.path] ?: emptyList())
        }

        return usages
    }

    fun isRoute(text: String): Boolean {
        // Ensure cache is populated
        if (routeCache.isEmpty()) {
            scanRoutes()
        }
        return routeCache.containsKey(text) || routePathCache.containsKey(text)
    }

    fun findRouteDeclaration(routeName: String): List<PsiElement> {
        // Ensure cache is populated
        if (routeCache.isEmpty()) {
            scanRoutes()
        }
        
        val results = mutableListOf<PsiElement>()
        routeCache[routeName]?.element?.let { results.add(it) }
        routePathCache[routeName]?.element?.let { results.add(it) }
        return results
    }

    fun scanRoutes() {
        // Clear existing caches
        routeCache.clear()
        routePathCache.clear()
        routeDomains.clear()

        // Get all route files
        val routeFiles = findRouteFiles()

        // Process each route file
        routeFiles.forEach { file ->
            processRouteFile(file)
        }
    }

    private fun findRouteFiles(): Collection<VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val routeFiles = mutableListOf<VirtualFile>()

        // Find the routes directory
        val routesDir = project.guessProjectDir()?.findChild("routes")

        if (routesDir != null && routesDir.isDirectory) {
            // Standard route files to check
            val standardRouteFiles = listOf(
                "web.php",
                "api.php",
                "channels.php",
                "console.php",
                "artisan.php"
            )

            // Add standard route files if they exist
            standardRouteFiles.forEach { filename ->
                routesDir.findChild(filename)?.let { file ->
                    routeFiles.add(file)
                }
            }

            // Add any additional PHP files in the routes directory
            routesDir.children.forEach { file ->
                if (file.extension == "php" && !standardRouteFiles.contains(file.name)) {
                    routeFiles.add(file)
                }
            }
        }

        return routeFiles
    }

    private fun processRouteFile(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        val groupContext = RouteGroupContext()

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                when (element) {
                    is MethodReference -> {
                        when (element.name?.lowercase()) {
                            "get", "post", "put", "patch", "delete", "options", "any", "match" -> {
                                if (isRouteDefinition(element)) {
                                    processRouteDefinition(element, groupContext)
                                }
                            }
                            "group" -> processRouteGroup(element, groupContext)
                            "domain" -> processDomainGroup(element, groupContext)
                            "name" -> processNamePrefixGroup(element, groupContext)
                            "middleware" -> processMiddlewareGroup(element, groupContext)
                        }
                    }
                }
            }
        })
    }

    fun scanForRouteUsages() {
        // Clear existing usages
        routeUsages.clear()
        
        val scope = GlobalSearchScope.projectScope(project)

        // Scan PHP files
        FilenameIndex.getAllFilesByExt(project, "php", scope)
            .filter { !it.path.endsWith(".blade.php") }
            .forEach { file ->
                scanFileForRouteUsages(file)
            }

        // Scan Blade files
        scanBladeFiles()

        // Scan JavaScript and Vue files
        scanJavaScriptFiles()
    }

    private fun scanFileForRouteUsages(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                when (element) {
                    is StringLiteralExpression -> {
                        val content = element.contents

                        if (routeCache.containsKey(content)) {
                            addRouteUsage(content, file, element, UsageType.ROUTE_NAME)
                        }

                        routePathCache[content]?.let { routeInfo ->
                            if (routeInfo.name != null) {
                                addRouteUsage(routeInfo.name, file, element, UsageType.ROUTE_PATH)
                            } else {
                                addRouteUsage(routeInfo.path, file, element, UsageType.ROUTE_PATH)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun addRouteUsage(routeIdentifier: String, file: VirtualFile, element: PsiElement, usageType: UsageType) {
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
        val lineNumber = document?.getLineNumber(element.textOffset) ?: 0

        val usage = RouteUsage(
            file = file,
            lineNumber = lineNumber,
            element = element,
            usageType = usageType
        )

        routeUsages.computeIfAbsent(routeIdentifier) { mutableListOf() }.add(usage)
    }

    private fun scanBladeFiles() {
        val scope = GlobalSearchScope.projectScope(project)
        FilenameIndex.getAllFilesByExt(project, "blade.php", scope).forEach { file ->
            scanBladeFileForRouteUsages(file)
        }
    }

    private fun scanBladeFileForRouteUsages(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        val content = psiFile.text

        val patterns = listOf(
            Regex("""\{\{\s*route\(['"]([^'"]+)['"]\s*(?:}|,)"""),
            Regex("""@route\(['"]([^'"]+)['"]\s*(?:\)|,)"""),
            Regex("""\{\{\s*url\(['"]([^'"]+)['"]\s*(?:}|,)"""),
            Regex("""\{\{\s*action\(['"]([^'"]+)['"]\s*(?:}|,)"""),
            Regex("""href\s*=\s*["']\{\{\s*route\(['"]([^'"]+)['"]\s*(?:}|,)"""),
            Regex("""action\s*=\s*["']\{\{\s*route\(['"]([^'"]+)['"]\s*(?:}|,)"""),
            Regex("""wire:navigate\.href\s*=\s*["']\{\{\s*route\(['"]([^'"]+)['"]\s*(?:}|,)""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { matchResult ->
                val routeIdentifier = matchResult.groupValues[1]

                if (routeCache.containsKey(routeIdentifier)) {
                    addBladeRouteUsage(routeIdentifier, file, matchResult.range.first, UsageType.ROUTE_NAME)
                } else if (routePathCache.containsKey(routeIdentifier)) {
                    addBladeRouteUsage(routeIdentifier, file, matchResult.range.first, UsageType.ROUTE_PATH)
                }
            }
        }
    }

    private fun addBladeRouteUsage(routeIdentifier: String, file: VirtualFile, offset: Int, usageType: UsageType) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
        val lineNumber = document.getLineNumber(offset)

        val usage = RouteUsage(
            file = file,
            lineNumber = lineNumber,
            element = psiFile,
            usageType = usageType
        )

        routeUsages.computeIfAbsent(routeIdentifier) { mutableListOf() }.add(usage)
    }

    private fun findMatchMethods(element: MethodReference): String {
        val methodsArg = element.parameters.firstOrNull() as? ArrayCreationExpression
        if (methodsArg != null) {
            val methods = methodsArg.children
                .filterIsInstance<StringLiteralExpression>()
                .map { it.contents.uppercase() }

            return if (methods.isNotEmpty()) {
                methods.joinToString("|")
            } else "GET"
        }

        return "GET"
    }

    private fun findRoutePath(element: MethodReference): String {
        val pathArg = when (element.name?.lowercase()) {
            "match" -> {
                element.parameters.getOrNull(1) as? StringLiteralExpression
            }
            "any" -> {
                element.parameters.firstOrNull() as? StringLiteralExpression
            }
            else -> {
                element.parameters.firstOrNull() as? StringLiteralExpression
            }
        }

        var path = pathArg?.contents ?: ""

        if (pathArg == null) {
            val arrayArg = when (element.name?.lowercase()) {
                "match" -> element.parameters.getOrNull(1)
                else -> element.parameters.firstOrNull()
            } as? ArrayCreationExpression

            arrayArg?.children?.forEach { child ->
                if (child is ArrayHashElement) {
                    val key = child.key as? StringLiteralExpression
                    if (key?.contents == "uri" || key?.contents == "path") {
                        val value = child.value as? StringLiteralExpression
                        path = value?.contents ?: ""
                    }
                }
            }
        }

        var current: PsiElement = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "domain") {
                val domainArg = current.parameters.firstOrNull() as? StringLiteralExpression
                val domain = domainArg?.contents
                if (domain != null && domain.isNotEmpty()) {
                    storeDomainForRoute(element, domain)
                }
                break
            }
        }

        val prefix = findRoutePrefix(element)
        if (prefix.isNotEmpty()) {
            path = "${prefix.trimEnd('/')}/${path.trimStart('/')}"
        }

        return normalizePath(path)
    }

    private fun findRoutePrefix(element: PsiElement): String {
        var prefix = ""
        var current: PsiElement? = element

        while (current != null) {
            if (current is MethodReference && current.name == "group") {
                val groupArg = current.parameters.firstOrNull() as? ArrayCreationExpression
                if (groupArg != null) {
                    groupArg.children.forEach { child ->
                        if (child is ArrayHashElement) {
                            val key = child.key as? StringLiteralExpression
                            if (key?.contents == "prefix") {
                                val value = child.value as? StringLiteralExpression
                                val newPrefix = value?.contents ?: ""
                                prefix = if (prefix.isEmpty()) {
                                    newPrefix
                                } else {
                                    "${newPrefix.trimEnd('/')}/${prefix.trimStart('/')}"
                                }
                            }
                        }
                    }
                }
            }
            current = current.parent
        }

        return prefix
    }

    private fun findRouteName(element: MethodReference): String? {
        var current: PsiElement = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "name") {
                val nameArg = current.parameters.firstOrNull() as? StringLiteralExpression
                return nameArg?.contents
            }
        }

        // Check for name in array parameter
        val routeParams = element.parameters.getOrNull(1) as? ArrayCreationExpression
        routeParams?.children?.forEach { child ->
            if (child is ArrayHashElement) {
                val key = child.key as? StringLiteralExpression
                if (key?.contents == "as") {
                    val value = child.value as? StringLiteralExpression
                    return value?.contents
                }
            }
        }

        return null
    }

    private fun findRouteController(element: MethodReference, namespace: String? = null): String? {
        val actionArg = when (element.name?.lowercase()) {
            "match" -> element.parameters.getOrNull(2)
            else -> element.parameters.getOrNull(1)
        }

        return when {
            actionArg is ArrayCreationExpression -> {
                val elements = actionArg.children
                if (elements.size >= 2) {
                    val controller = (elements[0] as? StringLiteralExpression)?.contents
                    val method = (elements[1] as? StringLiteralExpression)?.contents
                    if (controller != null && method != null) {
                        val fullController = if (namespace != null && !controller.startsWith("\\")) {
                            "$namespace\\$controller"
                        } else {
                            controller
                        }
                        "$fullController@$method"
                    } else null
                } else null
            }

            actionArg is StringLiteralExpression -> {
                val content = actionArg.contents
                if (namespace != null && !content.startsWith("\\")) {
                    "$namespace\\$content"
                } else {
                    content
                }
            }

            actionArg is ClassConstantReference -> {
                val className = actionArg.classReference?.text
                if (className != null) {
                    if (namespace != null && !className.startsWith("\\")) {
                        "$namespace\\$className"
                    } else {
                        className
                    }
                } else null
            }

            actionArg is Closure -> "Closure"

            else -> null
        }
    }

    private fun findRouteMiddleware(element: MethodReference): List<String> {
        val middleware = mutableListOf<String>()

        // Check direct middleware on route
        var current: PsiElement = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "middleware") {
                current.parameters.firstOrNull()?.let { middlewareArg ->
                    when (middlewareArg) {
                        is ArrayCreationExpression -> {
                            middleware.addAll(
                                middlewareArg.children
                                    .filterIsInstance<StringLiteralExpression>()
                                    .map { it.contents }
                            )
                        }
                        is StringLiteralExpression -> {
                            middleware.add(middlewareArg.contents)
                        }

                        else -> {}
                    }
                }
            }
        }

        // Check group middleware
        current = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "group") {
                val groupArg = current.parameters.firstOrNull() as? ArrayCreationExpression
                groupArg?.children?.forEach { child ->
                    if (child is ArrayHashElement) {
                        val key = child.key as? StringLiteralExpression
                        if (key?.contents == "middleware") {
                            when (val value = child.value) {
                                is StringLiteralExpression -> {
                                    middleware.add(value.contents)
                                }
                                is ArrayCreationExpression -> {
                                    middleware.addAll(
                                        value.children
                                            .filterIsInstance<StringLiteralExpression>()
                                            .map { it.contents }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        return middleware.distinct()
    }

    private fun normalizePath(path: String): String {
        var normalized = path.trim('/')

        // Replace multiple consecutive slashes with a single slash
        normalized = normalized.replace(Regex("/+"), "/")

        // Handle empty path
        if (normalized.isEmpty()) {
            return "/"
        }

        // Ensure path starts with /
        return "/$normalized"
    }

    private fun storeDomainForRoute(element: PsiElement, domain: String) {
        // Handle empty domain
        if (domain.isBlank()) return

        // Create unique key for the element
        val elementKey = when (element) {
            is MethodReference -> {
                val className = element.classReference?.text ?: "UnknownClass"
                val methodName = element.name ?: "UnknownMethod"
                val offset = element.textOffset
                "$className::$methodName@$offset"
            }
            is FunctionReference -> {
                val functionName = element.name ?: "UnknownFunction"
                val offset = element.textOffset
                "function::$functionName@$offset"
            }
            else -> {
                "${element.javaClass.simpleName}@${element.textOffset}"
            }
        }

        // Store the domain information
        routeDomains[elementKey] = RouteDomainInfo(
            domain = normalizeDomain(domain),
            element = element,
            parameters = extractDomainParameters(domain),
            parentGroup = element.parent,
            middleware = findRouteMiddleware(element as? MethodReference ?: return),
            isActive = true
        )

        // Update route caches
        updateRouteCachesWithDomain(elementKey, domain)
    }

    private fun normalizeDomain(domain: String): String {
        return domain.replace(Regex("^https?://"), "").trimEnd('/')
    }

    private fun extractDomainParameters(domain: String): List<DomainParameter> {
        val parameters = mutableListOf<DomainParameter>()
        val paramPattern = """\{([^{}]+?)(\?)?}""".toRegex()

        paramPattern.findAll(domain).forEach { match ->
            val (name, optional) = match.destructured
            parameters.add(DomainParameter(
                name = name.trim(),
                optional = optional == "?",
                pattern = name.split(':').getOrNull(1),
                defaultValue = name.split(':').getOrNull(2)
            ))
        }

        return parameters
    }

    private fun updateRouteCachesWithDomain(elementKey: String, domain: String) {
        routeCache.forEach { (key, routeInfo) ->
            if (createElementKey(routeInfo.element) == elementKey) {
                routeCache[key] = routeInfo.copy(domain = domain)
            }
        }

        routePathCache.forEach { (key, routeInfo) ->
            if (createElementKey(routeInfo.element) == elementKey) {
                routePathCache[key] = routeInfo.copy(domain = domain)
            }
        }
    }

    private fun createElementKey(element: PsiElement): String {
        return when (element) {
            is MethodReference -> {
                val className = element.classReference?.text ?: "UnknownClass"
                val methodName = element.name ?: "UnknownMethod"
                val offset = element.textOffset
                "$className::$methodName@$offset"
            }
            is FunctionReference -> {
                val functionName = element.name ?: "UnknownFunction"
                val offset = element.textOffset
                "function::$functionName@$offset"
            }
            else -> {
                "${element.javaClass.simpleName}@${element.textOffset}"
            }
        }
    }

    private fun processRouteGroup(element: MethodReference, context: RouteGroupContext) {
        val groupArg = element.parameters.firstOrNull() as? ArrayCreationExpression ?: return
        val newContext = context.copy()

        // Process group attributes
        groupArg.children.forEach { child ->
            if (child is ArrayHashElement) {
                val key = (child.key as? StringLiteralExpression)?.contents
                val value = child.value
                when (key) {
                    "prefix" -> {
                        val prefix = (value as? StringLiteralExpression)?.contents
                        if (prefix != null) {
                            newContext.prefix = buildFullPath(prefix, context.prefix)
                        }
                    }
                    "namespace" -> {
                        val namespace = (value as? StringLiteralExpression)?.contents
                        if (namespace != null) {
                            newContext.namespace = if (context.namespace != null) {
                                "${context.namespace}\\$namespace"
                            } else {
                                namespace
                            }
                        }
                    }
                    "middleware" -> {
                        when (value) {
                            is StringLiteralExpression -> newContext.middleware.add(value.contents)
                            is ArrayCreationExpression -> {
                                value.children
                                    .filterIsInstance<StringLiteralExpression>()
                                    .forEach { newContext.middleware.add(it.contents) }
                            }
                        }
                    }
                    "as" -> {
                        val namePrefix = (value as? StringLiteralExpression)?.contents
                        if (namePrefix != null) {
                            newContext.namePrefix = buildFullName(namePrefix, context.namePrefix) ?: ""
                        }
                    }
                    "domain" -> {
                        val domain = (value as? StringLiteralExpression)?.contents
                        if (domain != null) {
                            newContext.domain = domain
                            storeDomainForRoute(element, domain)
                        }
                    }
                }
            }
        }

        // Process group callback
        element.lastChild?.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element is MethodReference) {
                    when (element.name?.lowercase()) {
                        "get", "post", "put", "patch", "delete", "options", "any", "match" -> {
                            if (isRouteDefinition(element)) {
                                processRouteDefinition(element, newContext)
                            }
                        }
                        "group" -> processRouteGroup(element, newContext)
                    }
                }
            }
        })
    }

    private fun isRouteDefinition(element: MethodReference): Boolean {
        return element.classReference?.text == "Route"
    }

    private fun buildFullPath(path: String, prefix: String): String {
        val normalizedPath = path.trim('/')
        val normalizedPrefix = prefix.trim('/')

        return when {
            normalizedPrefix.isEmpty() -> "/$normalizedPath"
            normalizedPath.isEmpty() -> "/$normalizedPrefix"
            else -> "/$normalizedPrefix/$normalizedPath"
        }
    }

    private fun buildFullName(name: String?, prefix: String): String? {
        if (name == null) return null

        val normalizedName = name.trim('.')
        val normalizedPrefix = prefix.trim('.')

        return when {
            normalizedPrefix.isEmpty() -> normalizedName
            normalizedName.isEmpty() -> normalizedPrefix
            else -> "$normalizedPrefix.$normalizedName"
        }
    }

    private fun processRouteDefinition(element: MethodReference, context: RouteGroupContext) {
        val method = when (element.name?.lowercase()) {
            "match" -> findMatchMethods(element)
            "any" -> "ANY"
            else -> element.name?.uppercase() ?: "GET"
        }

        val path = findRoutePath(element)
        val name = findRouteName(element)
        val controller = findRouteController(element, context.namespace)
        val middleware = (context.middleware + findRouteMiddleware(element)).distinct()

        // Apply group context
        val fullPath = buildFullPath(path, context.prefix)
        val fullName = buildFullName(name, context.namePrefix)

        val routeInfo = RouteInfo(
            name = fullName,
            method = method,
            path = fullPath,
            controller = controller,
            middleware = middleware,
            element = element,
            domain = context.domain
        )

        // Cache the route information
        if (fullName != null) {
            routeCache[fullName] = routeInfo
        }
        if (fullPath.isNotEmpty()) {
            routePathCache[fullPath] = routeInfo
        }
    }

    private fun scanJavaScriptFiles() {
        val scope = GlobalSearchScope.projectScope(project)

        // Scan .js files
        FilenameIndex.getAllFilesByExt(project, "js", scope).forEach { file ->
            scanJsFileForRouteUsages(file)
        }

        // Scan .vue files
        FilenameIndex.getAllFilesByExt(project, "vue", scope).forEach { file ->
            scanVueFileForRouteUsages(file)
        }
    }

    private fun scanJsFileForRouteUsages(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        val content = psiFile.text

        val patterns = listOf(
            // Laravel route helper
            Regex("""route\(['"]([^'"]+)['"]\s*[),]"""),

            // Vue Router push via instance
            Regex(Regex.escape("this.\$router.push(s*{s*name:s*['\"]([^'\"]+)['\"]s*}")),

            // Vue Router push via variable
            Regex("""const\s+router\s*=\s*useRouter\(\).*?router\.push\(\s*\{\s*name:\s*['"]([^'"]+)['"]\s*}""", RegexOption.DOT_MATCHES_ALL),

            // Vue Router via composable
            Regex("""useRouter\(\)\.push\(\s*\{\s*name:\s*['"]([^'"]+)['"]\s*}"""),

            // Inertia visit via instance
            Regex(Regex.escape("this.\$inertia.visit(s*['\"]([^'\"]+)['\"]s*[),])")),

            // Inertia visit via global
            Regex("""Inertia\.visit\(\s*['"]([^'"]+)['"]\s*[),]"""),

            // Inertia via router
            Regex("""router\.visit\(\s*['"]([^'"]+)['"]\s*[),]"""),

            // Using destructured router
            Regex("""const\s*\{\s*visit\s*}\s*=\s*router.*?visit\(\s*['"]([^'"]+)['"]\s*[),]""", RegexOption.DOT_MATCHES_ALL)
        )

        scanWithPatterns(patterns, content, file)
    }

    private fun scanVueFileForRouteUsages(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        val content = psiFile.text

        val patterns = listOf(
            // Template router-link
            Regex(""":to="\{\s*name:\s*['"]([^'"]+)['"]\s*}"""),
            Regex("""to="\{\s*name:\s*['"]([^'"]+)['"]\s*}"""),

            // Setup composition navigation
            Regex("""const\s+router\s*=\s*useRouter\(\).*?router\.push\(\s*\{\s*name:\s*['"]([^'"]+)['"]\s*}""", RegexOption.DOT_MATCHES_ALL),

            // Options API navigation
            Regex(Regex.escape("this.\$router.push(s*{s*name:s*['\"]([^'\"]+)['\"]s*}")),

            // Route helper in template
            Regex("""route\(['"]([^'"]+)['"]\s*[),]"""),

            // Inertia Link component
            Regex("""<Link\s+:href="route\(['"]([^'"]+)['"]\s*[),]"""),
            Regex("""<Link\s+href="([^'"]+)""""),

            // Inertia navigation in setup
            Regex("""const\s*\{\s*visit\s*}\s*=\s*useForm\(.*?visit\(\s*['"]([^'"]+)['"]\s*[),]""", RegexOption.DOT_MATCHES_ALL),

            // Inertia navigation in options API
            Regex(Regex.escape("this.\$inertia.visit(s*['\"]([^'\"]+)['\"]s*[),])")),

            // Method definitions
            Regex("""methods:\s*\{[^}]*?['"]([^'"]+)['"]\s*[),]""", RegexOption.DOT_MATCHES_ALL),

            // Computed properties
            Regex("""computed:\s*\{[^}]*?['"]([^'"]+)['"]\s*[),]""", RegexOption.DOT_MATCHES_ALL),

            // Script setup router usage
            Regex("""const\s+router\s*=\s*useRouter\(\).*?name:\s*['"]([^'"]+)['"]\s*}""", RegexOption.DOT_MATCHES_ALL)
        )

        scanWithPatterns(patterns, content, file)
    }

    private fun scanWithPatterns(patterns: List<Regex>, content: String, file: VirtualFile) {
        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { matchResult ->
                val routeIdentifier = matchResult.groupValues[1]

                // Check both route name and path caches
                if (routeCache.containsKey(routeIdentifier)) {
                    addJsRouteUsage(routeIdentifier, file, matchResult.range.first, UsageType.ROUTE_NAME)
                } else if (routePathCache.containsKey(routeIdentifier)) {
                    addJsRouteUsage(routeIdentifier, file, matchResult.range.first, UsageType.ROUTE_PATH)
                }
            }
        }
    }

    private fun addJsRouteUsage(routeIdentifier: String, file: VirtualFile, offset: Int, usageType: UsageType) {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

        val lineNumber = document.getLineNumber(offset)

        val usage = RouteUsage(
            file = file,
            lineNumber = lineNumber,
            element = psiFile,
            usageType = usageType
        )

        routeUsages.computeIfAbsent(routeIdentifier) { mutableListOf() }.add(usage)
    }

    private fun processDomainGroup(element: MethodReference, context: RouteGroupContext) {
        // Get domain from first parameter
        val domainArg = element.parameters.firstOrNull() as? StringLiteralExpression
        if (domainArg != null) {
            val newContext = context.copy()
            newContext.domain = domainArg.contents

            // Store domain information
            storeDomainForRoute(element, domainArg.contents)

            // Process group callback
            element.lastChild?.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    if (element is MethodReference) {
                        when (element.name?.lowercase()) {
                            "get", "post", "put", "patch", "delete", "options", "any", "match" -> {
                                if (isRouteDefinition(element)) {
                                    processRouteDefinition(element, newContext)
                                }
                            }
                            "group" -> processRouteGroup(element, newContext)
                            "middleware" -> processMiddlewareGroup(element, newContext)
                            "name" -> processNamePrefixGroup(element, newContext)
                        }
                    }
                }
            })
        }
    }

    private fun processNamePrefixGroup(element: MethodReference, context: RouteGroupContext) {
        // Get name prefix from first parameter
        val nameArg = element.parameters.firstOrNull() as? StringLiteralExpression
        if (nameArg != null) {
            val newContext = context.copy()
            val namePrefix = nameArg.contents.trim('.')

            // Build full name prefix
            newContext.namePrefix = if (context.namePrefix.isEmpty()) {
                namePrefix
            } else {
                "${context.namePrefix}.$namePrefix"
            }

            // Process group callback
            element.lastChild?.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    if (element is MethodReference) {
                        when (element.name?.lowercase()) {
                            "get", "post", "put", "patch", "delete", "options", "any", "match" -> {
                                if (isRouteDefinition(element)) {
                                    processRouteDefinition(element, newContext)
                                }
                            }
                            "group" -> processRouteGroup(element, newContext)
                            "domain" -> processDomainGroup(element, newContext)
                            "middleware" -> processMiddlewareGroup(element, newContext)
                        }
                    }
                }
            })
        }
    }

    private fun processMiddlewareGroup(element: MethodReference, context: RouteGroupContext) {
        // Get middleware from first parameter
        val middlewareArg = element.parameters.firstOrNull()
        if (middlewareArg != null) {
            val newContext = context.copy()

            // Process middleware parameter
            when (middlewareArg) {
                is StringLiteralExpression -> {
                    // Single middleware
                    newContext.middleware.add(middlewareArg.contents)
                }
                is ArrayCreationExpression -> {
                    // Array of middleware
                    middlewareArg.children
                        .filterIsInstance<StringLiteralExpression>()
                        .mapNotNull { it.contents }
                        .forEach { newContext.middleware.add(it) }
                }
            }

            // Process group callback
            element.lastChild?.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    if (element is MethodReference) {
                        when (element.name?.lowercase()) {
                            "get", "post", "put", "patch", "delete", "options", "any", "match" -> {
                                if (isRouteDefinition(element)) {
                                    processRouteDefinition(element, newContext)
                                }
                            }
                            "group" -> processRouteGroup(element, newContext)
                            "domain" -> processDomainGroup(element, newContext)
                            "name" -> processNamePrefixGroup(element, newContext)
                        }
                    }
                }
            })
        }
    }

    fun getFullRouteDefinition(target: String): RouteDefinition? {
        // Try to find route by name first, then by path
        val routeInfo = routeCache[target] ?: routePathCache[target] ?: return null

        // Get all route parameters
        val parameters = getRouteParameters(routeInfo.path)

        // Get middleware with source information
        val middlewareWithSource = findMiddlewareSourceInfo(routeInfo.element)

        // Get route domain if exists
        val domain = routeInfo.domain

        // Build the full definition
        return RouteDefinition(
            name = routeInfo.name,
            method = routeInfo.method,
            uri = routeInfo.path,
            action = buildActionDefinition(routeInfo),
            middleware = middlewareWithSource,
            parameters = parameters,
            domain = domain,
            prefix = routeInfo.groupPrefix,
            whereConstraints = findWhereConstraints(routeInfo.element)
        )
    }

    data class RouteDefinition(
        val name: String?,
        val method: String,
        val uri: String,
        val action: ActionDefinition,
        val middleware: List<MiddlewareDefinition>,
        val parameters: List<RouteParameter>,
        val domain: String?,
        val prefix: String,
        val whereConstraints: Map<String, String>
    )

    data class ActionDefinition(
        val controller: String?,
        val method: String?,
        val namespace: String?,
        val isClosure: Boolean
    )

    data class MiddlewareDefinition(
        val name: String,
        val parameters: List<String>,
        val source: MiddlewareSource
    )

    enum class MiddlewareSource {
        ROUTE,
        GROUP,
        GLOBAL
    }

    private fun buildActionDefinition(routeInfo: RouteInfo): ActionDefinition {
        // Parse controller@method format
        val controllerParts = routeInfo.controller?.split("@") ?: emptyList()
        val namespace = controllerParts.firstOrNull()?.substringBeforeLast("\\")
        val controller = controllerParts.firstOrNull()?.substringAfterLast("\\")
        val method = controllerParts.getOrNull(1)

        return ActionDefinition(
            controller = controller,
            method = method,
            namespace = namespace,
            isClosure = routeInfo.controller == "Closure"
        )
    }

    private fun findMiddlewareSourceInfo(element: PsiElement): List<MiddlewareDefinition> {
        val middlewareList = mutableListOf<MiddlewareDefinition>()

        // Check for direct middleware on route
        var current: PsiElement = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "middleware") {
                processMiddlewareElement(current, MiddlewareSource.ROUTE, middlewareList)
            }
        }

        // Check for group middleware
        current = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "group") {
                val groupArg = current.parameters.firstOrNull() as? ArrayCreationExpression
                groupArg?.let { processGroupMiddleware(it, middlewareList) }
            }
        }

        return middlewareList
    }

    private fun processMiddlewareElement(
        element: MethodReference,
        source: MiddlewareSource,
        middlewareList: MutableList<MiddlewareDefinition>
    ) {
        element.parameters.firstOrNull()?.let { middlewareArg ->
            when (middlewareArg) {
                is StringLiteralExpression -> {
                    middlewareList.add(
                        MiddlewareDefinition(
                            name = middlewareArg.contents,
                            parameters = emptyList(),
                            source = source
                        )
                    )
                }
                is ArrayCreationExpression -> {
                    middlewareArg.children
                        .filterIsInstance<StringLiteralExpression>()
                        .forEach { middleware ->
                            middlewareList.add(
                                MiddlewareDefinition(
                                    name = middleware.contents,
                                    parameters = emptyList(),
                                    source = source
                                )
                            )
                        }
                }

                else -> {}
            }
        }
    }

    private fun processGroupMiddleware(
        groupArray: ArrayCreationExpression,
        middlewareList: MutableList<MiddlewareDefinition>
    ) {
        groupArray.children.forEach { child ->
            if (child is ArrayHashElement) {
                val key = child.key as? StringLiteralExpression
                if (key?.contents == "middleware") {
                    when (val value = child.value) {
                        is StringLiteralExpression -> {
                            middlewareList.add(
                                MiddlewareDefinition(
                                    name = value.contents,
                                    parameters = emptyList(),
                                    source = MiddlewareSource.GROUP
                                )
                            )
                        }
                        is ArrayCreationExpression -> {
                            value.children
                                .filterIsInstance<StringLiteralExpression>()
                                .forEach { middleware ->
                                    middlewareList.add(
                                        MiddlewareDefinition(
                                            name = middleware.contents,
                                            parameters = emptyList(),
                                            source = MiddlewareSource.GROUP
                                        )
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    private fun findWhereConstraints(element: PsiElement): Map<String, String> {
        val constraints = mutableMapOf<String, String>()

        var current: PsiElement = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "where") {
                current.parameters.firstOrNull()?.let { whereArg ->
                    when (whereArg) {
                        is ArrayCreationExpression -> {
                            whereArg.children.forEach { child ->
                                if (child is ArrayHashElement) {
                                    val key = (child.key as? StringLiteralExpression)?.contents
                                    val value = (child.value as? StringLiteralExpression)?.contents
                                    if (key != null && value != null) {
                                        constraints[key] = value
                                    }
                                }
                            }
                        }
                        is StringLiteralExpression -> {
                            // Handle single parameter where clause
                            val nextParam = current.parameters.getOrNull(1) as? StringLiteralExpression
                            if (nextParam != null) {
                                constraints[whereArg.contents] = nextParam.contents
                            }
                        }
                    }
                }
            }
        }

        return constraints
    }

    private fun getRouteParameters(path: String): List<RouteParameter> {
        val parameters = mutableListOf<RouteParameter>()

        // Match both required {param} and optional {param?} parameters
        val paramPattern = """\{([^{}]+?)(\?)?}""".toRegex()

        paramPattern.findAll(path).forEach { match ->
            val (fullParam, optional) = match.destructured

            // Split for constraints and defaults
            // Format could be: {param}, {param?}, {param:pattern}, {param?:pattern}
            val parts = fullParam.split(':')
            val paramName = parts[0].trim()
            val paramPattern = parts.getOrNull(1)

            // Determine parameter type based on common Laravel patterns
            val type = determineParameterType(paramPattern, paramName)

            parameters.add(RouteParameter(
                name = paramName,
                type = type,
                optional = optional == "?",
                pattern = paramPattern
            ))
        }

        return parameters
    }

    private fun determineParameterType(pattern: String?, paramName: String): String {
        return when {
            // Check pattern constraints
            pattern?.contains("\\d+") == true -> "integer"
            pattern?.contains("[0-9]+") == true -> "integer"
            pattern?.contains("\\w+") == true -> "string"
            pattern?.contains("[0-9a-fA-F]") == true -> "hex"
            pattern?.contains("^[A-Z]{2}\$") == true -> "alpha"
            pattern?.contains("uuid") == true -> "uuid"

            // Check common parameter names
            paramName.endsWith("_id") -> "integer"
            paramName.endsWith("Id") -> "integer"
            paramName == "id" -> "integer"
            paramName == "uuid" -> "uuid"
            paramName == "slug" -> "slug"
            paramName.contains("date") -> "date"
            paramName.contains("time") -> "datetime"
            paramName.contains("email") -> "email"
            paramName.contains("phone") -> "phone"
            paramName.contains("url") -> "url"

            // Default type
            else -> "string"
        }
    }

    // Helper method to get parameter pattern from route definition if exists
    private fun findParameterPattern(element: PsiElement, paramName: String): String? {
        var current: PsiElement = element
        while (current.parent != null) {
            current = current.parent
            if (current is MethodReference && current.name == "where") {
                // Check single parameter where clause
                val firstParam = current.parameters.getOrNull(0) as? StringLiteralExpression
                val secondParam = current.parameters.getOrNull(1) as? StringLiteralExpression
                if (firstParam?.contents == paramName && secondParam != null) {
                    return secondParam.contents
                }

                // Check array where clause
                val arrayParam = current.parameters.firstOrNull() as? ArrayCreationExpression
                arrayParam?.children?.forEach { child ->
                    if (child is ArrayHashElement) {
                        val key = child.key as? StringLiteralExpression
                        val value = child.value as? StringLiteralExpression
                        if (key?.contents == paramName && value != null) {
                            return value.contents
                        }
                    }
                }
            }
        }
        return null
    }
}