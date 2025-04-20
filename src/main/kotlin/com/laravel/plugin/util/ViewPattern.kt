package com.laravel.plugin.util

import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

object ViewPattern {

    fun isViewReference(element: StringLiteralExpression): Boolean {
        return LaravelPattern.isViewReference(element)
    }
}