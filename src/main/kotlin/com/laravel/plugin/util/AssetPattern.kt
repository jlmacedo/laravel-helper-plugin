package com.laravel.plugin.util

import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

object AssetPattern {

    fun isAssetReference(element: StringLiteralExpression): Boolean {
        // Ignore empty strings
        if (element.contents.isBlank()) return false

        // Check for asset() helper function
        if (isInFunctionCall(element, listOf("asset", "mix", "vite", "elixir", "secure_asset"))) return true

        // Check for URL asset methods
        if (isInMethodCall(element, listOf("asset", "secure", "assetFrom", "secureAssetFrom"), 
                          listOf("URL", "url", "Illuminate\\Support\\Facades\\URL"))) return true

        // Check for Laravel 6+ Vite helpers
        if (isInFunctionCall(element, listOf("vite", "viteReactRefresh", "viteRender", "vite_asset"))) return true

        // Check for Blade asset directive or expressions
        if (isInBladeAssetContext(element)) return true

        // Check for HTML attributes containing asset paths
        if (isInHtmlAssetContext(element)) return true

        return false
    }

    private fun isInFunctionCall(element: StringLiteralExpression, functionNames: List<String>): Boolean {
        var current: PsiElement? = element.parent
        while (current != null) {
            if (current is FunctionReference && functionNames.contains(current.name)) {
                // Check if the string is a direct parameter of the function
                val params = current.parameters
                if (params.isNotEmpty() && params[0] == element) {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    private fun isInMethodCall(
        element: StringLiteralExpression,
        methodNames: List<String>,
        classNames: List<String>
    ): Boolean {
        var current: PsiElement? = element.parent
        while (current != null) {
            if (current is MethodReference &&
                methodNames.contains(current.name) &&
                classNames.contains(current.classReference?.text)
            ) {
                // Check if the string is a direct parameter of the method
                val params = current.parameters
                if (params.isNotEmpty() && params[0] == element) {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    private fun isInBladeAssetContext(element: StringLiteralExpression): Boolean {
        if (!element.containingFile.name.endsWith(".blade.php")) return false

        var current: PsiElement = element
        var depth = 0
        val maxDepth = 5  // Prevent excessive recursion

        while (current.parent != null && depth < maxDepth) {
            current = current.parent
            depth++

            // Check for common Blade asset directives and expressions
            val text = current.text
            if (text?.contains("@asset") == true ||
                text?.contains("@mix") == true ||
                text?.contains("@vite") == true ||
                text?.contains("@viteReactRefresh") == true ||
                (text?.contains("{{") == true && text.contains("asset(")) ||
                (text?.contains("{{") == true && text.contains("mix(")) ||
                (text?.contains("{{") == true && text.contains("vite(")) ||
                (text?.contains("{{") == true && text.contains("secure_asset(")) ||
                (text?.contains("{!!") == true && text.contains("asset(")) ||
                (text?.contains("{!!") == true && text.contains("mix(")) ||
                (text?.contains("{!!") == true && text.contains("vite("))
            ) {
                return true
            }
        }
        return false
    }

    private fun isInHtmlAssetContext(element: StringLiteralExpression): Boolean {
        if (!element.containingFile.name.endsWith(".blade.php") &&
            !element.containingFile.name.endsWith(".php")) return false

        var current: PsiElement = element
        var depth = 0
        val maxDepth = 5  // Prevent excessive recursion

        while (current.parent != null && depth < maxDepth) {
            current = current.parent
            depth++

            // Check for common HTML asset attributes
            val text = current.text
            if (text?.contains("src=") == true ||
                text?.contains("href=") == true ||
                text?.contains("data-src=") == true ||
                text?.contains("data-background=") == true ||
                text?.contains("content=") == true ||
                text?.contains("data-vite") == true) {
                return true
            }
        }
        return false
    }
}