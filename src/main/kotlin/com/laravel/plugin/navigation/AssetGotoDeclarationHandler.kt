package com.laravel.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.util.AssetPattern
import com.laravel.plugin.services.AssetService

class AssetGotoDeclarationHandler : GotoDeclarationHandler {
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

        val assetPath = stringLiteral.contents
        if (assetPath.isBlank()) return null

        if (!AssetPattern.isAssetReference(stringLiteral)) {
            return null
        }

        val project = sourceElement.project
        val assetService = project.getService(AssetService::class.java)
        return assetService.findAssetDeclaration(assetPath).toTypedArray()
    }
}