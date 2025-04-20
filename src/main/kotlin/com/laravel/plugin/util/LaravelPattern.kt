package com.laravel.plugin.util

import com.jetbrains.php.lang.psi.elements.ParameterList
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

object LaravelPattern {

    fun isRouteReference(element: StringLiteralExpression): Boolean {
        // Skip empty strings
        if (element.contents.isBlank()) return false

        var current: PsiElement = element
        while (current.parent != null) {
            current = current.parent
            when (current) {
                is MethodReference -> {
                    when (current.name) {
                        "route" -> return true
                        "name" -> {
                            val prev = current.parent
                            if (prev is MethodReference &&
                                (prev.classReference?.text == "Route" || prev.name == "route")) {
                                return true
                            }
                        }
                        "get", "post", "put", "patch", "delete", "options", "match", "any", "resource", "apiResource", "singleton", "apiSingleton" -> {
                            if (current.classReference?.text == "Route") {
                                return true
                            }
                        }
                    }
                }
                is FunctionReference -> {
                    if (current.name == "route" || current.name == "url" || current.name == "action") return true
                }
            }

            // Check Blade syntax
            if (element.containingFile.name.endsWith(".blade.php")) {
                val text = current.text ?: continue
                if (text.contains("@route") ||
                    text.contains("@url") ||
                    (text.contains("{{") && text.contains("route(")) ||
                    (text.contains("{{") && text.contains("url(")) ||
                    (text.contains("{{") && text.contains("action("))) {
                    return true
                }
            }
        }
        return false
    }

    // Improved translation reference detection
    fun isTranslationReference(element: StringLiteralExpression): Boolean {
        // Skip empty strings
        if (element.contents.isBlank()) return false

        // Skip numeric-only strings (unlikely to be translation keys)
        if (element.contents.all { it.isDigit() }) return false

        // Get the PSI tree for the current context
        var current: PsiElement? = element
        var depth = 0
        val maxDepth = 8  // Prevent excessive recursion

        // Skip if we're in a route call context (prioritize route over translation)
        if (isRouteReference(element)) {
            return false
        }

        // Check direct parent relationships first (more common patterns)
        var parent = element.parent

        // Check for common translation function patterns as the direct parent
        if (parent is ParameterList) {
            val parameterIndex = parent.parameters.indexOf(element)
            val grandParent = parent.parent

            if (grandParent is FunctionReference) {
                val funcName = grandParent.name
                if ((funcName == "trans" || funcName == "__" ||
                            funcName == "trans_choice" || funcName == "lang" ||
                            funcName == "__t" || funcName == "_" ||
                            funcName == "localize" || funcName == "translate") &&
                    parameterIndex == 0) {
                    return true
                }
            } else if (grandParent is MethodReference) {
                val methodName = grandParent.name
                val className = grandParent.classReference?.text
                if ((methodName == "get" || methodName == "choice" ||
                            methodName == "trans" || methodName == "__" ||
                            methodName == "transChoice") &&
                    (className == "Lang" || className == "Trans" ||
                            className == "Translator" || className == "app" ||
                            className == "translator" || className == "I18n" ||
                            className == "TranslatorContract" || className == "Localization") &&
                    parameterIndex == 0) {
                    return true
                }
            }
        }

        // More thorough check of the context
        while (current != null && depth < maxDepth) {
            // For FunctionReference, check specific translation functions
            if (current is FunctionReference) {
                val funcName = current.name

                // Check for translation functions
                if (funcName == "trans" || funcName == "__" ||
                    funcName == "trans_choice" || funcName == "lang" ||
                    funcName == "__t" || funcName == "_" ||
                    funcName == "localize" || funcName == "translate") {

                    // Only return true if this element is the first parameter
                    val params = current.parameters
                    if (params.isNotEmpty() && params[0] == element) {
                        return true
                    }
                }
            }
            // For MethodReference, check translation methods on specific classes
            else if (current is MethodReference) {
                val methodName = current.name
                val className = current.classReference?.text

                if ((methodName == "get" || methodName == "choice" ||
                            methodName == "trans" || methodName == "__" ||
                            methodName == "transChoice") &&
                    (className == "Lang" || className == "Trans" ||
                            className == "Translator" || className == "app" ||
                            className == "translator" || className == "I18n" ||
                            className == "TranslatorContract" || className == "Localization" ||
                            className?.endsWith("\\Translator") == true)) {

                    // Only return true if this element is the first parameter
                    val params = current.parameters
                    if (params.isNotEmpty() && params[0] == element) {
                        return true
                    }
                }
            }

            // Check for Blade translation directives and expressions
            if (element.containingFile.name.endsWith(".blade.php")) {
                val text = current.text ?: ""
                if (text.contains("@lang(") ||
                    text.contains("@__(") ||
                    text.contains("@trans(") ||
                    text.contains("@translate(") ||
                    (text.contains("{{") && (
                            text.contains("__(") ||
                                    text.contains("trans(") ||
                                    text.contains("translate(") ||
                                    text.contains("Lang::get(") ||
                                    text.contains("trans_choice("))) ||
                    (text.contains("{!!") && (
                            text.contains("__(") ||
                                    text.contains("trans(") ||
                                    text.contains("translate(") ||
                                    text.contains("Lang::get(")))) {

                    return true
                }
            }

            // Move up the tree
            current = current.parent
            depth++
        }

        // Detect translation keys in Laravel language files
        try {
            val filePath = element.containingFile.virtualFile.path
            if (filePath.contains("/lang/") ||
                filePath.contains("/resources/lang/") ||
                filePath.contains("/app/lang/")) {
                // Check if the string appears to be a key in a translation array
                if (element.parent is com.jetbrains.php.lang.psi.elements.ArrayHashElement &&
                    (element.parent as com.jetbrains.php.lang.psi.elements.ArrayHashElement).key == element) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore file path errors
        }

        // Check with the translation service if this is a known translation key
        try {
            val translationService = element.project.getService(com.laravel.plugin.services.TranslationService::class.java)
            if (translationService != null && translationService.isTranslation(element.contents)) {
                return true
            }
        } catch (e: Exception) {
            // Ignore service errors
        }

        return false
    }

    fun isViewReference(element: StringLiteralExpression): Boolean {
        // Skip empty strings
        if (element.contents.isBlank()) return false

        var current = element.parent
        while (current != null) {
            when {
                // view('home'), markdown('home'), etc.
                current is FunctionReference && (
                        current.name == "view" ||
                                current.name == "markdown" ||
                                current.name == "render" ||
                                current.name == "renderView"
                        ) -> return true

                // View::make('home'), View::first(), etc.
                current is MethodReference &&
                        (current.name == "make" ||
                                current.name == "first" ||
                                current.name == "renderWhen" ||
                                current.name == "renderUnless" ||
                                current.name == "composer") &&
                        (current.classReference?.text == "View" ||
                                current.classReference?.text?.endsWith("\\View") == true) -> return true

                // Blade Directives: @include, @extends, etc.
                element.containingFile.name.endsWith(".blade.php") && (
                        current.text?.contains("@include") == true ||
                                current.text?.contains("@extends") == true ||
                                current.text?.contains("@component") == true ||
                                current.text?.contains("@livewire") == true ||
                                current.text?.contains("@each") == true ||
                                current.text?.contains("@includeIf") == true ||
                                current.text?.contains("@includeWhen") == true ||
                                current.text?.contains("@includeUnless") == true ||
                                current.text?.contains("@includeFirst") == true ||
                                current.text?.contains("@yield") == true
                        ) -> return true

                // Blade::render('view.name')
                current is MethodReference &&
                        (current.name == "render" ||
                                current.name == "renderComponent" ||
                                current.name == "compile") &&
                        (current.classReference?.text == "Blade" ||
                                current.classReference?.text?.endsWith("\\Blade") == true) -> return true

                // Response::view('home')
                current is MethodReference &&
                        current.name == "view" &&
                        (current.classReference?.text == "Response" ||
                                current.classReference?.text?.endsWith("\\Response") == true) -> return true
            }
            current = current.parent
        }
        return false
    }

    fun isAssetReference(element: StringLiteralExpression): Boolean {
        return AssetPattern.isAssetReference(element)
    }

    fun isValidLaravelCallContext(element: StringLiteralExpression): Boolean {
        return isRouteReference(element) ||
                isTranslationReference(element) ||
                isViewReference(element) ||
                isAssetReference(element)
    }
}