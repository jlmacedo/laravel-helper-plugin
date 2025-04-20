package com.laravel.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.services.ViewService
import com.laravel.plugin.util.LaravelPattern

class ViewGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // Try to get the string literal
        var stringLiteral: StringLiteralExpression? = null
        
        // Check if sourceElement is a part of a string literal
        if (sourceElement.parent is StringLiteralExpression) {
            stringLiteral = sourceElement.parent as StringLiteralExpression
        } 
        // Check if sourceElement is the string literal itself
        else if (sourceElement is StringLiteralExpression) {
            stringLiteral = sourceElement
        }
        
        if (stringLiteral == null) return null
        
        val viewName = stringLiteral.contents
        if (viewName.isBlank()) return null
        
        if (!LaravelPattern.isViewReference(stringLiteral)) {
            return null
        }

        val project = sourceElement.project
        val viewService = project.getService(ViewService::class.java)
        return viewService.findViewDeclaration(viewName).toTypedArray()
    }
}