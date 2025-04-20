package com.laravel.plugin.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.*
import com.laravel.plugin.util.LaravelPattern

object RoutePattern {

    /**
     * Detects a *route‑name* literal ( `'panel.dashboard'`, `'home'`, … ) and
     * **ignores** the method‑name literal inside the controller array
     * `[Controller::class, 'index']`.
     */
    fun isRouteReference(element: StringLiteralExpression): Boolean {
        // 1) pass the original heuristic
        if (!LaravelPattern.isRouteReference(element)) return false

        // 2) exclude `[Controller::class, 'method']` pattern
        if (isControllerActionLiteral(element)) return false

        return true
    }

    /* ────────────────── helper: literal inside `[FooController::class, 'bar']` ? ────────────────── */
    private fun isControllerActionLiteral(literal: StringLiteralExpression): Boolean {
        // find the nearest array creation `[ ... ]`
        val arrayExpr = PsiTreeUtil.getParentOfType(
            literal, ArrayCreationExpression::class.java, /* stopAt */ ParameterList::class.java
        ) ?: return false

        // quick sanity: array must have exactly two items
        val hashElements = PsiTreeUtil.getChildrenOfTypeAsList(arrayExpr, ArrayHashElement::class.java)
        if (hashElements.size != 2) return false

        // check that one element is a ::class reference, the other is *this* literal
        var hasClassConst = false
        var hasThisLiteral = false

        hashElements.forEach { hash ->
            val v = hash.value
            when {
                v === literal -> hasThisLiteral = true
                v is ClassConstantReference          && v.text.endsWith("::class") -> hasClassConst = true
                v is PhpReference && v.text.endsWith("::class")                     -> hasClassConst = true
            }
        }
        return hasClassConst && hasThisLiteral
    }

    /* ───────────────────────── Laravel 6+ helpers (unchanged) ───────────────────────── */

    fun isApiResource(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is MethodReference &&
                current.name == "apiResource" &&
                current.classReference?.text == "Route"
            ) {
                return true
            }
            current = current.parent
        }
        return false
    }

    fun isApiSingleton(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is MethodReference &&
                current.name == "apiSingleton" &&
                current.classReference?.text == "Route"
            ) {
                return true
            }
            current = current.parent
        }
        return false
    }

    fun isInertiaRoute(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is MethodReference &&
                current.name == "render" &&
                current.classReference?.text == "Inertia"
            ) {
                return true
            } else if (current is FunctionReference && current.name == "inertia") {
                return true
            }
            current = current.parent
        }
        return false
    }

    fun isLivewireRoute(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is FunctionReference && current.name == "livewire") {
                return true
            }
            current = current.parent
        }
        return false
    }
}